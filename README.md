# ⚡ SpearDash

> Spear Mace FFA | Dash plugin

Lánzate hacia donde miras con tu **lanza, espada, hacha o maza** de netherite. Shift + click derecho y sale volando.

Launch yourself toward where you're looking with your **netherite spear, sword, axe or mace**. Shift + right-click and fly.

---

## ✨ Features

- **Dash mechanic** — Shift + Right Click with a dash item propels you in the direction you're looking.
- **Per-item cooldowns** — each dash item has its own cooldown, so you can chain `spear → sword → axe → mace`.
- **Gradient actionbar** — the remaining time shows above your hotbar in a per-item color gradient:
  `4.5s | 2.3s`
- **`ready` flash + amethyst sound** (`block.amethyst_block.resonate`) when a cooldown finishes.
- **Custom items** — `/ffaitems` gives you *Dash Spear*, *Dash Sword*, *Dash Axe* and *Dash Mace*, each with its own color.
- **Hot reload** — tweak the config and `/ffareload`, no server restart needed.

## 🎮 Commands

| Command | Description |
| --- | --- |
| `/ffaitems <spear\|sword\|axe\|mace>` | Give yourself a dash item (tab-complete supported) |
| `/ffareload` | Reload the plugin config on the fly |

## 🛠 Configuration

```yaml
# Items that trigger the dash and their actionbar gradient colors
dash-items:
  NETHERITE_SPEAR: "#CE93D8:#6A1B9A"
  NETHERITE_SWORD: "#EF5350:#B71C1C"
  NETHERITE_AXE: "#A5D6A7:#1B5E20"
  MACE: "#FFD54F:#F57F17"

# Dash force (higher = faster / further)
dash-power: 1.8

# Cooldown in seconds per item
cooldown-seconds: 5.0
```

> Any `Material` name works as a key in `dash-items`. Colors are hex `#RRGGBB:#RRGGBB`.

## 📦 Installation

1. Drop `speardash-*.jar` into your server's `plugins/` folder.
2. Start (or `/reload confirm`) the server.
3. Run `/ffaitems spear` and enjoy. ⚡

- **Requirements:** Paper 1.21.11+ · Java 21+

## 🏗 Building

```bash
./gradlew build
```

The jar lands in `build/libs/`.

---

Made with 💜 for Spear Mace FFA.
