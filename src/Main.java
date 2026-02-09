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
        world.forceRegistry.add(player.body, new GravityForce(0, 900)); // tune gravity here

        lastTime = millis() / 1000.0f;
        textFont(createFont("Arial", 16));
    }

    public void draw() {
        float now = millis() / 1000.0f;
        float dt = now - lastTime;
        lastTime = now;

        // clamp dt for stability
        dt = constrain(dt, 0.0f, 1.0f / 30.0f);

        // Substeps to reduce tunneling through tiles
        int subSteps = 6;                 // increase if still tunneling
        float subDt = dt / subSteps;

        // We apply input as forces each substep (same held keys)
        for (int i = 0; i < subSteps; i++) {
            handleInput(subDt);

            world.step(subDt);

            // Resolve AABB vs tiles each substep
            TileCollision.resolvePlayerVsTiles(player, map);

            // Apply ground friction AFTER collision resolution (needs grounded info)
            applyFriction(subDt);

            // Clamp run speed (prevents crazy velocity tunneling)
            clampRunSpeed();
        }

        background(18);

        map.draw(this);
        portal.draw(this);
        player.draw(this);

        fill(255);
        text("A/D move, SPACE (or W) jump. Reach the portal.", 20, 30);
        text("pos(" + nf(player.body.position.x, 0, 1) + ", " + nf(player.body.position.y, 0, 1) + ") vel(" +
                nf(player.body.velocity.x, 0, 1) + ", " + nf(player.body.velocity.y, 0, 1) + ")", 20, 55);
        text("grounded: " + player.grounded, 20, 80);

        if (player.isAtPortal(portal)) {
            fill(0, 220, 120);
            text("LEVEL COMPLETE ✅", width / 2f - 80, 40);
        }
    }

    void handleInput(float dt) {
        // Horizontal movement as force
        float moveForce = 3800.0f;

        player.thrust.set(0, 0);

        if (leftHeld)  player.thrust.x -= moveForce;
        if (rightHeld) player.thrust.x += moveForce;

        player.body.addForce(player.thrust);

        // Jump: trigger once per key press, only if grounded
        if (jumpPressedThisFrame && player.grounded) {
            player.body.velocity.y = -430; // jump strength
            player.grounded = false;
        }
        jumpPressedThisFrame = false;
    }

    void applyFriction(float dt) {
        // Arcade friction: only applies when grounded.
        // Strong friction when no input -> stops quickly.
        // Light friction when moving -> still controlled.
        if (!player.grounded) return;

        boolean movingInput = leftHeld || rightHeld;

        float frictionPerFrame = movingInput ? 0.90f : 0.65f; // lower = stronger friction
        float factor = (float) Math.pow(frictionPerFrame, dt * 60.0f);

        player.body.velocity.x *= factor;

        // deadzone to fully stop
        if (!movingInput && Math.abs(player.body.velocity.x) < 5.0f) {
            player.body.velocity.x = 0;
        }
    }

    void clampRunSpeed() {
        float maxRunSpeed = 220.0f; // px/s
        if (player.body.velocity.x > maxRunSpeed) player.body.velocity.x = maxRunSpeed;
        if (player.body.velocity.x < -maxRunSpeed) player.body.velocity.x = -maxRunSpeed;
    }

    // --- Key handling (held + one-frame press for jump) ---
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