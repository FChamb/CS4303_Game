/**
 * Handles collision detection and resolution between the player
 * and the solid tiles in the world.
 *
 * The player uses an AABB (axis-aligned bounding box) collision shape.
 * Each solid tile is also treated as an AABB.
 *
 * Collision response is done by:
 * - detecting overlap
 * - resolving along the axis of least penetration
 * - zeroing the relevant velocity component
 *
 * This satisfies the Level 2 collision requirement.
 */
public class TileCollision {

    /**
     * Resolves collisions between the player and all overlapping solid tiles.
     *
     * The function may perform several passes because resolving one overlap
     * can sometimes create or reveal another nearby overlap.
     */
    public static void resolvePlayerVsTiles(Player p, TileMap map) {
        // Reset grounded state each frame; it will be set to true again
        // if the player lands on top of a tile during resolution.
        p.grounded = false;

        // Multiple iterations improve stability when colliding with corners
        // or several neighbouring tiles at once.
        for (int iter = 0; iter < 6; iter++) {

            // Determine which tiles the player's AABB currently overlaps.
            int leftC   = (int)Math.floor(p.left() / map.tileSize);
            int rightC  = (int)Math.floor((p.right() - 0.001f) / map.tileSize);
            int topR    = (int)Math.floor(p.top() / map.tileSize);
            int bottomR = (int)Math.floor((p.bottom() - 0.001f) / map.tileSize);

            boolean anyResolved = false;

            // Check all tiles touched by the player's bounding box.
            for (int r = topR; r <= bottomR; r++) {
                for (int c = leftC; c <= rightC; c++) {
                    if (!map.isSolidTile(r, c)) continue;

                    // Tile bounds in world coordinates.
                    float tileL = c * map.tileSize;
                    float tileR = tileL + map.tileSize;
                    float tileT = r * map.tileSize;
                    float tileB = tileT + map.tileSize;

                    // Compute overlap on each axis.
                    float overlapX = Math.min(p.right(), tileR) - Math.max(p.left(), tileL);
                    float overlapY = Math.min(p.bottom(), tileB) - Math.max(p.top(), tileT);

                    // If both overlaps are positive, the AABBs intersect.
                    if (overlapX > 0 && overlapY > 0) {
                        // Resolve along the axis of least penetration.
                        // This generally produces more stable platformer behaviour.
                        if (overlapX < overlapY) {
                            float tileCenterX = (tileL + tileR) * 0.5f;

                            // Push player left or right out of the tile.
                            if (p.body.position.x < tileCenterX) p.body.position.x -= overlapX;
                            else p.body.position.x += overlapX;

                            // Stop horizontal movement into the wall.
                            p.body.velocity.x = 0;
                        } else {
                            float tileCenterY = (tileT + tileB) * 0.5f;

                            if (p.body.position.y < tileCenterY) {
                                // Player is above tile: treat as landing.
                                p.body.position.y -= overlapY;
                                if (p.body.velocity.y > 0) p.body.velocity.y = 0;
                                p.grounded = true;
                            } else {
                                // Player is below tile: treat as hitting head.
                                p.body.position.y += overlapY;
                                if (p.body.velocity.y < 0) p.body.velocity.y = 0;
                            }
                        }

                        anyResolved = true;
                    }
                }
            }

            // Stop early if no overlaps were found this iteration.
            if (!anyResolved) break;
        }
    }
}