import java.util.ArrayList;
import java.util.Random;

/**
 * Level 4 sub-generator – cave tunnel carving.
 *
 * Carves cave networks inside mesa/island bodies, giving the player alternate
 * interior routes and climb options through each section. This separates cave
 * concern from the main terrain generation and depends on the platform list
 * produced by TerrainGenerator (a Level 4 data-dependency).
 *
 * Design
 * ------
 * Cave layout is adaptive to each platform's body depth:
 *  - one primary horizontal tunnel carved through the full island width
 *  - guaranteed left/right exits to open air
 *  - guaranteed top-access shafts so entry/exit is straightforward
 *
 * This avoids dead-end decorative holes and focuses caves on useful traversal.
 *
 * Placement rules (so caves don't break playability)
 * ---------------------------------------------------
 *  - Only sufficiently large floating islands are carved.
 *  - The starting mesa and summit mesa are always excluded.
 *  - Most qualifying mesas get caves (high probability).
 *  - If random rolls produce none, one eligible mesa is forced to get a cave.
 *  - Caves never carve through the BORDER tiles.
 */
public class CaveGenerator {
    private static final int FLOAT_THRESHOLD = 8; // must be a floating island

    private final int    cols;
    private final int    rows;
    private final Random rng;

    public CaveGenerator(int cols, int rows, long seed) {
        this.cols = cols;
        this.rows = rows;
        this.rng  = new Random(seed);
    }

    /**
     * Carves cave tunnels into the tile grid in place.
     *
     * @param tiles        the tile grid to modify
     * @param platforms    ordered critical path platform list
     * @param groundTopRow ground surface row from TerrainGenerator
     */
    public void carve(int[][] tiles, ArrayList<int[]> platforms, int groundTopRow) {
        int carvedCount = 0;
        int fallbackIdx = -1;
        int bestTransitionScore = Integer.MIN_VALUE;

        // Skip index 0 (start) and last (summit)
        for (int i = 1; i < platforms.size() - 2; i++) {
            int[] plat  = platforms.get(i);
            int platCol = plat[0];
            int platRow = plat[1];
            int platLen = plat[2];
            int heightAboveGround = groundTopRow - platRow;
            int[] next = platforms.get(i + 1);

            // Only carve caves inside floating islands, never in low ground mesas.
            if (heightAboveGround <= FLOAT_THRESHOLD) continue;
            if (platLen < 8 || heightAboveGround < 8) continue;

            int mesaDepth = estimateMesaDepth(tiles, platCol, platLen, platRow);
            if (mesaDepth < 6) continue;

            int transitionScore = transitionUsefulnessScore(plat, next);
            // I only allow caves when the next step actually benefits from a cave route.
            if (transitionScore <= 0) continue; // carve only if cave gives meaningful route value.

            if (transitionScore > bestTransitionScore) {
                bestTransitionScore = transitionScore;
                fallbackIdx = i;
            }

            // Keep coverage high but not universal.
            if (rng.nextFloat() >= 0.78f) continue;
            // I carve this cave as a traversal route, not as random empty space.
            carveTunnel(tiles, plat, next, mesaDepth);
            carvedCount++;
        }

        // Guarantee at least one cave where possible.
        if (carvedCount == 0 && fallbackIdx >= 0) {
            int[] plat = platforms.get(fallbackIdx);
            int[] next = platforms.get(fallbackIdx + 1);
            int depth = estimateMesaDepth(tiles, plat[0], plat[2], plat[1]);
            // I force one useful cave so a run does not feel cave empty.
            if (depth >= 5) carveTunnel(tiles, plat, next, depth);
        }
    }

    private int estimateMesaDepth(int[][] tiles, int platCol, int platLen, int platRow) {
        int cStart = Math.max(platCol + 1, platCol + platLen / 3);
        int cEnd   = Math.min(platCol + platLen - 2, platCol + (platLen * 2) / 3);
        if (cEnd < cStart) {
            cStart = platCol + platLen / 2;
            cEnd = cStart;
        }

        int best = 0;
        for (int c = cStart; c <= cEnd && c < cols - 1; c++) {
            int depth = 0;
            for (int r = platRow + 1; r < rows - 1; r++) {
                int t = tiles[r][c];
                if (t == TileTypes.AIR || t == TileTypes.BORDER) break;
                depth++;
            }
            if (depth > best) best = depth;
        }
        return best;
    }

    private void carveTunnel(int[][] tiles, int[] plat, int[] next, int mesaDepth) {
        int platCol = plat[0];
        int platRow = plat[1];
        int platLen = plat[2];

        int caveHeight = 2;

        int minTop = platRow + 2;
        int maxTop = platRow + Math.max(2, mesaDepth - caveHeight - 2);
        if (maxTop < minTop) return;

        int preferredSpan = Math.min(2, maxTop - minTop);
        int caveTop = minTop + rng.nextInt(preferredSpan + 1);
        int caveBottom = caveTop + caveHeight - 1;

        // I run this tunnel all the way through the island so it always leads somewhere.
        // Main tunnel through the island.
        int tunnelC1 = platCol;
        int tunnelC2 = platCol + platLen - 1;
        carveRect(tiles, caveTop, caveBottom, tunnelC1, tunnelC2);

        // Explicit side exits to ensure the tunnel actually leads somewhere.
        carveRect(tiles, caveTop, caveBottom, platCol - 1, platCol);
        carveRect(tiles, caveTop, caveBottom, platCol + platLen - 1, platCol + platLen);

        // Top-access shaft aimed toward the direction of next progression.
        boolean towardRight = next[0] >= platCol;
        int shaftCol = towardRight ? (platCol + platLen - 2) : (platCol + 1);
        int shaftTop = Math.max(1, platRow);
        // I open this shaft to keep cave entry and exit simple for normal movement.
        carveRect(tiles, shaftTop, caveBottom, shaftCol, shaftCol + 1);

        // Small opposite-side shaft keeps entry simple even when approaching backwards.
        int secondaryCol = towardRight ? (platCol + 1) : (platCol + platLen - 2);
        carveRect(tiles, shaftTop + 1, caveBottom, secondaryCol, secondaryCol);
    }

    private int transitionUsefulnessScore(int[] plat, int[] next) {
        int pCol = plat[0];
        int pRow = plat[1];
        int pLen = plat[2];
        int nCol = next[0];
        int nRow = next[1];
        int nLen = next[2];

        int pLeft = pCol;
        int pRight = pCol + pLen - 1;
        int nLeft = nCol;
        int nRight = nCol + nLen - 1;

        int horizontalGap;
        if (nLeft > pRight) horizontalGap = nLeft - pRight;
        else if (pLeft > nRight) horizontalGap = pLeft - nRight;
        else horizontalGap = 0;

        int rise = pRow - nRow; // positive means next platform is higher

        int score = 0;
        if (rise >= 2) score += 2;
        if (horizontalGap >= 4) score += 2;
        if (rise >= 3) score += 1;
        if (horizontalGap >= 6) score += 1;
        return score;
    }

    private void carveRect(int[][] tiles, int r1, int r2, int c1, int c2) {
        int sr = Math.max(1, Math.min(r1, r2));
        int er = Math.min(rows - 2, Math.max(r1, r2));
        int sc = Math.max(1, Math.min(c1, c2));
        int ec = Math.min(cols - 2, Math.max(c1, c2));

        for (int r = sr; r <= er; r++) {
            for (int c = sc; c <= ec; c++) {
                if (tiles[r][c] != TileTypes.BORDER) tiles[r][c] = TileTypes.AIR;
            }
        }
    }
}
