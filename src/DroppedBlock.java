import processing.core.PApplet;

/**
 * Represents a dropped block item in the world.
 *
 * I added this so block collection is more balanced:
 * when a block is broken it drops into the world, falls onto the terrain,
 * and can only be picked up if the player moves over it and has inventory space.
 *
 * This makes building resources feel more limited and deliberate.
 */
public class DroppedBlock {
    public int blockType;

    public float x;
    public float y;

    public float vy = 0f;

    public float size = 14f;

    // Small pickup delay so the item does not get instantly re-collected
    public float pickupDelay = 0.15f;

    // Bobbing animation
    private float bobTime;
    private float bobAmount = 2.5f;

    public DroppedBlock(int blockType, float x, float y) {
        this.blockType = blockType;
        this.x = x;
        this.y = y;

        // Give each drop a slightly different phase so they don't all bob identically
        this.bobTime = (float)(Math.random() * Math.PI * 2.0);
    }

    /**
     * Update simple item physics.
     */
    public void update(float dt, TileMap map) {
        bobTime += dt * 2.5f;

        if (pickupDelay > 0f) {
            pickupDelay -= dt;
            if (pickupDelay < 0f) pickupDelay = 0f;
        }

        // Apply simple falling
        vy += 900f * dt;
        if (vy > 350f) vy = 350f;

        float newY = y + vy * dt;

        // Check for ground collision under the item's bottom
        float half = size * 0.5f;
        float bottomY = newY + half;

        int leftCol = map.worldToTileCol(x - half + 1);
        int rightCol = map.worldToTileCol(x + half - 1);
        int bottomRow = map.worldToTileRow(bottomY);

        boolean hitGround = false;
        for (int c = leftCol; c <= rightCol; c++) {
            if (map.isSolidTile(bottomRow, c)) {
                hitGround = true;
                break;
            }
        }

        if (hitGround) {
            y = bottomRow * map.tileSize - half;
            vy = 0f;
        } else {
            y = newY;
        }
    }

    /**
     * Returns true if the player is close enough to pick up the item.
     */
    public boolean canBePickedUpBy(Player player) {
        if (pickupDelay > 0f) return false;

        float dx = player.body.position.x - x;
        float dy = player.body.position.y - y;
        float pickupRange = 22f;

        return dx * dx + dy * dy <= pickupRange * pickupRange;
    }

    /**
     * Draw the dropped block with a small bobbing effect.
     */
    public void draw(PApplet p) {
        int rgb = TileTypes.color(blockType);
        float drawY = y + (float)Math.sin(bobTime) * bobAmount;

        p.noStroke();
        p.fill((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        p.rectMode(PApplet.CENTER);
        p.rect(x, drawY, size, size, 4);

        // Small outline so it reads as an item
        p.noFill();
        p.stroke(255, 180);
        p.rect(x, drawY, size, size, 4);
        p.noStroke();
    }
}