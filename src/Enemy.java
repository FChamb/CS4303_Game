import processing.core.PApplet;
import java.util.ArrayList;

/**
 * Flying enemy AI used to demonstrate the four AI stages for the practical.
 *
 * The behavior was built up progressively:
 * - Level 1: basic steering behaviors such as wandering and pursuit
 * - Level 2: compound steering through obstacle avoidance and separation
 * - Level 3: decision making through a finite state machine
 * - Level 4: A* pathfinding on the tile grid, including conversion between
 *   world coordinates and discrete pathfinding nodes
 *
 * I chose flying enemies because they fit the mountain setting and let me focus
 * on AI behavior and pathfinding without also needing grounded navigation.
 */
public class Enemy {
    public final Body body;

    // Visual size
    public float radius = 14;

    // Speeds
    public float wanderSpeed = 85.0f;
    public float chaseSpeedMin = 145.0f;
    public float chaseSpeedMax = 235.0f;
    public float currentMaxSpeed = wanderSpeed;

    public float maxForce = 950.0f;

    // Detection / behaviour ranges
    public float detectionRange = 280.0f;
    public float separationRange = 50.0f;

    // Wander behaviour
    private final Vec2 wanderTarget = new Vec2();
    private float wanderTimer = 0f;
    private float wanderInterval = 1.1f;

    // ---------- LEVEL 3 FSM ----------
    public enum AIState {
        WANDER,
        CHASE
    }

    public AIState state = AIState.WANDER;

    // Internal state: remain alert briefly after losing player
    private float alertTimer = 0f;
    private float alertDuration = 2.0f;

    // ---------- LEVEL 4 PATHFINDING ----------
    private static final PathFinder PATH_FINDER = new PathFinder();

    private ArrayList<int[]> currentPath = new ArrayList<>();
    private int pathIndex = 0;

    private float repathTimer = 0f;
    private float repathInterval = 0.45f;

    private int lastGoalR = -9999;
    private int lastGoalC = -9999;

    public Enemy(float x, float y) {
        body = new Body(x, y);
        body.invMass = 1.0f;
        pickNewWanderTarget(x, y);
    }

    /**
     * Updates the enemy AI.
     */
    public void update(float dt, Player player, TileMap map, Enemy[] allEnemies) {
        updateState(dt, player);

        float steerX = 0f;
        float steerY = 0f;

        switch (state) {
            case WANDER -> {
                currentMaxSpeed = wanderSpeed;
                float[] w = wander(dt, map);
                steerX += w[0] * 0.9f;
                steerY += w[1] * 0.9f;
                currentPath.clear();
                pathIndex = 0;
            }

            case CHASE -> {
                // Make the enemies feel more heated as they get closer
                float dist = distanceTo(player.body.position.x, player.body.position.y);
                float t = 1.0f - clamp(dist / detectionRange, 0f, 1f);
                currentMaxSpeed = lerp(chaseSpeedMin, chaseSpeedMax, t);

                float[] chase = chaseWithPathfinding(dt, player, map);
                steerX += chase[0] * 1.0f;
                steerY += chase[1] * 1.0f;
            }
        }

        // Level 2 compound behaviours layered on top
        float[] avoid = avoidObstacles(map);
        steerX += avoid[0] * 1.35f;
        steerY += avoid[1] * 1.35f;

        float[] separate = separate(allEnemies);
        steerX += separate[0] * 1.15f;
        steerY += separate[1] * 1.15f;

        float[] clamped = clampMagnitude(steerX, steerY, maxForce);
        body.addForce(new Vec2(clamped[0], clamped[1]));

        clampSpeed();
        keepInsideWorld(map);
    }

    /**
     * Level 3 decision-making.
     *
     * Environmental condition:
     * - distance to the player
     *
     * Internal state:
     * - alert timer keeps enemy in chase briefly after losing the player
     */
    private void updateState(float dt, Player player) {
        float dx = player.body.position.x - body.position.x;
        float dy = player.body.position.y - body.position.y;
        float distSq = dx * dx + dy * dy;

        boolean playerDetected = distSq <= detectionRange * detectionRange;

        if (playerDetected) {
            alertTimer = alertDuration;
        } else {
            alertTimer = Math.max(0f, alertTimer - dt);
        }

        if (playerDetected || alertTimer > 0f) {
            state = AIState.CHASE;
        } else {
            state = AIState.WANDER;
        }
    }

    /**
     * Level 4 pathfinding:
     * - quantize enemy/player world positions to tile coordinates
     * - run A* in air space
     * - localize the next tile waypoint back into world coordinates
     */
    private float[] chaseWithPathfinding(float dt, Player player, TileMap map) {
        int startR = map.worldToTileRow(body.position.y);
        int startC = map.worldToTileCol(body.position.x);

        int goalR = map.worldToTileRow(player.body.position.y);
        int goalC = map.worldToTileCol(player.body.position.x);

        repathTimer -= dt;

        boolean needRepath = currentPath.isEmpty()
                || repathTimer <= 0f
                || goalR != lastGoalR
                || goalC != lastGoalC;

        if (needRepath) {
            repathTimer = repathInterval;
            lastGoalR = goalR;
            lastGoalC = goalC;

            currentPath = PATH_FINDER.findPath(map, startR, startC, goalR, goalC, 18000);
            pathIndex = Math.min(1, Math.max(0, currentPath.size() - 1));
        }

        // If a path exists, follow the next waypoint.
        if (!currentPath.isEmpty() && pathIndex < currentPath.size()) {
            int[] waypoint = currentPath.get(pathIndex);

            float wx = tileCenterX(map, waypoint[1]);
            float wy = tileCenterY(map, waypoint[0]);

            // Advance to the next waypoint if close enough
            float dx = wx - body.position.x;
            float dy = wy - body.position.y;
            if (dx * dx + dy * dy < 18 * 18 && pathIndex < currentPath.size() - 1) {
                pathIndex++;
                waypoint = currentPath.get(pathIndex);
                wx = tileCenterX(map, waypoint[1]);
                wy = tileCenterY(map, waypoint[0]);
            }

            return seekPoint(wx, wy);
        }

        // Fallback if no path is found: direct pursuit
        return pursue(player);
    }

    /**
     * Steering behaviour: pursuit.
     */
    private float[] pursue(Player player) {
        float toTargetX = player.body.position.x - body.position.x;
        float toTargetY = player.body.position.y - body.position.y;

        float distance = (float)Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        float predictionTime = Math.min(0.8f, distance / Math.max(60.0f, currentMaxSpeed));

        float targetX = player.body.position.x + player.body.velocity.x * predictionTime;
        float targetY = player.body.position.y + player.body.velocity.y * predictionTime;

        return seekPoint(targetX, targetY);
    }

    /**
     * Steering behaviour: wander.
     */
    private float[] wander(float dt, TileMap map) {
        wanderTimer -= dt;

        if (wanderTimer <= 0f) {
            wanderTimer = wanderInterval;
            pickNewWanderTarget(body.position.x, body.position.y);

            wanderTarget.x = clamp(wanderTarget.x, 50, map.getWorldWidth() - 50);
            wanderTarget.y = clamp(wanderTarget.y, 50, map.getWorldHeight() - 100);
        }

        return seekPoint(wanderTarget.x, wanderTarget.y);
    }

    /**
     * Steering behaviour: separation.
     */
    private float[] separate(Enemy[] allEnemies) {
        float steerX = 0f;
        float steerY = 0f;
        int count = 0;

        for (Enemy other : allEnemies) {
            if (other == null || other == this) continue;

            float dx = body.position.x - other.body.position.x;
            float dy = body.position.y - other.body.position.y;
            float distSq = dx * dx + dy * dy;

            if (distSq > 0.0001f && distSq < separationRange * separationRange) {
                float dist = (float)Math.sqrt(distSq);
                float strength = (separationRange - dist) / separationRange;

                steerX += (dx / dist) * strength * maxForce;
                steerY += (dy / dist) * strength * maxForce;
                count++;
            }
        }

        if (count > 0) {
            steerX /= count;
            steerY /= count;
        }

        return clampMagnitude(steerX, steerY, maxForce);
    }

    /**
     * Steering behaviour: obstacle avoidance.
     */
    private float[] avoidObstacles(TileMap map) {
        float speedSq = body.velocity.x * body.velocity.x + body.velocity.y * body.velocity.y;

        float dirX;
        float dirY;

        if (speedSq < 1f) {
            dirX = 1f;
            dirY = 0f;
        } else {
            float speed = (float)Math.sqrt(speedSq);
            dirX = body.velocity.x / speed;
            dirY = body.velocity.y / speed;
        }

        float lookAhead = 38.0f;
        float sideOffset = 18.0f;

        float aheadX = body.position.x + dirX * lookAhead;
        float aheadY = body.position.y + dirY * lookAhead;

        float perpX = -dirY;
        float perpY = dirX;

        boolean hitCenter = isSolidAtWorld(map, aheadX, aheadY);
        boolean hitLeft = isSolidAtWorld(map, aheadX + perpX * sideOffset, aheadY + perpY * sideOffset);
        boolean hitRight = isSolidAtWorld(map, aheadX - perpX * sideOffset, aheadY - perpY * sideOffset);

        float steerX = 0f;
        float steerY = 0f;

        if (hitCenter || hitLeft || hitRight) {
            steerX -= dirX * maxForce;
            steerY -= dirY * maxForce;

            if (hitLeft && !hitRight) {
                steerX -= perpX * maxForce * 0.8f;
                steerY -= perpY * maxForce * 0.8f;
            } else if (hitRight && !hitLeft) {
                steerX += perpX * maxForce * 0.8f;
                steerY += perpY * maxForce * 0.8f;
            } else {
                steerY -= maxForce * 0.45f;
            }
        }

        return clampMagnitude(steerX, steerY, maxForce);
    }

    /**
     * Basic seek steering to a target point.
     */
    private float[] seekPoint(float targetX, float targetY) {
        float dx = targetX - body.position.x;
        float dy = targetY - body.position.y;

        float distSq = dx * dx + dy * dy;
        if (distSq < 0.0001f) return new float[]{0f, 0f};

        float dist = (float)Math.sqrt(distSq);
        float dirX = dx / dist;
        float dirY = dy / dist;

        float desiredVX = dirX * currentMaxSpeed;
        float desiredVY = dirY * currentMaxSpeed;

        float steerX = desiredVX - body.velocity.x;
        float steerY = desiredVY - body.velocity.y;

        return clampMagnitude(steerX, steerY, maxForce);
    }

    private void pickNewWanderTarget(float cx, float cy) {
        float angle = (float)(Math.random() * Math.PI * 2.0);
        float distance = 70.0f + (float)(Math.random() * 110.0);

        wanderTarget.x = cx + (float)Math.cos(angle) * distance;
        wanderTarget.y = cy + (float)Math.sin(angle) * distance;
    }

    private void clampSpeed() {
        float speedSq = body.velocity.x * body.velocity.x + body.velocity.y * body.velocity.y;
        if (speedSq > currentMaxSpeed * currentMaxSpeed) {
            float speed = (float)Math.sqrt(speedSq);
            body.velocity.x = (body.velocity.x / speed) * currentMaxSpeed;
            body.velocity.y = (body.velocity.y / speed) * currentMaxSpeed;
        }
    }

    private void keepInsideWorld(TileMap map) {
        float margin = 55.0f;
        float steerX = 0;
        float steerY = 0;

        if (body.position.x < margin) steerX += maxForce * 0.5f;
        if (body.position.x > map.getWorldWidth() - margin) steerX -= maxForce * 0.5f;
        if (body.position.y < margin) steerY += maxForce * 0.5f;
        if (body.position.y > map.getWorldHeight() - margin) steerY -= maxForce * 0.5f;

        if (steerX != 0 || steerY != 0) {
            body.addForce(new Vec2(steerX, steerY));
        }
    }

    public void draw(PApplet p) {
        p.noStroke();

        switch (state) {
            case WANDER -> p.fill(220, 140, 70);
            case CHASE -> p.fill(220, 70, 70);
        }

        p.circle(body.position.x, body.position.y, radius * 2);

        p.fill(255);
        p.circle(body.position.x + 4, body.position.y - 2, 5);
    }

    private boolean isSolidAtWorld(TileMap map, float x, float y) {
        int c = map.worldToTileCol(x);
        int r = map.worldToTileRow(y);
        return map.isSolidTile(r, c);
    }

    private float distanceTo(float x, float y) {
        float dx = x - body.position.x;
        float dy = y - body.position.y;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    private float tileCenterX(TileMap map, int c) {
        return (c + 0.5f) * map.tileSize;
    }

    private float tileCenterY(TileMap map, int r) {
        return (r + 0.5f) * map.tileSize;
    }

    private float[] clampMagnitude(float x, float y, float maxMag) {
        float magSq = x * x + y * y;
        if (magSq <= maxMag * maxMag) return new float[]{x, y};

        float mag = (float)Math.sqrt(magSq);
        return new float[]{(x / mag) * maxMag, (y / mag) * maxMag};
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}