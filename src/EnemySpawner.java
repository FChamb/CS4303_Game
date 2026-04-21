import java.util.ArrayList;
import java.util.Random;

/**
 * Level 2 – procedural enemy placement.
 *
 * Enemies are distributed evenly along the critical path with deliberate
 * spacing rules that make the placement feel intentional rather than random:
 *
 *  - The first two platforms (start zone) are always enemy free so the player
 *    has time to orient before the first threat.
 *  - The summit platform (last entry in the list) is also kept clear so the
 *    player can reach the portal without a point-blank ambush.
 *  - Remaining enemies are spread uniformly across the available platforms,
 *    with a small per-enemy jitter so they don't all sit dead center.
 *  - Enemies float two-to-four tiles above their assigned platform, matching
 *    the flying-enemy design from Enemy.java.
 *
 * This generator depends on the platform list produced by TerrainGenerator,
 * demonstrating the data dependency required by the Level 4 combinator.
 */
public class EnemySpawner {

    private final int    tileSize;
    private final Random rng;

    public EnemySpawner(int tileSize, long seed) {
        this.tileSize = tileSize;
        this.rng      = new Random(seed);
    }

    /**
     * Returns spawn positions as float[enemyCount][2] where [i][0] = world-x
     * and [i][1] = world-y.
     *
     * @param platforms ordered list of int[]{col, row, length} from TerrainGenerator
     * @param config    difficulty config (provides enemyCount)
     */
    public float[][] spawn(ArrayList<int[]> platforms, DifficultyConfig config,
                           int[][] tiles, int cols, int rows) {
        int count   = config.enemyCount;
        int pathLen = platforms.size();

        // Need at least 3 platforms (start safe, at least one enemy platform, summit safe)
        int firstAvailable = Math.min(2, pathLen - 1);
        int lastAvailable  = Math.max(firstAvailable, pathLen - 2); // exclude summit
        int available      = Math.max(1, lastAvailable - firstAvailable + 1);

        float[][] spawns = new float[count][2];

        for (int i = 0; i < count; i++) {
            // Spread enemies uniformly across available section with jitter
            float t   = (float)(i + 0.5f) / count;
            int idx   = firstAvailable + (int)(t * available);
            idx       = Math.min(idx, lastAvailable);

            int[] plat = platforms.get(idx);
            int pCol   = plat[0];
            int pRow   = plat[1];
            int pLen   = plat[2];

            // Center of platform + bounded jitter (avoid extreme edges/walls).
            float centreX = (pCol + pLen * 0.5f) * tileSize;
            float jitterRange = Math.max(tileSize, pLen * tileSize * 0.28f);
            float jitterX = (rng.nextFloat() - 0.5f) * jitterRange;

            float spawnX = centreX + jitterX;
            float minX = (pCol + 1.2f) * tileSize;
            float maxX = (pCol + pLen - 1.2f) * tileSize;
            // I clamp x so enemies do not start close to island edges.
            if (minX < maxX) spawnX = clamp(spawnX, minX, maxX);

            // Float above platform, then push up until clear.
            float spawnY = (pRow - 2 - rng.nextInt(3)) * (float)tileSize;
            int adjust = 0;
            // I keep moving upward until the spawn area is clear of solid tiles.
            while (adjust < 10 && !isSpawnClear(tiles, cols, rows, spawnX, spawnY)) {
                spawnY -= tileSize;
                adjust++;
            }

            // Fallback: platform center and higher if still not clear.
            if (!isSpawnClear(tiles, cols, rows, spawnX, spawnY)) {
                spawnX = (pCol + pLen * 0.5f) * tileSize;
                spawnY = (pRow - 4) * (float)tileSize;
                adjust = 0;
                // I run one more safety pass from center to prevent block spawns.
                while (adjust < 10 && !isSpawnClear(tiles, cols, rows, spawnX, spawnY)) {
                    spawnY -= tileSize;
                    adjust++;
                }
            }

            spawns[i][0] = spawnX;
            spawns[i][1] = spawnY;
        }

        return spawns;
    }

    private boolean isSpawnClear(int[][] tiles, int cols, int rows, float wx, float wy) {
        int c = Math.max(1, Math.min(cols - 2, (int)(wx / tileSize)));
        int r = Math.max(1, Math.min(rows - 2, (int)(wy / tileSize)));

        // I require a fully clear patch so enemy bodies never clip into blocks.
        // Enemy body occupies roughly one tile radius region; keep a 3x3 clear patch.
        for (int rr = r - 1; rr <= r + 1; rr++) {
            if (rr < 1 || rr >= rows - 1) return false;
            for (int cc = c - 1; cc <= c + 1; cc++) {
                if (cc < 1 || cc >= cols - 1) return false;
                if (tiles[rr][cc] != TileTypes.AIR) return false;
            }
        }
        return true;
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
