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

    // ---------- LEVEL / PCG ----------
    LevelManager levelManager;

    // ---------- GAME STATE ----------
    enum GameState {
        START,
        PLAYING,
        DEAD,
        WON
    }

    GameState gameState = GameState.START;

    enum Objective {
        FIND_GRAPPLE,
        FIND_ENEMY,
        REACH_PORTAL
    }

    // Jump forgiveness to make controls feel less punishing.
    float coyoteTimer = 0f;
    float jumpBufferTimer = 0f;
    final float coyoteTimeWindow = 0.10f;
    final float jumpBufferWindow = 0.12f;

    // Basic onboarding progress tracking (mainly for level 1 hints).
    boolean hasMovedHorizontally = false;
    boolean hasMinedAnyBlock = false;
    boolean hasPlacedAnyBlock = false;
    boolean tutorialEnemySpotted = false;
    boolean hasShownStackLimitTip = false;

    float tutorialEnemyHintCooldown = 0f;

    // ---------- START SCREEN / SEED UI ----------
    long campaignSeed = 0L;
    String seedInputText = "";
    boolean seedEditMode = false;
    boolean startGenerating = false;
    float startGenerationStartSec = 0f;
    final float startGenerationDurationSec = 0.95f;

    String startMenuMessage = "";
    float startMenuMessageUntilSec = 0f;

    final String[] generationSteps = {
            "Preparing chunks...",
            "Shaping terrain...",
            "Placing enemies...",
            "Finalizing mountain..."
    };

    public static void main(String[] args) {
        PApplet.main("Main");
    }

    public void settings() {
        size(900, 600);
    }

    public void setup() {
        textFont(createFont("Arial", 16));
        surface.setTitle("Mountain Ascent");
        campaignSeed = System.currentTimeMillis();
        seedInputText = Long.toString(campaignSeed);
        levelManager = new LevelManager(campaignSeed);
        gameState = GameState.START;
    }

    /**
     * Creates a fresh run using a procedurally generated level from the
     * current LevelManager state.
     */
    void resetGame() {
        world = new PhysicsWorld();

        int worldWidth  = width * 6;
        int worldHeight = height * 14;

        // Procedural generation — LevelGenerator coordinates all sub-generators
        LevelGenerator gen = levelManager.createGenerator(worldWidth, worldHeight, 30);
        map = new TileMap(gen, worldWidth, worldHeight, 30);
        // gen.generate() ran inside TileMap; all metadata fields are now populated

        player = new Player(gen.playerSpawnX, gen.playerSpawnY);
        player.body.invMass = 1.0f;
        world.addBody(player.body);

        portal = new Portal(gen.portalX, gen.portalY, 18);

        world.forceRegistry.add(player.body, new GravityForce(0, 900));

        // Procedurally placed enemies with difficulty-scaled speeds.
        // Level 1 keeps exactly one tutorial enemy and tunes it to be non-threatening.
        int enemyCount = gen.enemySpawns.length;
        if (levelManager.getLevelNumber() == 1) enemyCount = Math.min(1, gen.enemySpawns.length);
        enemies = new Enemy[enemyCount];
        for (int i = 0; i < enemyCount; i++) {
            enemies[i] = new Enemy(gen.enemySpawns[i][0], gen.enemySpawns[i][1]);
            enemies[i].wanderSpeed    *= gen.enemySpeedScale;
            enemies[i].chaseSpeedMin  *= gen.enemySpeedScale;
            enemies[i].chaseSpeedMax  *= gen.enemySpeedScale;
            world.addBody(enemies[i].body);
        }
        if (levelManager.getLevelNumber() == 1 && enemies.length > 0) {
            Enemy introEnemy = enemies[0];
            introEnemy.wanderSpeed = 30f;
            introEnemy.chaseSpeedMin = 52f;
            introEnemy.chaseSpeedMax = 72f;
            introEnemy.currentMaxSpeed = introEnemy.wanderSpeed;
            introEnemy.detectionRange = 120f;
            introEnemy.separationRange = 30f;
            introEnemy.radius = 11f;

            // Keep the tutorial enemy around the middle of the level route.
            float midX = player.body.position.x + (portal.pos.x - player.body.position.x) * 0.55f;
            float midY = player.body.position.y + (portal.pos.y - player.body.position.y) * 0.50f;
            introEnemy.body.position.x = midX;
            introEnemy.body.position.y = midY - 35f;
            introEnemy.body.velocity.set(0f, 0f);
        }

        grappleCable = new GrappleCable();
        grapplePickupPos.set(gen.grappleX, gen.grappleY);

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

        coyoteTimer = 0f;
        jumpBufferTimer = 0f;
        hasMovedHorizontally = false;
        hasMinedAnyBlock = false;
        hasPlacedAnyBlock = false;
        tutorialEnemySpotted = false;
        hasShownStackLimitTip = false;
        tutorialEnemyHintCooldown = 0f;

        lastTime = millis() / 1000.0f;

        if (levelManager.getLevelNumber() == 1) {
            setStatus("Objective: Mine/place blocks, find grapple, locate enemy, then reach portal.", 4.2f);
        } else {
            setStatus("Objective: Reach the glowing portal.", 3.2f);
        }
    }

    public void draw() {
        if (gameState == GameState.START) {
            drawTitleBackground();
            drawStartScreen();
            return;
        }

        drawMountainBackground();

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
        updateTutorialEnemyProgress();

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
        drawObjectiveBeaconWorld();
        portal.draw(this);
        drawGrapplePickup();
        drawWorldPrompts();
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
        drawObjectivePointer();
        drawOnboardingHint();
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

    void drawTitleBackground() {
        rectMode(CORNER);
        for (int y = 0; y < height; y++) {
            float t = y / (float) height;
            int r = (int) lerp(90, 220, t);
            int g = (int) lerp(130, 235, t);
            int b = (int) lerp(180, 255, t);
            stroke(r, g, b);
            line(0, y, width, y);
        }
        noStroke();

        int block = 12;
        int cols = 74;
        int rows = 52;
        int originX = (width - cols * block) / 2;
        int originY = height - rows * block;

        int peakCol = cols / 2;
        int peakRow = 2;
        int baseRow = 39;
        int halfWidth = 32;

        for (int c = 0; c < cols; c++) {
            int dist = abs(c - peakCol);
            int topRow;

            if (dist <= halfWidth) {
                float t = dist / (float) halfWidth;
                topRow = peakRow + (int) ((baseRow - peakRow) * pow(t, 1.70f));

                int stepNoise = ((c * 17 + 11) % 7) - 3;
                if (stepNoise > 1) topRow += 1;
                if (stepNoise < -2) topRow -= 1;
                topRow = constrain(topRow, peakRow, baseRow - 1);
            } else {
                // Fill the remaining title space with world-like side terrain.
                int sideStep = ((c * 13 + 5) % 3 == 0) ? 1 : 0;
                topRow = baseRow + 1 + sideStep;
            }

            for (int r = topRow; r < rows; r++) {
                int type;
                if (r == topRow) type = TileTypes.GRASS;
                else if (r <= topRow + 2) type = TileTypes.DIRT;
                else type = TileTypes.STONE;

                int x = originX + c * block;
                int yPos = originY + r * block;
                drawTitleBlock(x, yPos, block, type);
            }
        }

        int caveCol = peakCol - 28;
        int caveRow = 34;
        drawTitleCave(originX, originY, block, caveCol, caveRow);
        drawHiddenTitleGrapple(originX, originY, block, caveCol + 1, caveRow + 1);

        // Put enemy easter eggs high in the sky.
        drawHiddenTitleEnemySky(width * 0.18f, 118, block);
        drawHiddenTitleEnemySky(width * 0.83f, 148, block);
    }

    void drawTitleBlock(int x, int y, int size, int type) {
        int rgb = TileTypes.color(type);
        int rr = (rgb >> 16) & 0xFF;
        int gg = (rgb >> 8) & 0xFF;
        int bb = rgb & 0xFF;

        noStroke();
        fill(rr, gg, bb);
        rect(x, y, size, size);

        stroke(0, 0, 0, 34);
        noFill();
        rect(x, y, size, size);
        noStroke();
    }

    void drawHiddenTitleEnemySky(float x, float y, int block) {
        float cell = block * 0.72f;

        noStroke();
        fill(122, 96, 78, 170);
        rect(x - cell * 1.5f, y - cell * 1.2f, cell * 3, cell * 2.5f);
        rect(x - cell * 2.4f, y - cell * 0.3f, cell, cell);
        rect(x + cell * 1.4f, y - cell * 0.3f, cell, cell);

        fill(218, 214, 205, 190);
        rect(x + cell * 0.2f, y - cell * 0.45f, cell * 0.6f, cell * 0.6f);
    }

    void drawTitleCave(int originX, int originY, int block, int col, int row) {
        // Blocky cave opening on the mountain side.
        int startX = originX + (col - 1) * block;
        int startY = originY + (row - 1) * block;
        int[][] cells = {
                {0, 0}, {1, 0}, {2, 0},
                {0, 1}, {1, 1}, {2, 1}, {3, 1},
                {1, 2}, {2, 2}
        };

        noStroke();
        fill(36, 52, 68, 220);
        for (int[] cell : cells) {
            rect(startX + cell[0] * block, startY + cell[1] * block, block, block);
        }

        fill(18, 28, 38, 185);
        rect(startX + block, startY + block, block * 2, block);
    }

    void drawHiddenTitleGrapple(int originX, int originY, int block, int col, int row) {
        fill(186, 150, 72, 190);
        rect(originX + col * block + 2, originY + row * block + 2, block - 4, block * 3 - 4);
        rect(originX + (col - 1) * block + 2, originY + (row + 1) * block + 2, block * 3 - 4, block - 4);

        fill(255, 220, 120, 180);
        rect(originX + col * block + 4, originY + (row + 1) * block + 4, block - 8, block - 8);
    }

    void drawStartScreen() {
        float panelW = min(620, width - 84);
        float panelH = 206;
        float panelX = (width - panelW) * 0.5f;
        float panelY = height * 0.50f - panelH * 0.5f;

        float cardPad = 20;
        float btnW = panelW - cardPad * 2;
        float btnH = 52;
        float generateX = panelX + cardPad;
        float btnY = panelY + 118;

        float inputX = panelX + cardPad;
        float inputY = panelY + 56;
        float inputW = panelW - cardPad * 2;
        float inputH = 46;

        noStroke();
        fill(4, 8, 16, 120);
        rect(panelX + 4, panelY + 6, panelW, panelH, 14);

        fill(11, 20, 34, 198);
        rect(panelX, panelY, panelW, panelH, 14);
        stroke(180, 220, 255, 105);
        strokeWeight(1.4f);
        noFill();
        rect(panelX, panelY, panelW, panelH, 14);
        noStroke();

        fill(255);
        textAlign(CENTER, CENTER);
        textSize(64);
        text("Mountain Ascent", width / 2.0f, 84);

        boolean hoverGenerate = !startGenerating && pointInRect(mouseX, mouseY, generateX, btnY, btnW, btnH);
        drawMenuButton(generateX, btnY, btnW, btnH, "Generate World", hoverGenerate, true);

        long previewSeed = campaignSeed;
        boolean inputHover = pointInRect(mouseX, mouseY, inputX, inputY, inputW, inputH);
        fill(190, 215, 238);
        textAlign(LEFT, BASELINE);
        textSize(13);
        text("SEED", inputX + 2, inputY - 8);

        stroke(seedEditMode ? color(255, 228, 136) : color(150, 184, 214, inputHover ? 230 : 185));
        strokeWeight(seedEditMode ? 2.2f : 1.3f);
        fill(18, 29, 46, 230);
        rect(inputX, inputY, inputW, inputH, 10);
        noStroke();
        fill(35, 54, 78, 85);
        rect(inputX + 2, inputY + 2, inputW - 4, inputH * 0.45f, 8);

        String shownSeed = seedInputText.isEmpty() ? " " : seedInputText;
        if (seedEditMode && frameCount % 40 < 20 && seedInputText.length() < 32) {
            shownSeed += "_";
        }

        textAlign(LEFT, CENTER);
        textSize(16);
        fill(240, 246, 255);
        text(shownSeed, inputX + 12, inputY + inputH * 0.5f);

        fill(190, 215, 238);
        textSize(13);
        text("Click seed field to edit. Resolved long seed: " + previewSeed, inputX, panelY + panelH - 16);

        float now = millis() / 1000.0f;
        if (!startMenuMessage.isEmpty() && now <= startMenuMessageUntilSec) {
            textAlign(CENTER, CENTER);
            textSize(14);
            fill(255, 238, 170);
            text(startMenuMessage, width * 0.5f, panelY + panelH + 24);
        }

        if (startGenerating) {
            float elapsed = now - startGenerationStartSec;
            float progress = constrain(elapsed / startGenerationDurationSec, 0f, 1f);
            int stepIdx = min(generationSteps.length - 1, (int) (progress * generationSteps.length));

            noStroke();
            fill(8, 12, 22, 170);
            rect(0, 0, width, height);

            float overlayW = min(460, width - 120);
            float overlayH = 134;
            float overlayX = (width - overlayW) * 0.5f;
            float overlayY = height * 0.58f - overlayH * 0.5f;
            fill(16, 24, 38, 230);
            rect(overlayX, overlayY, overlayW, overlayH, 12);

            textAlign(CENTER, CENTER);
            textSize(22);
            fill(255);
            text("Generating World", width * 0.5f, overlayY + 36);

            textSize(15);
            fill(205, 230, 255);
            text(generationSteps[stepIdx], width * 0.5f, overlayY + 62);

            float barX = overlayX + 26;
            float barY = overlayY + 84;
            float barW = overlayW - 52;
            float barH = 18;
            fill(34, 50, 70, 210);
            rect(barX, barY, barW, barH, 9);
            fill(140, 215, 255);
            rect(barX, barY, barW * progress, barH, 9);

            if (progress >= 1f) {
                startGenerating = false;
                levelManager = new LevelManager(campaignSeed);
                resetGame();
                gameState = GameState.PLAYING;
            }
        }

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawMenuButton(float x, float y, float w, float h, String label, boolean hovered, boolean primary) {
        noStroke();
        if (primary) {
            fill(hovered ? 108 : 82, hovered ? 196 : 168, hovered ? 136 : 116, 242);
        } else {
            fill(hovered ? 68 : 52, hovered ? 104 : 86, hovered ? 138 : 116, 228);
        }
        rect(x, y, w, h, 10);

        fill(255, 255, 255, hovered ? 32 : 20);
        rect(x + 2, y + 2, w - 4, h * 0.45f, 8);

        stroke(255, hovered ? 220 : 165);
        strokeWeight(hovered ? 2.1f : 1.25f);
        noFill();
        rect(x, y, w, h, 10);

        textAlign(CENTER, CENTER);
        textSize(18);
        fill(247, 252, 255);
        text(label, x + w * 0.5f, y + h * 0.5f);
    }

    boolean pointInRect(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    long seedFromText(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            long hash = 1469598103934665603L;
            for (int i = 0; i < text.length(); i++) {
                hash ^= text.charAt(i);
                hash *= 1099511628211L;
            }
            if (hash == 0L) hash = 1L;
            return hash;
        }
    }

    void applySeedInput() {
        String trimmed = seedInputText.trim();
        if (trimmed.isEmpty()) {
            campaignSeed = System.currentTimeMillis();
            seedInputText = Long.toString(campaignSeed);
            startMenuMessage = "Empty seed -> generated random seed " + campaignSeed;
        } else {
            campaignSeed = seedFromText(trimmed);
            startMenuMessage = "Seed resolved to " + campaignSeed;
        }
        startMenuMessageUntilSec = millis() / 1000.0f + 2.2f;
        levelManager = new LevelManager(campaignSeed);
    }

    void refreshSeedFromInput() {
        String trimmed = seedInputText.trim();
        if (trimmed.isEmpty()) return;
        campaignSeed = seedFromText(trimmed);
        levelManager = new LevelManager(campaignSeed);
    }

    void beginStartGeneration() {
        applySeedInput();
        startGenerating = true;
        startGenerationStartSec = millis() / 1000.0f;
        seedEditMode = false;
    }

    void drawDeathScreen() {
        fill(255, 80, 80);
        textAlign(CENTER, CENTER);

        textSize(42);
        text("You Died", width / 2.0f, height / 2.0f - 40);

        fill(255);
        textSize(20);
        text("The enemies caught you.", width / 2.0f, height / 2.0f + 10);
        text("Press ENTER to retry this level.", width / 2.0f, height / 2.0f + 55);

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawWinScreen() {
        textAlign(CENTER, CENTER);

        if (levelManager.hasNextLevel()) {
            fill(80, 220, 120);
            textSize(42);
            text("Level " + levelManager.getLevelNumber() + " Complete!",
                    width / 2.0f, height / 2.0f - 60);

            fill(255);
            textSize(20);
            text("You reached the portal and escaped the mountain.",
                    width / 2.0f, height / 2.0f - 5);

            textSize(17);
            fill(180, 230, 255);
            text("Next: Level " + (levelManager.getLevelNumber() + 1)
                    + "   Difficulty " + levelManager.getNextDifficulty() + " / 10",
                    width / 2.0f, height / 2.0f + 35);

            fill(255);
            textSize(18);
            text("Press ENTER to continue.", width / 2.0f, height / 2.0f + 72);

        } else {
            fill(255, 215, 0);
            textSize(42);
            text("You Win!", width / 2.0f, height / 2.0f - 60);

            fill(255);
            textSize(22);
            text("All " + levelManager.getTotalLevels() + " mountains conquered!",
                    width / 2.0f, height / 2.0f - 5);
            textSize(18);
            text("Press ENTER to return to the start screen.",
                    width / 2.0f, height / 2.0f + 45);
        }

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
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            float dx = e.body.position.x - player.body.position.x;
            float dy = e.body.position.y - player.body.position.y;
            float r = e.radius + Math.max(player.w, player.h) * 0.5f;
            if (dx * dx + dy * dy > r * r) continue;

            // Level-1 tutorial enemy does not kill the player.
            if (levelManager.getLevelNumber() == 1 && i == 0) {
                tutorialEnemySpotted = true;
                if (tutorialEnemyHintCooldown <= 0f) {
                    setStatus("Tutorial enemy: avoid it and keep moving.", 2.4f);
                    tutorialEnemyHintCooldown = 2.4f;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    float mouseWorldX() {
        return mouseX + cameraX;
    }

    float mouseWorldY() {
        return mouseY + cameraY;
    }

    Objective getCurrentObjective() {
        if (levelManager.getLevelNumber() == 1) {
            if (!hasGrapplePickup && !grappleUsed) return Objective.FIND_GRAPPLE;
            if (enemies.length > 0 && !tutorialEnemySpotted) return Objective.FIND_ENEMY;
        }
        return Objective.REACH_PORTAL;
    }

    float objectiveTargetX(Objective objective) {
        return switch (objective) {
            case FIND_GRAPPLE -> grapplePickupPos.x;
            case FIND_ENEMY -> (enemies.length > 0) ? enemies[0].body.position.x : portal.pos.x;
            case REACH_PORTAL -> portal.pos.x;
        };
    }

    float objectiveTargetY(Objective objective) {
        return switch (objective) {
            case FIND_GRAPPLE -> grapplePickupPos.y;
            case FIND_ENEMY -> (enemies.length > 0) ? enemies[0].body.position.y : portal.pos.y;
            case REACH_PORTAL -> portal.pos.y;
        };
    }

    String objectiveLabel(Objective objective) {
        return switch (objective) {
            case FIND_GRAPPLE -> "Find Grappling Hook";
            case FIND_ENEMY -> "Locate Enemy";
            case REACH_PORTAL -> "Reach Portal";
        };
    }

    void drawObjectiveBeaconWorld() {
        if (levelManager.getLevelNumber() != 1) return;

        Objective objective = getCurrentObjective();
        float tx = objectiveTargetX(objective);
        float ty = objectiveTargetY(objective);
        float pulse = 0.5f + 0.5f * sin(millis() * 0.006f);

        noStroke();
        switch (objective) {
            case FIND_GRAPPLE -> {
                fill(255, 210, 40, 90 + (int)(60 * pulse));
                circle(tx, ty, 42 + 8 * pulse);
            }
            case FIND_ENEMY -> {
                fill(255, 120, 120, 78 + (int)(56 * pulse));
                circle(tx, ty, 48 + 8 * pulse);
            }
            case REACH_PORTAL -> {
                fill(200, 120, 255, 70 + (int)(55 * pulse));
                circle(tx, ty, 52 + 10 * pulse);
            }
        }
    }

    void drawWorldPrompts() {
        if (levelManager.getLevelNumber() != 1) return;

        textAlign(CENTER, CENTER);
        textSize(14);

        if (!hasGrapplePickup && !grappleUsed) {
            float dx = player.body.position.x - grapplePickupPos.x;
            float dy = player.body.position.y - grapplePickupPos.y;
            if (dx * dx + dy * dy <= 320f * 320f) {
                fill(255, 240, 140);
                text("Collect Grappling Hook", grapplePickupPos.x, grapplePickupPos.y - 28);
            }
        }

        if (enemies.length > 0 && hasGrapplePickup && !tutorialEnemySpotted) {
            Enemy introEnemy = enemies[0];
            float edx = player.body.position.x - introEnemy.body.position.x;
            float edy = player.body.position.y - introEnemy.body.position.y;
            if (edx * edx + edy * edy <= 420f * 420f) {
                fill(255, 180, 180);
                text("Tutorial Enemy (slow)", introEnemy.body.position.x, introEnemy.body.position.y - 26);
            }
        }

        float pdx = player.body.position.x - portal.pos.x;
        float pdy = player.body.position.y - portal.pos.y;
        if (pdx * pdx + pdy * pdy <= 300f * 300f) {
            fill(230, 190, 255);
            text("Enter Portal", portal.pos.x, portal.pos.y - 34);
        }

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawObjectivePointer() {
        if (levelManager.getLevelNumber() != 1) return;

        Objective objective = getCurrentObjective();
        float tx = objectiveTargetX(objective);
        float ty = objectiveTargetY(objective);
        float sx = tx - cameraX;
        float sy = ty - cameraY;

        float dx = sx - width * 0.5f;
        float dy = sy - height * 0.5f;
        float dist = sqrt(dx * dx + dy * dy);
        float toTargetX = tx - player.body.position.x;
        float toTargetY = ty - player.body.position.y;
        float worldDist = sqrt(toTargetX * toTargetX + toTargetY * toTargetY);

        // Always show objective summary.
        fill(255, 255, 255, 220);
        textSize(16);
        textAlign(CENTER, TOP);
        text("Objective: " + objectiveLabel(objective), width * 0.5f, 12);
        textSize(13);
        fill(200, 230, 255, 215);
        text("Distance: " + (int)worldDist + " px", width * 0.5f, 32);

        // Draw edge arrow if target is off-screen.
        float margin = 40f;
        if (sx < margin || sx > width - margin || sy < margin || sy > height - margin) {
            float nx = dx / Math.max(0.001f, dist);
            float ny = dy / Math.max(0.001f, dist);

            float txEdge = width * 0.5f;
            float tyEdge = height * 0.5f;

            float maxX = width * 0.5f - margin;
            float maxY = height * 0.5f - margin;
            float scaleX = (Math.abs(nx) > 0.001f) ? maxX / Math.abs(nx) : Float.MAX_VALUE;
            float scaleY = (Math.abs(ny) > 0.001f) ? maxY / Math.abs(ny) : Float.MAX_VALUE;
            float edgeScale = Math.min(scaleX, scaleY);

            txEdge += nx * edgeScale;
            tyEdge += ny * edgeScale;

            pushMatrix();
            translate(txEdge, tyEdge);
            rotate(atan2(ny, nx));
            noStroke();
            fill(255, 230, 140, 230);
            triangle(12, 0, -10, -8, -10, 8);
            popMatrix();
        }

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawOnboardingHint() {
        if (levelManager.getLevelNumber() != 1) return;

        String hint;
        if (!hasMovedHorizontally) {
            hint = "Move with A/D. Jump with SPACE or W.";
        } else if (!hasMinedAnyBlock) {
            hint = "Hold LEFT CLICK on a block to mine resources.";
        } else if (!hasPlacedAnyBlock) {
            hint = "RIGHT CLICK to place the selected block.";
        } else if (!hasGrapplePickup && !grappleUsed) {
            hint = "Follow the objective marker to find the grapple.";
        } else if (enemies.length > 0 && !tutorialEnemySpotted) {
            hint = "Next: find the tutorial enemy. It is slow and easy to avoid.";
        } else if (hasGrapplePickup && !grappleUsed && !grappleActive) {
            hint = "Press E to attach grapple. Press E again to disconnect (one-time use).";
        } else if (grappleActive) {
            hint = "Grapple active: press E to disconnect and consume it.";
        } else if (tutorialEnemySpotted && !player.isAtPortal(portal)) {
            hint = "Future levels add more enemies that detect and chase faster.";
        } else {
            hint = "Reach the glowing portal to clear the level.";
        }

        float boxW = Math.min(560, width - 40);
        float boxH = 34;
        float x = (width - boxW) * 0.5f;
        float y = height - 122;

        noStroke();
        fill(18, 25, 36, 170);
        rectMode(CORNER);
        rect(x, y, boxW, boxH, 8);

        fill(240, 245, 255);
        textAlign(CENTER, CENTER);
        textSize(14);
        text(hint, width * 0.5f, y + boxH * 0.5f);

        textAlign(LEFT, BASELINE);
        textSize(16);
    }

    void drawHUD() {
        // Persistent level + difficulty indicator (always shown)
        fill(255, 255, 255, 200);
        textSize(14);
        textAlign(RIGHT, TOP);
        text("Level " + levelManager.getLevelNumber() + " / " + levelManager.getTotalLevels()
                + "   Diff " + levelManager.getDifficulty() + "/10",
                width - 12, 10);
        textAlign(LEFT, BASELINE);
        textSize(16);

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
        if (leftHeld || rightHeld) hasMovedHorizontally = true;

        player.body.addForce(player.thrust);

        if (jumpPressedThisFrame) {
            jumpBufferTimer = jumpBufferWindow;
        }
        jumpPressedThisFrame = false;

        if (player.grounded) {
            coyoteTimer = coyoteTimeWindow;
        } else {
            coyoteTimer = max(0f, coyoteTimer - dt);
        }
        jumpBufferTimer = max(0f, jumpBufferTimer - dt);

        if (jumpBufferTimer > 0f && coyoteTimer > 0f) {
            player.body.velocity.y = -430;
            player.grounded = false;
            jumpBufferTimer = 0f;
            coyoteTimer = 0f;
        }
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
        if (gameState == GameState.START) {
            if (startGenerating) return;

            float panelW = min(620, width - 84);
            float panelH = 206;
            float panelX = (width - panelW) * 0.5f;
            float panelY = height * 0.50f - panelH * 0.5f;
            float cardPad = 20;
            float btnW = panelW - cardPad * 2;
            float btnH = 52;
            float generateX = panelX + cardPad;
            float btnY = panelY + 118;
            float inputX = panelX + cardPad;
            float inputY = panelY + 56;
            float inputW = panelW - cardPad * 2;
            float inputH = 46;

            if (pointInRect(mouseX, mouseY, generateX, btnY, btnW, btnH)) {
                beginStartGeneration();
                return;
            }

            seedEditMode = pointInRect(mouseX, mouseY, inputX, inputY, inputW, inputH);
            return;
        }

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
        hasPlacedAnyBlock = true;
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
            hasMinedAnyBlock = true;
            if (levelManager.getLevelNumber() == 1 && !hasShownStackLimitTip) {
                setStatus("Tip: inventory holds up to 4 blocks per slot.", 2.4f);
                hasShownStackLimitTip = true;
            }

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

            if (drop.canBePickedUpBy(player)) {
                if (inv.canAddBlock(drop.blockType)) {
                    inv.addBlock(drop.blockType);
                    it.remove();
                }
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
            setStatus("Picked up grappling hook (one-time use).");
        }
    }

    void drawGrapplePickup() {
        if (hasGrapplePickup || grappleUsed) return;

        float pulse = 0.5f + 0.5f * sin(millis() * 0.01f);
        noStroke();
        fill(255, 210, 40, 85 + (int)(65 * pulse));
        circle(grapplePickupPos.x, grapplePickupPos.y, 36 + pulse * 9);

        fill(255, 220, 70);
        circle(grapplePickupPos.x, grapplePickupPos.y, 18);

        stroke(255, 245, 150, 220);
        strokeWeight(2);
        line(grapplePickupPos.x - 10, grapplePickupPos.y, grapplePickupPos.x + 10, grapplePickupPos.y);
        line(grapplePickupPos.x, grapplePickupPos.y - 10, grapplePickupPos.x, grapplePickupPos.y + 10);
        strokeWeight(1);
    }

    void updateTutorialEnemyProgress() {
        if (levelManager.getLevelNumber() != 1) return;
        if (tutorialEnemySpotted) return;
        if (enemies.length == 0) return;
        if (!hasGrapplePickup && !grappleUsed) return;

        Enemy introEnemy = enemies[0];
        float dx = player.body.position.x - introEnemy.body.position.x;
        float dy = player.body.position.y - introEnemy.body.position.y;
        if (dx * dx + dy * dy <= 200f * 200f) {
            tutorialEnemySpotted = true;
            setStatus("Enemy located. Future levels have more/faster enemies. Now head to portal.", 3.6f);
        }
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
        setStatus("Grapple attached. Press E again to disconnect (consumes it).");
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
        setStatus(msg, 1.2f);
    }

    void setStatus(String msg, float durationSeconds) {
        statusMsg = msg;
        statusTimer = durationSeconds;
    }

    void updateStatus(float dt) {
        if (tutorialEnemyHintCooldown > 0f) tutorialEnemyHintCooldown = max(0f, tutorialEnemyHintCooldown - dt);

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
            if (startGenerating) return;
            if (key == ESC) {
                key = 0;
                return;
            }

            if (!seedEditMode) {
                if (key == TAB) seedEditMode = true;
                return;
            }

            if (key == ENTER || key == RETURN) {
                seedEditMode = false;
                return;
            }

            if (keyCode == BACKSPACE || key == BACKSPACE) {
                if (!seedInputText.isEmpty()) {
                    seedInputText = seedInputText.substring(0, seedInputText.length() - 1);
                }
                refreshSeedFromInput();
                return;
            }

            if (keyCode == DELETE || key == DELETE) {
                seedInputText = "";
                return;
            }

            if (key >= 32 && key <= 126 && seedInputText.length() < 32) {
                seedInputText += key;
                refreshSeedFromInput();
            }
            return;
        }

        if (gameState == GameState.DEAD) {
            if (key == ENTER || key == RETURN) {
                resetGame();
                gameState = GameState.PLAYING;
            }
            return;
        }

        if (gameState == GameState.WON) {
            if (key == ENTER || key == RETURN) {
                if (levelManager.hasNextLevel()) {
                    levelManager.advanceLevel();
                    resetGame();
                    gameState = GameState.PLAYING;
                } else {
                    levelManager = new LevelManager(campaignSeed);
                    seedEditMode = false;
                    startGenerating = false;
                    startMenuMessage = "Campaign complete. Edit seed or generate again.";
                    startMenuMessageUntilSec = millis() / 1000.0f + 2.8f;
                    gameState = GameState.START;
                }
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
