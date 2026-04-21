# Mountain Ascent - Player Guide

## Goal

Reach the portal at the summit of each mountain while surviving enemies and navigating gaps.

The game is a 5 level campaign with increasing difficulty.

## Starting a Run

1. On the title screen, click the seed input box.
2. Type a seed (number or text).
3. Click `Generate World`.

A short generation animation plays, then the run starts.

Using the same seed gives the same campaign generation pattern.

## Controls

```text
A / D          Move left / right
SPACE or W     Jump
Left Click     Mine block
Right Click    Place block
Mouse Wheel    Switch hotbar slot
1 / 2          Select hotbar slot
E              Activate / release grapple
F3             Toggle help/debug text
ENTER          Retry after death / continue after level clear
```

## Core Mechanics

### Mining and Building

- Hold left click on a nearby block to mine it.
- Mined blocks drop as pickups in the world.
- Pickups are collected by moving over them.
- Right click places a block from the selected hotbar slot.

### Inventory

- Two-slot hotbar.
- Each slot has limited capacity.
- Only the selected slot is used for placement.

### Grappling Hook

- The grapple is found as a pickup in the level.
- Press `E` to attach toward the mouse position.
- Press `E` again to detach.
- It is one-use only per level.

### Enemies

- Enemies wander and chase when the player is nearby.
- Touching an enemy kills the player.
- Level 1 includes tutorial guidance and a gentler onboarding flow before full pressure ramps up.

## Level Progression

- Clearing a level advances to the next generated mountain.
- Difficulty increases across 5 levels.
- After finishing the final level, the game returns to the start screen so a new run/seed can be generated.

## Practical Tips

- Mine before long jumps so you can build recovery paths.
- Save grapple usage for large gaps or high vertical shortcuts.
- Keep inventory space available so mined blocks are not wasted.
- If an enemy is close, prioritize movement and terrain usage over mining.

## Win/Loss Conditions

- Win a level: touch the portal.
- Lose a level: collide with an enemy.
- After loss, press `ENTER` to retry the current level.
