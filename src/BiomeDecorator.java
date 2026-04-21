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
    public BiomeDecorator(int cols, int rows, long seed) {
        this.cols = cols;
        this.rows = rows;
    }

    /**
     * @param tiles        the tile grid to modify in place
     * @param groundTopRow the row index of the main ground surface
     * @param portalRow    the row index of the summit/portal platform
     */
    public void decorate(int[][] tiles, int groundTopRow, int portalRow) {
        // I reset solids to stone first, so later layering is consistent everywhere.
        // Normalize non-wood solids to stone first.
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                int t = tiles[r][c];
                if (t == TileTypes.AIR || t == TileTypes.BORDER || t == TileTypes.WOOD) continue;
                tiles[r][c] = TileTypes.STONE;
            }
        }

        // Build strict stratification for surfaces open to sky:
        // top grass, two rows of dirt below, then stone.
        for (int r = 1; r < rows - 2; r++) {
            for (int c = 1; c < cols - 1; c++) {
                if (tiles[r][c] != TileTypes.STONE) continue;
                if (tiles[r - 1][c] != TileTypes.AIR) continue;
                if (!openToSky(tiles, r, c)) continue;

                // I keep this stack strict so islands do not look noisy or random.
                tiles[r][c] = TileTypes.GRASS;
                if (tiles[r + 1][c] == TileTypes.STONE) tiles[r + 1][c] = TileTypes.DIRT;
                if (tiles[r + 2][c] == TileTypes.STONE) tiles[r + 2][c] = TileTypes.DIRT;
            }
        }
    }

    private boolean openToSky(int[][] tiles, int row, int col) {
        for (int r = row - 1; r >= 1; r--) {
            int t = tiles[r][col];
            if (t != TileTypes.AIR) return false;
        }
        return true;
    }
}
