import java.util.Random;

/**
 * Level 2 – post-processes the tile grid to create three distinct visual biomes
 * based on height, giving the world a sense of progression.
 *
 *  Lower zone  (bottom third of the climb): rocky/earthy — stone platforms get
 *              grass tops to look like grassy cliff ledges.
 *
 *  Mid zone    (middle third): bare stone and wood — left as-is from the terrain
 *              generator for a harsh, exposed mountain feel.
 *
 *  Summit zone (top third + final platform area): bright highlights — stone tops
 *              become grass to evoke a snowy/mossy summit.
 *
 * This pass runs after TerrainGenerator so it never disrupts the guaranteed
 * critical path; it only changes tile *types*, never adds or removes tiles.
 */
public class BiomeDecorator {

    private final int   cols;
    private final int   rows;
    private final Random rng;

    public BiomeDecorator(int cols, int rows, long seed) {
        this.cols = cols;
        this.rows = rows;
        this.rng  = new Random(seed);
    }

    /**
     * @param tiles        the tile grid to modify in place
     * @param groundTopRow the row index of the main ground surface
     * @param portalRow    the row index of the summit/portal platform
     */
    public void decorate(int[][] tiles, int groundTopRow, int portalRow) {
        int climbHeight = groundTopRow - portalRow;   // total rows of vertical play
        if (climbHeight <= 0) return;

        int lowerBound = groundTopRow - climbHeight / 3;   // boundary lower→mid
        int upperBound = groundTopRow - 2 * climbHeight / 3; // boundary mid→summit

        for (int r = portalRow; r < groundTopRow; r++) {
            for (int c = 1; c < cols - 1; c++) {
                int t = tiles[r][c];
                if (t == TileTypes.AIR || t == TileTypes.BORDER
                        || t == TileTypes.WOOD) continue;

                boolean isTop = r > 0 && tiles[r - 1][c] == TileTypes.AIR;

                if (r >= lowerBound) {
                    // Lower zone: grass tops give ledges an earthy, natural feel
                    if (isTop && (t == TileTypes.STONE || t == TileTypes.DIRT))
                        tiles[r][c] = TileTypes.GRASS;
                } else if (r < upperBound) {
                    // Summit zone: most exposed stone tops become grass
                    if (isTop && t == TileTypes.STONE && rng.nextFloat() < 0.6f)
                        tiles[r][c] = TileTypes.GRASS;
                }
                // Mid zone: leave as-is (bare stone/wood aesthetic)
            }
        }

        // Summit: convert any stone/dirt tops to grass for a bright peak look
        for (int c = 1; c < cols - 1; c++) {
            int t = tiles[portalRow][c];
            if (t == TileTypes.STONE || t == TileTypes.DIRT)
                tiles[portalRow][c] = TileTypes.GRASS;
        }
    }
}
