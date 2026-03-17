# CS4303 Practical 1 – Physics Based Sandbox Game

---

# Overview

This project is a small 2D sandbox style platformer built using a custom physics engine implemented in Java using the Processing framework.

The main goal of the game is to reach a portal placed somewhere in the world. The player can move around the world, mine blocks, place blocks, and use a grappling hook to swing across obstacles.

The project focuses primarily on implementing the physics systems required in the assignment specification:

- **Level 1:** Point mass physics body with force accumulation and integration
- **Level 2:** Collision detection and response using axis aligned bounding boxes against a tile based world
- **Level 3:** Modular physics architecture using force generators and a force registry
- **Level 4:** A grappling hook cable constraint allowing the player to swing from an anchor point

---

# Physics Engine Architecture

The physics engine is designed to be modular and separated from the game logic.

## Body

Represents a dynamic physics object containing:

- position
- velocity
- acceleration
- inverse mass
- accumulated forces

Motion is integrated using **semi implicit Euler integration**.

## PhysicsWorld

Stores all active physics bodies and advances the simulation for each frame.

## ForceRegistry

Associates bodies with force generators.

## ForceGenerator

Interface used by systems that apply forces to bodies.

## GravityForce

Applies constant downward acceleration to registered bodies.

## TileCollision

Handles collision detection and response between the player and solid tiles in the tile map.

## GrappleCable

Implements a cable constraint used for the grappling hook mechanic.

If the player moves beyond the cable length:

- the body is projected back onto the cable radius
- outward radial velocity is removed
- tangential velocity is preserved

This allows natural swinging behavior.

---

# AI System

The game includes hostile flying enemies that demonstrate the four AI levels required in the specification.

## Level 1 – Steering Behaviors

Enemies use basic steering behaviors:

- wandering movement
- seeking/pursuit of the player

## Level 2 – Compound Behaviors

Multiple steering behaviors are combined:

- pursuit/path-following
- obstacle avoidance (tile based lookahead)
- separation (enemies avoid clustering)

These behaviors are weighted and combined to produce smoother movement.

## Level 3 – Decision Making

Enemies use a **finite state machine (FSM)** with two main states:

- **WANDER** – slow roaming behavior
- **CHASE** – actively pursue the player

State transitions depend on:

- distance to the player
- a short “alert timer” so enemies do not instantly disengage

## Level 4 – Pathfinding

Enemies use **A*** pathfinding over the tile grid:

- world positions are converted to tile coordinates
- A* is run on traversable tiles (air)
- the resulting path is converted back into world space waypoints

This allows enemies to navigate complex terrain and reach the player even when direct movement is blocked.

---

# Game Features

The game includes several sandbox and gameplay systems:

- Player movement and jumping using physics
- Tile based mining and block placement
- Limited inventory system (2 slots, capped stack sizes)
- Dropped block system
- Grappling hook with single use cable constraint
- AI enemies with multi level behaviour
- Start screen, death screen, and win screen
- Camera system that follows the player through the world

---

# Controls

## Movement

```
A / D         Move left / right
SPACE or W    Jump
```

## Sandbox Mechanics

```
Left Click     Mine block
Right Click    Place block
Mouse Wheel    Switch hotbar slot
1 / 2          Select hotbar slot
```

## Grappling Hook

```
E    Activate / release grapple
```

The grappling hook must first be collected from the world before it can be used.  
It has a **single use**.

## Other

```
F3    Toggle help/debug text
ENTER Navigate menus / restart
```

---

---

# Objective

Reach the **portal** located in the world.

Players must:

- navigate platforming sections
- manage limited building resources
- avoid enemies
- use the grappling hook strategically

to reach the goal.


---

# Compilation

The project uses Java together with the Processing core library.

To compile the project from the command line:

```
make compile
```

---

# Running the Game

To run the game:

```
make run
```

A window will open containing the game.

---

# Dependencies

This project requires:

- Java (JDK 8 or newer)
- Processing core library (`core.jar`)

The `core.jar` file is included in the submission source folder.