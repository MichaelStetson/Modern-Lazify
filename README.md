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
