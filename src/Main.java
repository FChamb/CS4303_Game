import processing.core.PApplet;
import processing.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Main game loop and top level controller for the game.
 *
 * This class is responsible for:
 * - setting up the world, player, portal, and enemies
 * - updating the physics and AI systems each frame
 * - handling mining, placing, dropped items, and the grappling hook
 * - managing the camera and game states such as start, death, and win screens
 *
 * I kept Main as the overall coordinator, while moving physics, enemy behavior,
 * and pathfinding into separate classes so the code stays modular and easier to explain.
 */
public class Main extends PApplet {

    PhysicsWorld world;
    Player player;
    Portal portal;
    TileMap map;

    // Level 2/3/4 AI enemies
    Enemy[] enemies;

    // timing
    float lastTime;

    // camera
    float cameraX = 0;
    float cameraY = 0;

    // input state
    boolean leftHeld, rightHeld, jumpHeld;
    boolean jumpPressedThisFrame;

    // Toggleable helper/debug HUD text
    boolean showHelpText = false;
    boolean f3Held = false;

    // Maximum reach distance for mining and block placement.
    float interactRange = 160.0f;

    // Inventory / hotbar state: 2 slots only
    Inventory inv = new Inventory(2);
    int selectedSlot = 0;

    // Mining state for timed block breaking.
    boolean miningActive = false;
    int miningR = -1, miningC = -1;
    float miningProgress = 0f;
    float miningRequired = 0f;

    // Dropped block items in the world
    ArrayList<DroppedBlock> droppedBlocks = new ArrayList<>();

    // Temporary HUD message state.
    String statusMsg = "";
    float statusTimer = 0f;

    // ---------- GRAPPLING HOOK ----------
    boolean hasGrapplePickup = false;
    boolean grappleUsed = false;
    boolean grappleActive = false;
    boolean eHeld = false;

    Vec2 grapplePickupPos = new Vec2();
    GrappleCable grappleCable;

    // ---------- GAME STATE ----------
    enum GameState {
        START,
        PLAYING,
        DEAD,
        WON
    }

    GameState gameState = GameState.START;

    public static void main(String[] args) {
        PApplet.main("Main");
    }

    public void settings() {
        size(900, 600);
    }

    public void setup() {
        textFont(createFont("Arial", 16));
        resetGame();
        gameState = GameState.START;
    }

    /**
     * Creates a fresh run of the game world.
     */
    void resetGame() {
        world = new PhysicsWorld();

        int worldWidth = width * 6;
        int worldHeight = height * 14;

        map = new TileMap(worldWidth, worldHeight, 30);

        // Spawn player near the bottom-left start zone.
        player = new Player(180, map.getGroundTopY() - 120);
        player.body.invMass = 1.0f;
        world.addBody(player.body);

        // Portal high and far.
        portal = new Portal(worldWidth - 260, map.getUpperGoalY() - 40, 18);

        world.forceRegistry.add(player.body, new GravityForce(0, 900));

        // Spread enemies across the level
        enemies = new Enemy[4];
        enemies[0] = new Enemy(worldWidth * 0.22f, map.getGroundTopY() - 280);
        enemies[1] = new Enemy(worldWidth * 0.42f, map.getGroundTopY() - 620);
        enemies[2] = new Enemy(worldWidth * 0.62f, map.getGroundTopY() - 980);
        enemies[3] = new Enemy(worldWidth * 0.82f, map.getUpperGoalY() - 180);

        for (Enemy e : enemies) {
            world.addBody(e.body);
        }

        grappleCable = new GrappleCable();
        grapplePickupPos.set(map.getHiddenRewardX(), map.getHiddenRewardY());

        cameraX = 0;
        cameraY = 0;

        inv = new Inventory(2);
        selectedSlot = 0;

        miningActive = false;
        miningR = -1;
        miningC = -1;
        miningProgress = 0f;
        miningRequired = 0f;

        droppedBlocks = new ArrayList<>();

        hasGrapplePickup = false;
        grappleUsed = false;
        grappleActive = false;
        eHeld = false;

        statusMsg = "";
        statusTimer = 0f;

        leftHeld = false;
        rightHeld = false;
        jumpHeld = false;
        jumpPressedThisFrame = false;
        f3Held = false;

        lastTime = millis() / 1000.0f;
    }

    public void draw() {
        drawMountainBackground();

        if (gameState == GameState.START) {
            drawStartScreen();
            return;
        }

        if (gameState == GameState.DEAD) {
            drawDeathScreen();
            return;
        }

        if (gameState == GameState.WON) {
            drawWinScreen();
            return;
        }

        // ---------- PLAYING ----------
        float now = millis() / 1000.0f;
        float dt = now - lastTime;
        lastTime = now;

        dt = constrain(dt, 0.0f, 1.0f / 30.0f);

        updateGrapplePickup();

        for (Enemy e : enemies) {
            e.update(dt, player, map, enemies);
        }

        int subSteps = 6;
        float subDt = dt / subSteps;

        for (int i = 0; i < subSteps; i++) {
            handleInput(subDt);
            world.step(subDt);

            if (grappleActive) {
                grappleCable.enforce(player.body);
            }

            TileCollision.resolvePlayerVsTiles(player, map);

            applyFriction(subDt);
            clampRunSpeed();
        }

        if (anyEnemyTouchesPlayer()) {
            gameState = GameState.DEAD;
            return;
        }

        if (player.isAtPortal(portal)) {
            gameState = GameState.WON;
            return;
        }

        updateMining(dt);
        updateDroppedBlocks(dt);
        updateStatus(dt);
        updateCamera();

        // ---------- WORLD RENDERING ----------
        pushMatrix();
        translate(-cameraX, -cameraY);

        map.draw(this);
        portal.draw(this);
        drawGrapplePickup();
        drawGrappleCable();
        drawDroppedBlocks();

        for (Enemy e : enemies) {
            e.draw(this);
        }

        player.draw(this);
        drawPlayerGrappleIndicator();
        drawTileCursorAndMiningUI();

        popMatrix();

        // ---------- SCREEN-SPACE UI ----------
        drawHUD();
        drawHotbar();
    }

    void drawMountainBackground() {
        for (int y = 0; y < height; y++) {
            float t = y / (float) height;
            int r = (int) lerp(90, 220, t);
            int g = (int) lerp(130, 235, t);
            int b = (int) lerp(180, 255, t);
            stroke(r, g, b);
            line(0, y, width, y);
        }
        noStroke();

        fill(255, 245, 220, 90);
        circle(width - 120, 100, 120);
        fill(255, 245, 220, 180);
        circle(width - 120, 100, 70);

        float farOffset = -(cameraX * 0.08f) % width;
        fill(120, 140, 170, 180);
        for (int i = -1; i < 3; i++) {
            float baseX = i * width + farOffset;
            triangle(baseX - 80, height, baseX + 120, 220, baseX + 320, height);
            triangle(baseX + 180, height, baseX + 360, 180, baseX + 560, height);
        }

        float midOffset = -(cameraX * 0.16f) % width;
        fill(95, 110, 140, 220);
        for (int i = -1; i < 3; i++) {
            float baseX = i * width + midOffset;
            triangle(baseX - 50, height, baseX + 100, 260, baseX + 260, height);
            triangle(baseX + 150, height, baseX + 330, 210, baseX + 520, height);
            triangle(baseX + 390, height, baseX + 560, 260, baseX + 760, height);
        }

        float nearOffset = -(cameraX * 0.28f) % width;
        fill(60, 70, 90, 255);
        for (int i = -1; i < 3; i++) {
            float baseX = i * width + nearOffset;
            triangle(baseX - 60, height, baseX + 90, 340, baseX + 240, height);
            triangle(baseX + 110, height, baseX + 280, 300, baseX + 450, height);
            triangle(baseX + 360, height, baseX + 520, 350, baseX + 700, height);
        }

        fill(255, 255, 255, 60);
        ellipse(140, 120, 120, 40);
        ellipse(180, 115, 90, 35);
        ellipse(520, 90, 140, 45);
        ellipse(570, 82, 100, 35);
    }

    void drawStartScreen() {
        fill(255);
        textAlign(CENTER, CENTER);

        textSize(36);
        text("Sandbox AI Game", width / 2.0f, height / 2.0f - 90);

        textSize(18);
        text("Climb through the mountains, avoid the enemies, and reach the portal.", width / 2.0f, height / 2.0f - 35);
        text("Enemies use FSM and pathfinding. Resources are limited.", width / 2.0f, height / 2.0f - 5);

        textSize(16);
        text("Controls:", width / 2.0f, height / 2.0f + 45);
        text("A / D = Move    SPACE/W = Jump    Left Click = Mine", width / 2.0f, height / 2.0f + 75);
        text("Right Click = Place    Mouse Wheel = Hotbar    E = Grapple", width / 2.0f, height / 2.0f + 100);
        text("1 / 2 = Select hotbar slot    F3 = Toggle help text", width / 2.0f, height / 2.0f + 125);

        textSize(20);
        text("Press ENTER to Start", width / 2.0f, height / 2.0f + 180);

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawDeathScreen() {
        fill(255, 80, 80);
        textAlign(CENTER, CENTER);

        textSize(42);
        text("You Died", width / 2.0f, height / 2.0f - 40);

        fill(255);
        textSize(20);
        text("The enemies caught you.", width / 2.0f, height / 2.0f + 10);
        text("Press ENTER to return to the start screen.", width / 2.0f, height / 2.0f + 55);

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawWinScreen() {
        fill(80, 220, 120);
        textAlign(CENTER, CENTER);

        textSize(42);
        text("You Win!", width / 2.0f, height / 2.0f - 40);

        fill(255);
        textSize(20);
        text("You reached the portal and escaped the mountain.", width / 2.0f, height / 2.0f + 10);
        text("Press ENTER to return to the start screen.", width / 2.0f, height / 2.0f + 55);

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void updateCamera() {
        float targetX = player.body.position.x - width / 2.0f;
        float targetY = player.body.position.y - height / 2.0f;

        float maxCamX = max(0, map.getWorldWidth() - width);
        float maxCamY = max(0, map.getWorldHeight() - height);

        targetX = constrain(targetX, 0, maxCamX);
        targetY = constrain(targetY, 0, maxCamY);

        cameraX = lerp(cameraX, targetX, 0.12f);
        cameraY = lerp(cameraY, targetY, 0.12f);
    }

    boolean anyEnemyTouchesPlayer() {
        for (Enemy e : enemies) {
            float dx = e.body.position.x - player.body.position.x;
            float dy = e.body.position.y - player.body.position.y;
            float r = e.radius + Math.max(player.w, player.h) * 0.5f;
            if (dx * dx + dy * dy <= r * r) return true;
        }
        return false;
    }

    float mouseWorldX() {
        return mouseX + cameraX;
    }

    float mouseWorldY() {
        return mouseY + cameraY;
    }

    void drawHUD() {
        if (!showHelpText) return;

        fill(255);
        text("A/D move, SPACE jump | Hold LEFT = mine | RIGHT click = place | E = grapple", 20, 30);

        int selType = inv.peekType(selectedSlot);
        int selCount = inv.peekCount(selectedSlot);
        text("Selected Slot: " + (selectedSlot + 1) +
                "  Item: " + TileTypes.name(selType) +
                "  Count: " + selCount +
                "  (scroll wheel or 1/2 to switch)", 20, 52);

        String grappleText;
        if (grappleActive) grappleText = "Grapple: ACTIVE";
        else if (hasGrapplePickup) grappleText = "Grapple: READY (press E)";
        else if (grappleUsed) grappleText = "Grapple: USED";
        else grappleText = "Grapple: NOT COLLECTED";

        text(grappleText, 20, 74);

        int wandering = 0;
        int chasing = 0;

        for (Enemy e : enemies) {
            switch (e.state) {
                case WANDER -> wandering++;
                case CHASE -> chasing++;
            }
        }

        text("Enemy states - W: " + wandering + "  C: " + chasing, 20, 96);

        if (!statusMsg.isEmpty()) {
            text(statusMsg, 20, 118);
        }
    }

    void handleInput(float dt) {
        float moveForce = 3800.0f;

        player.thrust.set(0, 0);
        if (leftHeld) player.thrust.x -= moveForce;
        if (rightHeld) player.thrust.x += moveForce;

        player.body.addForce(player.thrust);

        if (jumpPressedThisFrame && player.grounded) {
            player.body.velocity.y = -430;
            player.grounded = false;
        }

        jumpPressedThisFrame = false;
    }

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

    void clampRunSpeed() {
        float maxRunSpeed = 220.0f;
        if (player.body.velocity.x > maxRunSpeed) player.body.velocity.x = maxRunSpeed;
        if (player.body.velocity.x < -maxRunSpeed) player.body.velocity.x = -maxRunSpeed;
    }

    public void mousePressed() {
        if (gameState != GameState.PLAYING) return;
        if (mouseButton != RIGHT) return;

        int tc = map.worldToTileCol(mouseWorldX());
        int tr = map.worldToTileRow(mouseWorldY());

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

    void updateMining(float dt) {
        if (!(mousePressed && mouseButton == LEFT)) {
            resetMining();
            return;
        }

        int tc = map.worldToTileCol(mouseWorldX());
        int tr = map.worldToTileRow(mouseWorldY());

        if (!map.inBounds(tr, tc) || !isTileInReach(tr, tc)) {
            resetMining();
            return;
        }

        int tile = map.getTile(tr, tc);

        if (!TileTypes.isBreakable(tile)) {
            resetMining();
            return;
        }

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

            // Break the block and drop it into the world
            map.setTile(miningR, miningC, TileTypes.AIR);

            float dropX = (miningC + 0.5f) * map.tileSize;
            float dropY = (miningR + 0.5f) * map.tileSize;

            droppedBlocks.add(new DroppedBlock(brokenType, dropX, dropY));

            resetMining();
        }
    }

    void updateDroppedBlocks(float dt) {
        Iterator<DroppedBlock> it = droppedBlocks.iterator();

        while (it.hasNext()) {
            DroppedBlock drop = it.next();
            drop.update(dt, map);

            if (drop.canBePickedUpBy(player) && inv.canAddBlock(drop.blockType)) {
                inv.addBlock(drop.blockType);
                it.remove();
            }
        }
    }

    void drawDroppedBlocks() {
        for (DroppedBlock drop : droppedBlocks) {
            drop.draw(this);
        }
    }

    void resetMining() {
        miningActive = false;
        miningR = -1;
        miningC = -1;
        miningProgress = 0f;
        miningRequired = 0f;
    }

    void drawTileCursorAndMiningUI() {
        int tc = map.worldToTileCol(mouseWorldX());
        int tr = map.worldToTileRow(mouseWorldY());

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

    boolean isTileInReach(int tr, int tc) {
        float tileCenterX = (tc + 0.5f) * map.tileSize;
        float tileCenterY = (tr + 0.5f) * map.tileSize;
        float dx = tileCenterX - player.body.position.x;
        float dy = tileCenterY - player.body.position.y;
        return (dx * dx + dy * dy) <= interactRange * interactRange;
    }

    boolean aabbIntersectsTile(Player p, int tr, int tc, int tileSize) {
        float tileL = tc * tileSize;
        float tileR = tileL + tileSize;
        float tileT = tr * tileSize;
        float tileB = tileT + tileSize;

        return (p.right() > tileL && p.left() < tileR && p.bottom() > tileT && p.top() < tileB);
    }

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

    void drawGrapplePickup() {
        if (hasGrapplePickup || grappleUsed) return;

        noStroke();
        fill(255, 200, 0);
        circle(grapplePickupPos.x, grapplePickupPos.y, 18);
    }

    void drawPlayerGrappleIndicator() {
        if (!hasGrapplePickup || grappleUsed) return;

        noStroke();
        fill(255, 200, 0);
        circle(player.body.position.x + 8, player.body.position.y - 10, 8);
    }

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

    boolean canAttachGrapple() {
        return hasGrapplePickup && !grappleUsed && !grappleActive;
    }

    void activateGrapple() {
        float ax = mouseWorldX();
        float ay = mouseWorldY();

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

    void deactivateGrapple() {
        if (!grappleActive) return;

        grappleActive = false;
        grappleCable.detach();

        hasGrapplePickup = false;
        grappleUsed = true;

        setStatus("Grapple released and consumed.");
    }

    public void mouseWheel(MouseEvent event) {
        if (gameState != GameState.PLAYING) return;

        float e = event.getCount();
        if (e > 0) selectedSlot = (selectedSlot + 1) % inv.size();
        else if (e < 0) selectedSlot = (selectedSlot - 1 + inv.size()) % inv.size();
    }

    void setStatus(String msg) {
        statusMsg = msg;
        statusTimer = 1.2f;
    }

    void updateStatus(float dt) {
        if (statusTimer > 0f) {
            statusTimer -= dt;
            if (statusTimer <= 0f) {
                statusTimer = 0f;
                statusMsg = "";
            }
        }
    }

    public void keyPressed() {
        if (gameState == GameState.START) {
            if (key == ENTER || key == RETURN) {
                resetGame();
                gameState = GameState.PLAYING;
            }
            return;
        }

        if (gameState == GameState.DEAD) {
            if (key == ENTER || key == RETURN) {
                resetGame();
                gameState = GameState.START;
            }
            return;
        }

        if (gameState == GameState.WON) {
            if (key == ENTER || key == RETURN) {
                resetGame();
                gameState = GameState.START;
            }
            return;
        }

        if (keyCode == 114 && !f3Held) {
            f3Held = true;
            showHelpText = !showHelpText;
        }

        if (key == 'a' || key == 'A') leftHeld = true;
        if (key == 'd' || key == 'D') rightHeld = true;

        if (key == ' ' || key == 'w' || key == 'W') {
            if (!jumpHeld) jumpPressedThisFrame = true;
            jumpHeld = true;
        }

        if (key == '1') selectedSlot = 0;
        if (key == '2') selectedSlot = 1;

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

    public void keyReleased() {
        if (gameState != GameState.PLAYING) return;

        if (key == 'a' || key == 'A') leftHeld = false;
        if (key == 'd' || key == 'D') rightHeld = false;

        if (key == ' ' || key == 'w' || key == 'W') jumpHeld = false;
        if (key == 'e' || key == 'E') eHeld = false;

        if (keyCode == 114) f3Held = false;
    }
}