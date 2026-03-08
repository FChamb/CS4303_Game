import processing.core.PApplet;
import processing.event.MouseEvent;

/**
 * Main Processing sketch for the game.
 *
 * Responsibilities:
 * - create and initialize the game world
 * - run the main update/render loop
 * - collect player input
 * - drive the physics engine
 * - apply collision resolution
 * - manage sandbox mechanics (mining, placing, inventory)
 * - manage the Stage 4 grappling-hook feature
 *
 * This class deliberately keeps the engine modular by delegating:
 * - integration to PhysicsWorld / Body
 * - forces to ForceRegistry / GravityForce
 * - collision handling to TileCollision
 * - grapple constraint to GrappleCable
 */
public class Main extends PApplet {

    PhysicsWorld world;
    Player player;
    Portal portal;
    TileMap map;

    // timing
    float lastTime;

    // input state
    boolean leftHeld, rightHeld, jumpHeld;
    boolean jumpPressedThisFrame;

    // Maximum reach distance for mining and block placement.
    float interactRange = 160.0f;

    // Inventory / hotbar state.
    Inventory inv = new Inventory(3);
    int selectedSlot = 0;

    // Mining state for timed block breaking.
    boolean miningActive = false;
    int miningR = -1, miningC = -1;
    float miningProgress = 0f;
    float miningRequired = 0f;

    // Temporary HUD message state.
    String statusMsg = "";
    float statusTimer = 0f;

    // ---------- STAGE 4: GRAPPLING HOOK ----------
    // The hook must first be collected as a pickup item.
    boolean hasGrapplePickup = false;

    // Once the player has used and released the grapple, it is consumed.
    boolean grappleUsed = false;

    // Whether the grapple cable is currently attached.
    boolean grappleActive = false;

    // Used to prevent repeated E toggles while the key is held down.
    boolean eHeld = false;

    // Position of the grapple pickup item in the level.
    Vec2 grapplePickupPos = new Vec2();

    // Stage 4 cable constraint object.
    GrappleCable grappleCable;

    public static void main(String[] args) {
        PApplet.main("Main");
    }

    public void settings() {
        size(900, 600);
    }

    /**
     * Initialises the game world and all major objects.
     */
    public void setup() {
        world = new PhysicsWorld();

        // Create the player and register its body with the physics world.
        player = new Player(width * 0.2f, height * 0.5f);
        player.body.invMass = 1.0f;
        world.addBody(player.body);

        // Create the goal object.
        portal = new Portal(width * 0.8f, height * 0.4f, 18);

        // Create the tile-based environment.
        map = new TileMap(width, height, 30);

        // Register constant gravity as a continuous force on the player.
        world.forceRegistry.add(player.body, new GravityForce(0, 900));

        // Create the cable constraint used for Stage 4.
        grappleCable = new GrappleCable();

        // Place the grapple pickup on top of the left stone pillar.
        // The pillar is at column 3 in the current TileMap generation.
        // We place the pickup centred horizontally on the pillar and slightly
        // above its top tile so it looks like it is resting on top.
        grapplePickupPos.set(3 * map.tileSize + map.tileSize / 2.0f,
                (map.rows - 12) * map.tileSize - 12.0f);

        lastTime = millis() / 1000.0f;
        textFont(createFont("Arial", 16));
    }

    /**
     * Processing draw loop.
     *
     * This acts as the main game loop:
     * 1. compute dt
     * 2. update pickup state
     * 3. perform several smaller physics substeps for stability
     * 4. update mining and HUD state
     * 5. render the world and UI
     */
    public void draw() {
        float now = millis() / 1000.0f;
        float dt = now - lastTime;
        lastTime = now;

        // Clamp dt to avoid unstable jumps in simulation time.
        dt = constrain(dt, 0.0f, 1.0f / 30.0f);

        // Check whether the player picked up the grappling hook.
        updateGrapplePickup();

        // Use substeps to reduce tunnelling and improve collision stability.
        int subSteps = 6;
        float subDt = dt / subSteps;

        for (int i = 0; i < subSteps; i++) {
            handleInput(subDt);

            // Step the force-based physics world.
            world.step(subDt);

            // Enforce the Stage 4 cable constraint after integration.
            if (grappleActive) {
                grappleCable.enforce(player.body);
            }

            // Resolve collisions against the tile world.
            TileCollision.resolvePlayerVsTiles(player, map);

            // Apply extra arcade-style movement controls.
            applyFriction(subDt);
            clampRunSpeed();
        }

        updateMining(dt);
        updateStatus(dt);

        // ---------- Rendering ----------
        background(18);

        map.draw(this);
        portal.draw(this);
        drawGrapplePickup();
        drawGrappleCable();
        player.draw(this);
        drawPlayerGrappleIndicator();

        drawTileCursorAndMiningUI();
        drawHotbar();

        fill(255);
        text("A/D move, SPACE jump | Hold LEFT = mine | RIGHT click = place | E = grapple", 20, 30);

        int selType = inv.peekType(selectedSlot);
        int selCount = inv.peekCount(selectedSlot);
        text("Selected Slot: " + (selectedSlot + 1) +
                "  Item: " + TileTypes.name(selType) +
                "  Count: " + selCount +
                "  (scroll wheel to switch)", 20, 52);

        // Display current grapple state to the player.
        String grappleText;
        if (grappleActive) grappleText = "Grapple: ACTIVE";
        else if (hasGrapplePickup) grappleText = "Grapple: READY (press E)";
        else if (grappleUsed) grappleText = "Grapple: USED";
        else grappleText = "Grapple: NOT COLLECTED";

        text(grappleText, 20, 74);

        if (!statusMsg.isEmpty()) {
            fill(255);
            text(statusMsg, 20, 96);
        }

        if (player.isAtPortal(portal)) {
            fill(0, 220, 120);
            text("LEVEL COMPLETE", width / 2f - 80, 40);
        }
    }

    /**
     * Handles movement input for the current frame.
     *
     * Horizontal movement is implemented as a force.
     * Jumping is implemented by setting vertical velocity directly,
     * which is common in platformers for responsiveness.
     */
    void handleInput(float dt) {
        float moveForce = 3800.0f;

        player.thrust.set(0, 0);
        if (leftHeld) player.thrust.x -= moveForce;
        if (rightHeld) player.thrust.x += moveForce;

        player.body.addForce(player.thrust);

        // Only allow jumping when grounded.
        if (jumpPressedThisFrame && player.grounded) {
            player.body.velocity.y = -430;
            player.grounded = false;
        }

        jumpPressedThisFrame = false;
    }

    /**
     * Applies extra ground friction to make horizontal movement feel less floaty.
     *
     * This is separate from the physics body's damping because it is
     * specifically a gameplay/character-control feature.
     */
    void applyFriction(float dt) {
        if (!player.grounded) return;

        boolean movingInput = leftHeld || rightHeld;

        float frictionPerFrame = movingInput ? 0.90f : 0.65f;
        float factor = (float) Math.pow(frictionPerFrame, dt * 60.0f);

        player.body.velocity.x *= factor;

        if (!movingInput && Math.abs(player.body.velocity.x) < 5.0f) {
            player.body.velocity.x = 0;
        }
    }

    /**
     * Caps horizontal speed so the player remains controllable
     * and to reduce the chance of collision tunnelling.
     */
    void clampRunSpeed() {
        float maxRunSpeed = 220.0f;
        if (player.body.velocity.x > maxRunSpeed) player.body.velocity.x = maxRunSpeed;
        if (player.body.velocity.x < -maxRunSpeed) player.body.velocity.x = -maxRunSpeed;
    }

    /**
     * Handles right-click block placement.
     *
     * A block can only be placed if:
     * - the target tile is in bounds
     * - the tile is within reach
     * - the selected inventory slot contains a placeable block
     * - the target tile is empty
     * - the block would not overlap the player's AABB
     */
    public void mousePressed() {
        if (mouseButton != RIGHT) return;

        int tc = map.worldToTileCol(mouseX);
        int tr = map.worldToTileRow(mouseY);

        if (!map.inBounds(tr, tc)) return;
        if (!isTileInReach(tr, tc)) return;

        int placeType = inv.peekType(selectedSlot);
        if (!TileTypes.isPlaceable(placeType) || inv.peekCount(selectedSlot) <= 0) {
            setStatus("No blocks in selected slot.");
            return;
        }

        if (map.getTile(tr, tc) != TileTypes.AIR) return;
        if (aabbIntersectsTile(player, tr, tc, map.tileSize)) return;

        int consumed = inv.consumeFromSlot(selectedSlot);
        if (consumed == TileTypes.AIR) {
            setStatus("No blocks to place.");
            return;
        }

        map.setTile(tr, tc, consumed);
    }

    /**
     * Handles hold-to-mine block breaking.
     *
     * Mining only progresses while:
     * - left mouse is held
     * - the target block is in range
     * - the target block is breakable
     *
     * Once enough mining time has elapsed, the block is removed and
     * added to the inventory.
     */
    void updateMining(float dt) {
        if (!(mousePressed && mouseButton == LEFT)) {
            resetMining();
            return;
        }

        int tc = map.worldToTileCol(mouseX);
        int tr = map.worldToTileRow(mouseY);

        if (!map.inBounds(tr, tc) || !isTileInReach(tr, tc)) {
            resetMining();
            return;
        }

        int tile = map.getTile(tr, tc);

        if (!TileTypes.isBreakable(tile)) {
            resetMining();
            return;
        }

        // If the player starts mining a different tile, restart progress.
        if (!miningActive || tr != miningR || tc != miningC) {
            miningActive = true;
            miningR = tr;
            miningC = tc;
            miningProgress = 0f;
            miningRequired = TileTypes.breakTimeSeconds(tile);
        }

        miningProgress += dt;

        if (miningProgress >= miningRequired) {
            int brokenType = map.getTile(miningR, miningC);
            map.setTile(miningR, miningC, TileTypes.AIR);

            boolean added = inv.addBlock(brokenType);
            if (!added) {
                setStatus("Inventory full! Couldn't pick up " + TileTypes.name(brokenType));
            }

            resetMining();
        }
    }

    /**
     * Clears all mining state.
     */
    void resetMining() {
        miningActive = false;
        miningR = miningC = -1;
        miningProgress = 0f;
        miningRequired = 0f;
    }

    /**
     * Draws the tile outline under the cursor and, if mining is active,
     * draws the mining progress bar.
     *
     * The outline is only shown when the tile is within reach.
     */
    void drawTileCursorAndMiningUI() {
        int tc = map.worldToTileCol(mouseX);
        int tr = map.worldToTileRow(mouseY);

        if (!map.inBounds(tr, tc)) return;
        if (!isTileInReach(tr, tc)) return;

        noFill();
        stroke(255);
        strokeWeight(2);
        rectMode(CORNER);
        rect(tc * map.tileSize, tr * map.tileSize, map.tileSize, map.tileSize);

        if (miningActive && tr == miningR && tc == miningC && miningRequired > 0) {
            float t = constrain(miningProgress / miningRequired, 0f, 1f);

            noStroke();
            fill(0, 0, 0, 140);
            rect(tc * map.tileSize + 4, tr * map.tileSize + map.tileSize - 10,
                    map.tileSize - 8, 6);

            fill(255, 255, 255, 200);
            rect(tc * map.tileSize + 4, tr * map.tileSize + map.tileSize - 10,
                    (map.tileSize - 8) * t, 6);
        }

        strokeWeight(1);
    }

    /**
     * Checks whether a target tile is within the player's interaction range.
     */
    boolean isTileInReach(int tr, int tc) {
        float tileCenterX = (tc + 0.5f) * map.tileSize;
        float tileCenterY = (tr + 0.5f) * map.tileSize;
        float dx = tileCenterX - player.body.position.x;
        float dy = tileCenterY - player.body.position.y;
        return (dx * dx + dy * dy) <= interactRange * interactRange;
    }

    /**
     * Returns true if the player's AABB overlaps the given tile.
     * Used to prevent placing a block inside the player.
     */
    boolean aabbIntersectsTile(Player p, int tr, int tc, int tileSize) {
        float tileL = tc * tileSize;
        float tileR = tileL + tileSize;
        float tileT = tr * tileSize;
        float tileB = tileT + tileSize;

        return (p.right() > tileL && p.left() < tileR && p.bottom() > tileT && p.top() < tileB);
    }

    /**
     * Draws the hotbar UI at the bottom of the screen.
     *
     * Each slot shows:
     * - slot number
     * - block colour swatch
     * - item count
     * The selected slot is highlighted.
     */
    void drawHotbar() {
        int slots = inv.size();
        int boxSize = 54;
        int pad = 10;

        int totalW = slots * boxSize + (slots - 1) * pad;
        int startX = (width - totalW) / 2;
        int y = height - boxSize - 20;

        rectMode(CORNER);
        textAlign(CENTER, CENTER);

        for (int i = 0; i < slots; i++) {
            int x = startX + i * (boxSize + pad);

            noStroke();
            fill(0, 0, 0, 140);
            rect(x, y, boxSize, boxSize, 10);

            if (i == selectedSlot) {
                noFill();
                stroke(255);
                strokeWeight(3);
                rect(x, y, boxSize, boxSize, 10);
                strokeWeight(1);
            } else {
                noFill();
                stroke(120);
                rect(x, y, boxSize, boxSize, 10);
            }

            int t = inv.peekType(i);
            int count = inv.peekCount(i);

            if (t != TileTypes.AIR && count > 0) {
                int rgb = TileTypes.color(t);
                fill((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                noStroke();
                rect(x + 12, y + 12, boxSize - 24, boxSize - 24, 8);

                fill(255);
                textAlign(RIGHT, BOTTOM);
                text(count, x + boxSize - 8, y + boxSize - 6);
            }

            fill(220);
            textAlign(LEFT, TOP);
            text("" + (i + 1), x + 6, y + 5);
        }

        textAlign(LEFT, BASELINE);
    }

    /**
     * Checks whether the player has touched the grappling-hook pickup.
     * The pickup can only be collected once.
     */
    void updateGrapplePickup() {
        if (hasGrapplePickup || grappleUsed) return;

        float dx = player.body.position.x - grapplePickupPos.x;
        float dy = player.body.position.y - grapplePickupPos.y;
        float pickupRadius = 18.0f;

        if (dx * dx + dy * dy <= pickupRadius * pickupRadius) {
            hasGrapplePickup = true;
            setStatus("Picked up grappling hook.");
        }
    }

    /**
     * Draws the grapple pickup in the world if it has not been collected yet.
     */
    void drawGrapplePickup() {
        if (hasGrapplePickup || grappleUsed) return;

        noStroke();
        fill(255, 200, 0);
        circle(grapplePickupPos.x, grapplePickupPos.y, 18);

        fill(255);
        text("Grapple", grapplePickupPos.x - 22, grapplePickupPos.y - 14);
    }

    /**
     * Draws a small gold marker on the player when they are carrying
     * the grapple but have not used it yet.
     */
    void drawPlayerGrappleIndicator() {
        if (!hasGrapplePickup || grappleUsed) return;

        noStroke();
        fill(255, 200, 0);
        circle(player.body.position.x + 8, player.body.position.y - 10, 8);
    }

    /**
     * Draws the active grapple cable and its anchor point.
     */
    void drawGrappleCable() {
        if (!grappleActive) return;

        stroke(255, 220, 120);
        strokeWeight(3);
        line(
                grappleCable.anchor.x, grappleCable.anchor.y,
                player.body.position.x, player.body.position.y
        );
        strokeWeight(1);

        noStroke();
        fill(255, 180, 40);
        circle(grappleCable.anchor.x, grappleCable.anchor.y, 10);
    }

    /**
     * Returns true if the player owns an unused grapple and it is not already active.
     */
    boolean canAttachGrapple() {
        return hasGrapplePickup && !grappleUsed && !grappleActive;
    }

    /**
     * Activates the grapple by attaching it to the current mouse position,
     * as long as that position is within the allowed grapple range.
     */
    void activateGrapple() {
        float ax = mouseX;
        float ay = mouseY;

        float dx = ax - player.body.position.x;
        float dy = ay - player.body.position.y;
        float maxAttachRange = 220.0f;

        if (dx * dx + dy * dy > maxAttachRange * maxAttachRange) {
            setStatus("Target too far for grapple.");
            return;
        }

        grappleCable.attach(player.body, ax, ay);
        grappleActive = true;
        setStatus("Grapple attached.");
    }

    /**
     * Releases the grapple and consumes its single use.
     */
    void deactivateGrapple() {
        if (!grappleActive) return;

        grappleActive = false;
        grappleCable.detach();

        hasGrapplePickup = false;
        grappleUsed = true;

        setStatus("Grapple released and consumed.");
    }

    /**
     * Mouse wheel cycles through hotbar slots.
     */
    public void mouseWheel(MouseEvent event) {
        float e = event.getCount();
        if (e > 0) selectedSlot = (selectedSlot + 1) % inv.size();
        else if (e < 0) selectedSlot = (selectedSlot - 1 + inv.size()) % inv.size();
    }

    /**
     * Displays a temporary status message to the player.
     */
    void setStatus(String msg) {
        statusMsg = msg;
        statusTimer = 1.2f;
    }

    /**
     * Updates and expires the temporary HUD status message.
     */
    void updateStatus(float dt) {
        if (statusTimer > 0f) {
            statusTimer -= dt;
            if (statusTimer <= 0f) {
                statusTimer = 0f;
                statusMsg = "";
            }
        }
    }

    /**
     * Key press handler for movement, jumping, and grapple activation.
     */
    public void keyPressed() {
        if (key == 'a' || key == 'A') leftHeld = true;
        if (key == 'd' || key == 'D') rightHeld = true;

        if (key == ' ' || key == 'w' || key == 'W') {
            if (!jumpHeld) jumpPressedThisFrame = true;
            jumpHeld = true;
        }

        // E toggles the grapple on/off. A held-key guard is used to prevent
        // repeated toggles while the key remains down.
        if ((key == 'e' || key == 'E') && !eHeld) {
            eHeld = true;

            if (grappleActive) {
                deactivateGrapple();
            } else if (canAttachGrapple()) {
                activateGrapple();
            } else if (grappleUsed) {
                setStatus("Grapple already used.");
            } else {
                setStatus("Pick up the grappling hook first.");
            }
        }
    }

    /**
     * Key release handler for movement/jump/grapple state flags.
     */
    public void keyReleased() {
        if (key == 'a' || key == 'A') leftHeld = false;
        if (key == 'd' || key == 'D') rightHeld = false;

        if (key == ' ' || key == 'w' || key == 'W') jumpHeld = false;
        if (key == 'e' || key == 'E') eHeld = false;
    }
}