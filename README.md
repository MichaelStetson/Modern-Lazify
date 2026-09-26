# Modern-Lazify

Client-side Fabric port of Lazify’s BedWars stats HUD and gameplay utilities for Minecraft 26.2.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5 or newer and Fabric API
- Java 25

## Build and run

On Windows, from the repository root:

```bat
gradlew.bat build
gradlew.bat runClient
```

The mod JAR is written to `build/libs/`.

## Configure

Press **L** in-game or run `/ov`, `/overlay`, or `/lazify` to open settings. The **API** tab accepts your own keys:

- **Hypixel** — optional stats fallback
- **Bordic** — session, winstreak, ping, and nick-detection data
- **Urchin** and **Seraph** — player tags

The settings screen masks keys while editing. Settings are stored locally in the game’s `config/lazify.json`; keys are not bundled with the mod and must not be committed.

Choose Lazify, Nerdify, or Mellow from the settings screen. `/ov help` lists the HUD, player, and configuration commands.

Settings edits apply immediately and autosave automatically; **Save & Apply** flushes pending typing or drag edits at once. Drag the top bar to move the settings window, or its lower-right corner to resize it; both position and dimensions persist in the local config. The Move Overlay screen previews the HUD sharply, even when the overlay itself is disabled.

## Visuals

The Lazify and Nerdify HUDs use the legacy panel spacing, headers, column alignment, outlines, and row highlighting. Mellow replaces the tab list with a centered compact table, player heads, and scroll indicators. Settings, overlay positioning, hidden-player management, and appearance presets use the matching dark panel and control styling.

## Legacy config import

On first launch, Forge settings from `config/lazify/lazify.cfg` or `config/lazify.cfg` are imported into `config/lazify.json`. The old file is kept unchanged. Provider keys are migrated locally, and legacy LWJGL keybinds are converted to GLFW key codes. Existing appearance presets in `config/lazify/presets/*.properties` remain readable. If `config/lazify.json` already exists, it takes precedence; back it up and remove it before launch to import the old config.

## Credits

The original Forge mod was created by abusez. [Discord](https://discord.gg/abusez).
