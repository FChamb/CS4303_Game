import java.util.ArrayList;
import java.util.Random;

/**
 * Level 2 – procedural enemy placement.
 *
 * Enemies are distributed evenly along the critical path with deliberate
 * spacing rules that make the placement feel intentional rather than random:
 *
 *  - The first two platforms (start zone) are always enemy-free so the player
 *    has time to orient before the first threat.
 *  - The summit platform (last entry in the list) is also kept clear so the
 *    player can reach the portal without a point-blank ambush.
 *  - Remaining enemies are spread uniformly across the available platforms,
 *    with a small per-enemy jitter so they don't all sit dead-centre.
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
    public float[][] spawn(ArrayList<int[]> platforms, DifficultyConfig config) {
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

            // Centre of platform + small horizontal jitter
            float centreX = (pCol + pLen * 0.5f) * tileSize;
            float jitterX = (rng.nextFloat() - 0.5f) * (pLen * tileSize * 0.4f);

            // Float 2–4 tiles above the platform surface
            float spawnY = (pRow - 2 - rng.nextInt(3)) * (float)tileSize;

            spawns[i][0] = centreX + jitterX;
            spawns[i][1] = spawnY;
        }

        return spawns;
    }
}
