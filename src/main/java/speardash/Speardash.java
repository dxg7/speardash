package speardash;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Speardash extends JavaPlugin implements Listener {

    private static final long READY_SHOW_MS = 1000L;

    private final Map<Material, int[]> dashItems = new LinkedHashMap<>();
    private double dashPower;
    private double cooldownSeconds;

    private final Map<UUID, Map<Material, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<Material, Long>> readyItems = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tickCooldownBars, 0L, 1L);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("ffaitems")
                            .requires(source -> source.getSender() instanceof Player)
                            .then(Commands.argument("item", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        builder.suggest("spear");
                                        builder.suggest("sword");
                                        builder.suggest("axe");
                                        builder.suggest("mace");
                                        return builder.buildFuture();
                                    })
                                    .executes(this::handleDashItem))
                            .build(),
                    "Get a dash item",
                    List.of());
            event.registrar().register(
                    Commands.literal("ffareload")
                            .executes(this::handleReload)
                            .build(),
                    "Reload the Speardash config",
                    List.of());
        });
        getLogger().info("Speardash enabled");
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
        readyItems.clear();
    }

    private void loadConfig() {
        reloadConfig();
        dashItems.clear();
        for (String key : getConfig().getConfigurationSection("dash-items").getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                getLogger().warning("Unknown dash item '" + key + "', skipping");
                continue;
            }
            dashItems.put(material, parseColors(getConfig().getString("dash-items." + key, "#FFFFFF:#808080")));
        }
        if (dashItems.isEmpty()) {
            getLogger().warning("No valid dash-items found in config");
        }
        dashPower = getConfig().getDouble("dash-power", 1.8);
        cooldownSeconds = getConfig().getDouble("cooldown-seconds", 5.0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !dashItems.containsKey(item.getType())) {
            return;
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Map<Material, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long until = playerCooldowns.get(item.getType());
        if (until != null && now < until) {
            Component bar = statusBar(player.getUniqueId(), now);
            if (bar != null) {
                player.sendActionBar(bar);
            }
            return;
        }

        playerCooldowns.put(item.getType(), now + (long) (cooldownSeconds * 1000.0));
        Map<Material, Long> ready = readyItems.get(player.getUniqueId());
        if (ready != null) {
            ready.remove(item.getType());
        }
        player.setCooldown(item.getType(), (int) (cooldownSeconds * 20.0));

        Vector direction = player.getEyeLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(dashPower));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cooldowns.remove(uuid);
        readyItems.remove(uuid);
    }

    private void tickCooldownBars() {
        if (cooldowns.isEmpty() && readyItems.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Map<Material, Long>>> readyIt = readyItems.entrySet().iterator();
        while (readyIt.hasNext()) {
            Map.Entry<UUID, Map<Material, Long>> entry = readyIt.next();
            entry.getValue().entrySet().removeIf(e -> e.getValue() <= now);
            if (entry.getValue().isEmpty()) {
                readyIt.remove();
            }
        }

        Iterator<Map.Entry<UUID, Map<Material, Long>>> playerIt = cooldowns.entrySet().iterator();
        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<Material, Long>> entry = playerIt.next();
            UUID uuid = entry.getKey();
            Player player = getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerIt.remove();
                readyItems.remove(uuid);
                continue;
            }

            Map<Material, Long> map = entry.getValue();
            Iterator<Map.Entry<Material, Long>> itemIt = map.entrySet().iterator();
            while (itemIt.hasNext()) {
                Map.Entry<Material, Long> cd = itemIt.next();
                if (cd.getValue() <= now) {
                    itemIt.remove();
                    readyItems.computeIfAbsent(uuid, k -> new HashMap<>()).put(cd.getKey(), now + READY_SHOW_MS);
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
                }
            }
            if (map.isEmpty()) {
                playerIt.remove();
            }
        }

        Set<UUID> players = new HashSet<>(cooldowns.keySet());
        players.addAll(readyItems.keySet());
        for (UUID uuid : players) {
            Player player = getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Component bar = statusBar(uuid, now);
            if (bar != null) {
                player.sendActionBar(bar);
            }
        }
    }

    private Component statusBar(UUID uuid, long now) {
        List<Component> parts = new ArrayList<>();
        Map<Material, Long> cds = cooldowns.get(uuid);
        if (cds != null) {
            for (Map.Entry<Material, Long> e : cds.entrySet()) {
                long remaining = e.getValue() - now;
                if (remaining > 0) {
                    parts.add(cooldownTime(e.getKey(), remaining / 1000.0));
                }
            }
        }
        Map<Material, Long> rdy = readyItems.get(uuid);
        if (rdy != null) {
            for (Map.Entry<Material, Long> e : rdy.entrySet()) {
                if (e.getValue() > now) {
                    parts.add(readyText(e.getKey()));
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return Component.join(JoinConfiguration.separator(Component.text(" | ")), parts);
    }

    private Component cooldownTime(Material item, double seconds) {
        int[] colors = dashItems.getOrDefault(item, new int[]{0xFFFFFF, 0x808080});
        String text = String.format(Locale.US, "%.1f", seconds) + "s";
        int length = text.length();
        List<Component> parts = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            double t = length == 1 ? 0.0 : (double) i / (length - 1);
            parts.add(Component.text(text.substring(i, i + 1), TextColor.color(lerp(colors[0], colors[1], t))));
        }
        return Component.text().append(parts).build();
    }

    private Component readyText(Material item) {
        return Component.text("ready", NamedTextColor.GREEN);
    }

    private int handleReload(CommandContext<CommandSourceStack> context) {
        loadConfig();
        cooldowns.clear();
        readyItems.clear();
        context.getSource().getSender().sendMessage(Component.text("Speardash config reloaded", NamedTextColor.GREEN));
        return 1;
    }

    private int handleDashItem(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        String arg = context.getArgument("item", String.class).toLowerCase(Locale.ROOT);
        Material material = switch (arg) {
            case "spear", "netherite", "netherite_spear" -> Material.NETHERITE_SPEAR;
            case "sword", "netherite_sword" -> Material.NETHERITE_SWORD;
            case "axe", "netherite_axe" -> Material.NETHERITE_AXE;
            case "mace" -> Material.MACE;
            default -> Material.matchMaterial(arg);
        };
        if (material == null || !dashItems.containsKey(material)) {
            player.sendMessage(Component.text("That item is not a dash item", NamedTextColor.RED));
            return 0;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            int[] colors = dashItems.getOrDefault(material, new int[]{0xFFFFFF, 0x808080});
            meta.displayName(Component.text("Dash " + dashName(material), TextColor.color(colors[0])));
            stack.setItemMeta(meta);
        }
        player.getInventory().addItem(stack);
        player.sendMessage(Component.text("Gave you: dash " + dashName(material).toLowerCase(Locale.ROOT), NamedTextColor.GREEN));
        return 1;
    }

    private static String dashName(Material material) {
        return switch (material) {
            case NETHERITE_SPEAR -> "Spear";
            case NETHERITE_SWORD -> "Sword";
            case NETHERITE_AXE -> "Axe";
            case MACE -> "Mace";
            default -> material.name();
        };
    }

    private static int[] parseColors(String value) {
        String[] parts = value.split(":");
        int start = parseHex(parts[0].trim());
        int end = parts.length > 1 ? parseHex(parts[1].trim()) : start;
        return new int[]{start, end};
    }

    private static int parseHex(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return (int) (Long.parseLong(s, 16) & 0xFFFFFFL);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static int lerp(int from, int to, double t) {
        int r = from >> 16 & 0xFF, g = from >> 8 & 0xFF, b = from & 0xFF;
        int tr = to >> 16 & 0xFF, tg = to >> 8 & 0xFF, tb = to & 0xFF;
        int nr = (int) (r + (tr - r) * t);
        int ng = (int) (g + (tg - g) * t);
        int nb = (int) (b + (tb - b) * t);
        return (nr << 16) | (ng << 8) | nb;
    }
}
