# ChunkMirrored

A Paper Minecraft plugin that **mirrors player movement and block changes across surrounding chunks.**
This creates a "looping world" effect, where each player sees a copy of themselves in a grid of mirrored NPCs.

---

## Features
- **Mirrored NPC Grid**: Each player sees NPC copies of themselves in all surrounding chunks (up to their view distance).
- **Block Masking**:  
  - Placing/breaking blocks updates all corresponding mirrored chunks.  
  - Changes persist across server restarts (`changedBlocks.yml`).
- **Torus Wrapping**: NPCs move in a wrapped grid around the player (infinite tiling illusion).
- **Gamemode Support**:  
  - Spectators: NPCs are also transferred to Spectator-mode.  
- **Automatic Saving**: Block masks are stored on shutdown and reapplied when chunks load.

---

## Requirements
- **Minecraft Server**: Spigot or Paper (1.21.8 recommended).
- Java 21

---

## Installation
1. Download or build the plugin JAR.
2. Place it in your server’s `plugins/` folder.
3Start/restart your server.

---

## Usage
- On join, players will see NPC copies of themselves in a full mirrored grid.  
- Place/break blocks → updates will be mirrored across the world.  
- Command: /tpc, which teleports the user to the chunk of a fellow player.
