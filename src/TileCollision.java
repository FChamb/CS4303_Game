public class TileCollision {

    public static void resolvePlayerVsTiles(Player p, TileMap map) {
        p.grounded = false;

        for (int iter = 0; iter < 6; iter++) {

            int leftC   = (int)Math.floor(p.left() / map.tileSize);
            int rightC  = (int)Math.floor((p.right() - 0.001f) / map.tileSize);
            int topR    = (int)Math.floor(p.top() / map.tileSize);
            int bottomR = (int)Math.floor((p.bottom() - 0.001f) / map.tileSize);

            boolean anyResolved = false;

            for (int r = topR; r <= bottomR; r++) {
                for (int c = leftC; c <= rightC; c++) {
                    if (!map.isSolidTile(r, c)) continue;

                    float tileL = c * map.tileSize;
                    float tileR = tileL + map.tileSize;
                    float tileT = r * map.tileSize;
                    float tileB = tileT + map.tileSize;

                    float overlapX = Math.min(p.right(), tileR) - Math.max(p.left(), tileL);
                    float overlapY = Math.min(p.bottom(), tileB) - Math.max(p.top(), tileT);

                    if (overlapX > 0 && overlapY > 0) {
                        if (overlapX < overlapY) {
                            float tileCenterX = (tileL + tileR) * 0.5f;
                            if (p.body.position.x < tileCenterX) p.body.position.x -= overlapX;
                            else p.body.position.x += overlapX;

                            p.body.velocity.x = 0;
                        } else {
                            float tileCenterY = (tileT + tileB) * 0.5f;

                            if (p.body.position.y < tileCenterY) {
                                p.body.position.y -= overlapY;
                                if (p.body.velocity.y > 0) p.body.velocity.y = 0;
                                p.grounded = true;
                            } else {
                                p.body.position.y += overlapY;
                                if (p.body.velocity.y < 0) p.body.velocity.y = 0;
                            }
                        }

                        anyResolved = true;
                    }
                }
            }

            if (!anyResolved) break;
        }
    }
}