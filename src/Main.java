import processing.core.PApplet;

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

    // sandbox controls
    int selectedBlock = 1; // later: 1=dirt,2=stone etc.
    float interactRange = 160.0f; // px (set large or remove if you want)

    public static void main(String[] args) {
        PApplet.main("Main");
    }

    public void settings() {
        size(900, 600);
    }

    public void setup() {
        world = new PhysicsWorld();

        player = new Player(width * 0.2f, height * 0.5f);
        player.body.invMass = 1.0f;
        world.addBody(player.body);

        portal = new Portal(width * 0.8f, height * 0.4f, 18);

        map = new TileMap(width, height, 30);

        // Platformer gravity
        world.forceRegistry.add(player.body, new GravityForce(0, 900));

        lastTime = millis() / 1000.0f;
        textFont(createFont("Arial", 16));
    }

    public void draw() {
        float now = millis() / 1000.0f;
        float dt = now - lastTime;
        lastTime = now;

        dt = constrain(dt, 0.0f, 1.0f / 30.0f);

        // Substeps reduce tunneling through tiles
        int subSteps = 6;
        float subDt = dt / subSteps;

        for (int i = 0; i < subSteps; i++) {
            handleInput(subDt);

            world.step(subDt);

            TileCollision.resolvePlayerVsTiles(player, map);

            applyFriction(subDt);
            clampRunSpeed();
        }

        background(18);

        map.draw(this);
        portal.draw(this);
        player.draw(this);

        drawTileCursor();

        fill(255);
        text("A/D move, SPACE (or W) jump", 20, 30);
        text("Left click = break | Right click = place", 20, 52);
        text("pos(" + nf(player.body.position.x, 0, 1) + ", " + nf(player.body.position.y, 0, 1) + ") vel(" +
                nf(player.body.velocity.x, 0, 1) + ", " + nf(player.body.velocity.y, 0, 1) + ")", 20, 74);

        if (player.isAtPortal(portal)) {
            fill(0, 220, 120);
            text("LEVEL COMPLETE ✅", width / 2f - 80, 40);
        }
    }

    void handleInput(float dt) {
        float moveForce = 3800.0f;

        player.thrust.set(0, 0);
        if (leftHeld)  player.thrust.x -= moveForce;
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

    // ---------- SANDBOX INTERACTION ----------
    public void mousePressed() {
        // Convert mouse -> tile coords (no camera yet, so screen==world)
        int tc = map.worldToTileCol(mouseX);
        int tr = map.worldToTileRow(mouseY);

        if (!map.inBounds(tr, tc)) return;

        // Optional: range limit (feels more game-like)
        float tileCenterX = (tc + 0.5f) * map.tileSize;
        float tileCenterY = (tr + 0.5f) * map.tileSize;
        float dx = tileCenterX - player.body.position.x;
        float dy = tileCenterY - player.body.position.y;
        if (dx * dx + dy * dy > interactRange * interactRange) return;

        if (mouseButton == LEFT) {
            // Break block (don’t break outside boundary tiles if you add them later)
            map.setTile(tr, tc, 0);
        } else if (mouseButton == RIGHT) {
            // Place block (only into empty)
            if (map.getTile(tr, tc) == 0) {
                // Prevent placing inside player AABB
                if (!aabbIntersectsTile(player, tr, tc, map.tileSize)) {
                    map.setTile(tr, tc, selectedBlock);
                }
            }
        }
    }

    boolean aabbIntersectsTile(Player p, int tr, int tc, int tileSize) {
        float tileL = tc * tileSize;
        float tileR = tileL + tileSize;
        float tileT = tr * tileSize;
        float tileB = tileT + tileSize;

        // AABB overlap test
        return (p.right() > tileL && p.left() < tileR && p.bottom() > tileT && p.top() < tileB);
    }

    void drawTileCursor() {
        int tc = map.worldToTileCol(mouseX);
        int tr = map.worldToTileRow(mouseY);
        if (!map.inBounds(tr, tc)) return;

        float tileCenterX = (tc + 0.5f) * map.tileSize;
        float tileCenterY = (tr + 0.5f) * map.tileSize;
        float dx = tileCenterX - player.body.position.x;
        float dy = tileCenterY - player.body.position.y;

        boolean inRange = (dx * dx + dy * dy <= interactRange * interactRange);

        noFill();
        stroke(inRange ? 255 : 120);
        strokeWeight(2);
        rectMode(CORNER);
        rect(tc * map.tileSize, tr * map.tileSize, map.tileSize, map.tileSize);
        strokeWeight(1);
    }

    // ---------- KEY INPUT ----------
    public void keyPressed() {
        if (key == 'a' || key == 'A') leftHeld = true;
        if (key == 'd' || key == 'D') rightHeld = true;

        if (key == ' ' || key == 'w' || key == 'W') {
            if (!jumpHeld) jumpPressedThisFrame = true;
            jumpHeld = true;
        }
    }

    public void keyReleased() {
        if (key == 'a' || key == 'A') leftHeld = false;
        if (key == 'd' || key == 'D') rightHeld = false;

        if (key == ' ' || key == 'w' || key == 'W') jumpHeld = false;
    }
}