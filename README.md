# Mountain Ascent

CS4303 Practical 3: a 2D sandbox platformer built in Java with the Processing framework, focused on procedural content generation.

## Overview

The player climbs a procedurally generated mountain by platforming, mining/placing blocks, and using a one-use grappling hook, while avoiding enemies and reaching a portal.

Generation is deterministic from a seed. The start screen lets the player type a custom seed and generate a repeatable five-level campaign.

## Practical 3 Coverage

### Level 1: Generate Playable Levels

- Constraint based terrain generation creates a critical path of platforms/mesas.
- Core movement constraints are enforced by bounded gap/rise ranges.
- Border and ground rules guarantee valid world bounds and recoverable space.

### Level 2: Procedural Extras

- Enemy placement uses spacing/position rules derived from generated platforms.
- Grapple pickup and resource placement use dedicated distribution logic.
- Biome and cave passes decorate and reshape terrain after base generation.

### Level 3: Difficulty Parameter

- A 5 level campaign uses a difficulty curve: `1, 3, 5, 7, 9`.
- Difficulty influences platform lengths/gaps/rises and enemy pressure/speed.
- Progression is coordinated by `LevelManager`.

### Level 4: Generator Combinator

- `LevelGenerator` coordinates sub-generators in dependency order:
  - `TerrainGenerator`
  - `BiomeDecorator`
  - `CaveGenerator`
  - `EnemySpawner`
  - `ItemDistributor`
- This preserves consistency between layout, decoration, item placement, and challenge scaling.

## Seeded Generation

- Start screen flow:
  - Click seed input box and type a number or text.
  - Click `Generate World`.
- Numeric seeds are used directly; text seeds are hashed to a stable long value.
- The same seed reproduces the same campaign layout sequence and difficulty progression.

## Controls

### Start Screen

```text
Click seed input box     Edit custom seed
Click "Generate World"   Start generation and play
```

### In Game

```text
A / D          Move left / right
SPACE or W     Jump
Left Click     Mine block
Right Click    Place block
Mouse Wheel    Switch hotbar slot
1 / 2          Select hotbar slot
E              Activate / release grapple
F3             Toggle help/debug text
```

### Menus During Run

```text
ENTER          Retry after death / continue after level clear
```

## Build and Run

Requirements:

- Java JDK (8+)
- Processing core library JAR included in `lib/`

Commands:

```bash
make compile
make run
make clean
```

## Project Structure

- `src/Main.java`: main loop, rendering, UI, input, game state coordination
- `src/LevelManager.java`: campaign progression and difficulty curve
- `src/LevelGenerator.java`: generator combinator
- `src/TerrainGenerator.java`: base playable mountain/path generation
- `src/BiomeDecorator.java`, `src/CaveGenerator.java`: terrain passes
- `src/EnemySpawner.java`, `src/ItemDistributor.java`: procedural extras
- `src/Enemy.java`, `src/PathFinder.java`: enemy AI/pathfinding
- `src/TileMap.java`, `src/TileTypes.java`: tile world and tile data
