import java.util.ArrayList;
import java.util.Random;

/**
 * Level 2 – procedural item and resource placement.
 *
 * Grapple pickup — hidden alcove
 * --------------------------------
 * Rather than floating the grapple in open air, this generator carves a small
 * alcove into an early-to-mid mesa side wall. The alcove is a tighter pocket
 * (not a long cave), and placement scales with difficulty:
 *  - low difficulty: earlier, lower, and more visible
 *  - high difficulty: later, higher, and partially concealed
 *
 *   Canyon view (player approaches from left):
 *
 *     ║ STONE ║          ← mesa body
 *     ║ STONE ║
 *     ║       ║  ← alcove entrance (2 tiles tall, open to the canyon)
 *     ║  [★]  ║  ← grapple pickup on alcove floor
 *     ╠═══════╣
 *     [ground  ]
 *
 * The player decides: "Jump into the alcove for the grapple, or skip it and
 * rely on blocks for the hard gaps ahead?"
 *
 * Resource piles
 * --------------
 * Small clusters of breakable WOOD / STONE tiles are placed alongside the
 * critical-path platforms (never on them).  Count scales inversely with
 * difficulty so hard levels still feel completable despite tighter gaps.
 */
public class ItemDistributor {

    private final int    tileSize;
    private final Random rng;

    // Published after distribute()
    public float grappleX;
    public float grappleY;

    public ItemDistributor(int tileSize, long seed) {
        this.tileSize = tileSize;
        this.rng      = new Random(seed);
    }

    /**
     * @param tiles        tile grid (modified in place)
     * @param platforms    ordered critical-path list from TerrainGenerator
     * @param config       difficulty config
     * @param cols         grid width
     * @param rows         grid height
     * @param groundTopRow row index of ground surface (from TerrainGenerator)
     */
    public void distribute(int[][] tiles, ArrayList<int[]> platforms,
                           DifficultyConfig config,
                           int cols, int rows, int groundTopRow) {
        placeGrapple(tiles, platforms, config, groundTopRow, cols, rows);
        placeResources(tiles, platforms, config, cols, rows);
    }

    // ------------------------------------------------------------------ grapple

    /**
     * Finds a suitable platform window based on difficulty, then carves a
     * compact 2–3 tile deep alcove (2 tiles tall) into a mesa side.
     * As difficulty increases, the chosen platform tends to be later, the
     * entrance is placed higher, and the opening is more concealed.
     *
     * Falls back to a floating position near the first platform if no suitable
     * mesa exists (e.g., very easy levels where all early platforms are low).
     */
    private void placeGrapple(int[][] tiles, ArrayList<int[]> platforms,
                              DifficultyConfig config,
                              int groundTopRow, int cols, int rows) {
        if (platforms.size() < 2) {
            grappleX = 6 * tileSize;
            grappleY = 4 * tileSize;
            return;
        }

        float t = (config.difficulty - 1) / 9.0f; // 0 easy -> 1 hard

        // Harder levels push the alcove farther into the route.
        int firstCandidate = 1 + Math.round(t * 3.0f); // level 1-ish: index 1, hardest: ~4
        int lastCandidate  = Math.min(platforms.size() - 2, firstCandidate + 3);
        firstCandidate = Math.min(firstCandidate, lastCandidate);

        for (int i = firstCandidate; i <= lastCandidate; i++) {
            int[] plat   = platforms.get(i);
            int platRow  = plat[1];
            int platCol  = plat[0];
            int platLen  = plat[2];
            int height   = groundTopRow - platRow; // tiles above ground

            // Height requirement increases with difficulty.
            int requiredHeight = 5 + Math.round(t * 3.0f); // 5..8
            if (height < requiredHeight || platLen < 4) continue;

            // Entrance rises with difficulty, but must stay within mesa body.
            int desiredLift = 4 + Math.round(t * 4.0f); // 4..8 tiles above ground
            int maxLiftInsideMesa = Math.max(4, height - 1);
            int lift = Math.min(desiredLift, maxLiftInsideMesa);

            int alcoveTopRow   = groundTopRow - lift;
            int alcoveFloorRow = alcoveTopRow + 2;
            if (alcoveTopRow <= platRow) continue;

            // Keep alcove compact so it reads as a hidden pocket, not a cave.
            int alcoveDepth = Math.min(3, platLen - 1);
            if (config.difficulty <= 3) alcoveDepth = Math.min(2, platLen - 1);

            if (alcoveDepth < 1) continue;

            // Easy levels favor left-face entrances (more discoverable).
            // Hard levels often use right-face entrances (less obvious while moving right).
            boolean useRightFace = config.difficulty >= 6 && rng.nextFloat() < 0.7f;
            int alcoveStartCol = useRightFace ? (platCol + platLen - alcoveDepth) : platCol;
            int entranceCol    = useRightFace ? (alcoveStartCol + alcoveDepth - 1) : alcoveStartCol;

            // Carve the alcove: rows alcoveTopRow and alcoveTopRow+1 (2 tiles tall)
            for (int r = alcoveTopRow; r < alcoveFloorRow; r++) {
                for (int c = alcoveStartCol; c < alcoveStartCol + alcoveDepth && c < cols - 1; c++) {
                    if (r >= 0 && r < rows && tiles[r][c] != TileTypes.BORDER)
                        tiles[r][c] = TileTypes.AIR;
                }
            }

            // Add a small lip before the entrance to require a better jump-in angle.
            int lipCol = useRightFace ? (entranceCol + 1) : (entranceCol - 1);
            int lipTopRow = alcoveFloorRow - 1;
            int lipBottomRow = alcoveFloorRow;
            if (lipCol >= 1 && lipCol < cols - 1) {
                if (lipTopRow >= 1 && lipTopRow < rows - 1 && tiles[lipTopRow][lipCol] == TileTypes.AIR)
                    tiles[lipTopRow][lipCol] = TileTypes.STONE;
                if (lipBottomRow >= 1 && lipBottomRow < rows - 1 && tiles[lipBottomRow][lipCol] == TileTypes.AIR)
                    tiles[lipBottomRow][lipCol] = TileTypes.STONE;
            }

            // Higher difficulties get a concealment cap above the entrance.
            if (config.difficulty >= 7) {
                int capRow = alcoveTopRow - 1;
                if (capRow >= 1 && capRow < rows - 1
                        && entranceCol >= 1 && entranceCol < cols - 1
                        && tiles[capRow][entranceCol] == TileTypes.AIR) {
                    tiles[capRow][entranceCol] = TileTypes.STONE;
                }
            }

            // Grapple sits at the inner end so the player must commit to the alcove.
            float innerCol = useRightFace ? (alcoveStartCol + 0.5f)
                                          : (alcoveStartCol + alcoveDepth - 0.5f);
            grappleX = innerCol * tileSize;
            grappleY = alcoveFloorRow * tileSize - tileSize * 0.4f; // just above floor
            return;
        }

        // Fallback: visible floating pickup near the 2nd platform
        if (platforms.size() >= 2) {
            int[] plat = platforms.get(1);
            grappleX   = (plat[0] + plat[2] * 0.5f) * tileSize;
            grappleY   = (plat[1] - 2.5f)            * tileSize;
        } else {
            grappleX = 6 * tileSize;
            grappleY = 4 * tileSize;
        }
    }

    // ------------------------------------------------------------------ resources

    /**
     * Places small clusters of breakable blocks alongside (not on) the
     * critical-path platforms so the player has nearby building materials.
     * Quantity is inversely proportional to difficulty.
     */
    private void placeResources(int[][] tiles, ArrayList<int[]> platforms,
                                DifficultyConfig config, int cols, int rows) {
        int pileCount = config.resourcePileCount;
        int pathLen   = platforms.size();
        if (pathLen == 0) return;

        int usable = Math.max(1, pathLen - 1); // exclude summit

        for (int i = 0; i < pileCount; i++) {
            float t   = (float)(i + 0.5f) / pileCount;
            int   idx = Math.min((int)(t * usable), usable - 1);
            int[] pl  = platforms.get(idx);

            // Place 2-tile cluster to the LEFT of the platform, same row
            int type     = rng.nextBoolean() ? TileTypes.WOOD : TileTypes.STONE;
            int blockRow = pl[1];
            int blockCol = pl[0] - 2 - rng.nextInt(3);

            for (int dc = 0; dc < 2; dc++) {
                int c = blockCol + dc;
                if (c >= 1 && c < cols - 1
                        && blockRow >= 1 && blockRow < rows - 1
                        && tiles[blockRow][c] == TileTypes.AIR)
                    tiles[blockRow][c] = type;
            }
        }
    }
}
