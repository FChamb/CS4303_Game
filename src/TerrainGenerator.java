import java.util.ArrayList;
import java.util.Random;

/**
 * Level 1 – constraint-based terrain generator.
 *
 * Mesa/canyon design — guaranteed playability
 * -------------------------------------------
 * Every platform is a flat surface anchored to a mesa body.  Whether that body
 * reaches the ground depends on the platform's height above the ground layer:
 *
 *   Low  (≤ 8 tiles above ground):  fill all the way down → grounded canyon.
 *   High (> 8 tiles above ground):  fill only 6 tiles down → floating island.
 *
 * This creates a natural difficulty progression: early levels feel like canyons
 * (you fall in, you climb out), while later levels float free over open ground
 * (fall off and land on the clear flat floor, then walk back to the start).
 *
 * Visual variety
 * --------------
 * To avoid monotonous flat rectangles every platform randomly gains one or more
 * embellishments:
 *   - Surface rocks:  1–2-tile stone bumps on top (navigational obstacle).
 *   - Step ledge:     a 1-tile elevated section on one end.
 *   - Stalactites:    hanging stone spikes from the bottom of floating mesas.
 *
 * Playability guarantee
 * ---------------------
 *   horizontal gap  ≤ 7 tiles  (speed 220 × air-time 0.96 s ≈ 211 px / 30)
 *   vertical rise   ≤ 3 tiles  (v₀²/2g = 430²/1800 ≈ 103 px / 30)
 *
 * Gaps ≥ 6 tiles (high difficulty) require block-building or the grapple hook;
 * resourcePileCount in DifficultyConfig scales inversely to compensate.
 */
public class TerrainGenerator {

    // Heights at which the mesa becomes a floating island rather than a grounded cliff
    private static final int FLOAT_THRESHOLD = 8;  // tiles above ground
    private static final int FLOAT_FILL_DEPTH = 6; // tiles of body below a floating mesa

    private final int cols;
    private final int rows;
    private final int tileSize;
    private final DifficultyConfig config;
    private final Random rng;

    // Published after generate()
    public int   groundTopRow;
    public int   portalRow;
    public int   portalCol;
    public float upperGoalY;

    public TerrainGenerator(int cols, int rows, int tileSize,
                            DifficultyConfig config, long seed) {
        this.cols     = cols;
        this.rows     = rows;
        this.tileSize = tileSize;
        this.config   = config;
        this.rng      = new Random(seed);
    }

    // ============================================================ public API

    public ArrayList<int[]> generate(int[][] tiles) {
        ArrayList<int[]> platforms = new ArrayList<>();

        fillAir(tiles);
        placeBorder(tiles);
        placeGround(tiles);
        buildCriticalPath(tiles, platforms);
        removeBuriedGroundGrass(tiles);

        upperGoalY = portalRow * tileSize - 14.0f;
        return platforms;
    }

    // ============================================================ setup

    private void fillAir(int[][] tiles) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                tiles[r][c] = TileTypes.AIR;
    }

    private void placeBorder(int[][] tiles) {
        for (int c = 0; c < cols; c++) {
            tiles[0][c]        = TileTypes.BORDER;
            tiles[rows - 1][c] = TileTypes.BORDER;
        }
        for (int r = 0; r < rows; r++) {
            tiles[r][0]        = TileTypes.BORDER;
            tiles[r][cols - 1] = TileTypes.BORDER;
        }
    }

    private void placeGround(int[][] tiles) {
        groundTopRow = rows - 8;
        for (int c = 1; c < cols - 1; c++) {
            tiles[groundTopRow][c]     = TileTypes.GRASS;
            tiles[groundTopRow + 1][c] = TileTypes.DIRT;
            tiles[groundTopRow + 2][c] = TileTypes.DIRT;
            for (int r = groundTopRow + 3; r < rows - 1; r++)
                tiles[r][c] = TileTypes.STONE;
        }
    }

    /**
     * Prevents grass from appearing under mountain bodies.
     * If a ground-row grass tile is covered by solid terrain above, it becomes dirt.
     */
    private void removeBuriedGroundGrass(int[][] tiles) {
        int r = groundTopRow;
        for (int c = 1; c < cols - 1; c++) {
            if (tiles[r][c] == TileTypes.GRASS && tiles[r - 1][c] != TileTypes.AIR) {
                tiles[r][c] = TileTypes.DIRT;
            }
        }
    }

    // ============================================================ critical path

    private void buildCriticalPath(int[][] tiles, ArrayList<int[]> platforms) {
        int currentRow = groundTopRow - 3;

        // ---- wide starting mesa — safe landing zone ----
        int startCol = 3;
        int startEnd = startCol + 7;
        placeSurface(tiles, startCol, startEnd, currentRow, TileTypes.GRASS);
        fillMesaBelow(tiles, startCol, startEnd, currentRow);
        platforms.add(new int[]{startCol, currentRow, startEnd - startCol + 1});

        int currentCol = startEnd + 3 + rng.nextInt(2);
        int section    = 0;

        while (currentCol < cols - 30 && currentRow > 15) {
            int len  = rng.nextInt(config.platformLenMax - config.platformLenMin + 1)
                       + config.platformLenMin;
            int gap  = rng.nextInt(config.gapMax - config.gapMin + 1)  + config.gapMin;
            int rise = rng.nextInt(config.riseMax - config.riseMin + 1) + config.riseMin;

            rise = Math.min(rise, currentRow - 16);
            if (rise < 0) rise = 0;

            int endCol = Math.min(currentCol + len - 1, cols - 2);
            int type   = platformMaterial(section);

            // ---- platform surface ----
            placeSurface(tiles, currentCol, endCol, currentRow, type);

            // ---- mesa body (grounded or floating depending on height) ----
            fillMesaBelow(tiles, currentCol, endCol, currentRow);

            // ---- visual embellishments ----
            maybeAddSurfaceRock(tiles, currentCol, endCol, currentRow, len);
            maybeAddStepLedge(tiles, currentCol, endCol, currentRow, type);

            platforms.add(new int[]{currentCol, currentRow, endCol - currentCol + 1});

            currentCol += len + gap;
            currentRow -= rise;
            section++;
        }

        // ---- summit mesa ----
        portalRow = Math.max(15, currentRow);
        portalCol = Math.min(currentCol + 2, cols - 14);
        int summitEnd = Math.min(portalCol + 9, cols - 2);

        placeSurface(tiles, portalCol, summitEnd, portalRow, TileTypes.WOOD);
        fillMesaBelow(tiles, portalCol, summitEnd, portalRow);
        platforms.add(new int[]{portalCol, portalRow, summitEnd - portalCol + 1});
    }

    // ============================================================ mesa body

    /**
     * Fills solid terrain below a platform surface.
     *
     * If the platform is close to the ground (≤ FLOAT_THRESHOLD tiles up) the
     * fill reaches all the way to the ground layer — classic grounded canyon.
     * If the platform is higher, only FLOAT_FILL_DEPTH rows are filled and the
     * rest is open air — a floating island.  A player who falls off a floating
     * island lands on the flat ground below and can walk back to the start.
     */
    private void fillMesaBelow(int[][] tiles, int c1, int c2, int platformRow) {
        int heightAboveGround = groundTopRow - platformRow;
        int fromRow = platformRow + 1;
        int toRow;

        if (heightAboveGround <= FLOAT_THRESHOLD) {
            toRow = groundTopRow - 1;                           // grounded
        } else {
            toRow = Math.min(fromRow + FLOAT_FILL_DEPTH - 1, groundTopRow - 1); // floating
        }

        for (int r = fromRow; r <= toRow; r++) {
            int type = (r - fromRow < 2) ? TileTypes.DIRT : TileTypes.STONE;
            for (int c = Math.max(1, c1); c <= Math.min(cols - 2, c2); c++) {
                if (tiles[r][c] == TileTypes.AIR)
                    tiles[r][c] = type;
            }
        }

        // Hanging stalactites on floating mesas add visual depth
        if (heightAboveGround > FLOAT_THRESHOLD && rng.nextFloat() < 0.65f) {
            addStalactites(tiles, c1, c2, toRow);
        }
    }

    /** Hangs stone spikes from the bottom of a floating mesa body. */
    private void addStalactites(int[][] tiles, int c1, int c2, int mesaBottom) {
        int len = c2 - c1 + 1;
        if (len < 2) return;
        int numSpikes = 1 + rng.nextInt(Math.min(3, len / 2 + 1));
        for (int i = 0; i < numSpikes; i++) {
            int spikeCol  = c1 + rng.nextInt(len);
            int spikeLen  = 1 + rng.nextInt(3);
            for (int k = 1; k <= spikeLen; k++) {
                int r = mesaBottom + k;
                if (r > 0 && r < rows - 1 && tiles[r][spikeCol] == TileTypes.AIR)
                    tiles[r][spikeCol] = TileTypes.STONE;
            }
        }
    }

    // ============================================================ surface embellishments

    /**
     * 25 % chance: places a 1–2-tile stone rock on top of the platform surface.
     * The player must jump over it; on narrow platforms this is a real obstacle.
     */
    private void maybeAddSurfaceRock(int[][] tiles, int c1, int c2,
                                     int platformRow, int len) {
        if (len < 4 || rng.nextFloat() >= 0.25f) return;
        int rockCol    = c1 + 1 + rng.nextInt(len - 2);
        int rockHeight = 1 + rng.nextInt(2);
        for (int h = 1; h <= rockHeight; h++) {
            int rr = platformRow - h;
            if (rr > 1 && tiles[rr][rockCol] == TileTypes.AIR)
                tiles[rr][rockCol] = TileTypes.STONE;
        }
    }

    /**
     * 30 % chance: raises one end of the platform by 1 tile, creating a step.
     * The player can use it as a launching ramp or must jump to clear it.
     */
    private void maybeAddStepLedge(int[][] tiles, int c1, int c2,
                                   int platformRow, int surfaceType) {
        int len = c2 - c1 + 1;
        if (len < 5 || rng.nextFloat() >= 0.30f) return;

        int stepWidth = 1 + rng.nextInt(Math.max(1, len / 3));
        boolean stepLeft = rng.nextBoolean();
        int stepC1 = stepLeft ? c1 : (c2 - stepWidth + 1);
        int stepC2 = stepLeft ? (c1 + stepWidth - 1) : c2;
        int stepRow = platformRow - 1;

        if (stepRow <= 1) return;
        placeSurface(tiles, stepC1, stepC2, stepRow, surfaceType);
        // Solid support directly below the step (already covered by main surface)
    }

    // ============================================================ helpers

    private void placeSurface(int[][] tiles, int c1, int c2, int row, int type) {
        if (row < 1 || row >= rows - 1) return;
        for (int c = Math.max(1, c1); c <= Math.min(cols - 2, c2); c++)
            tiles[row][c] = type;
    }

    private int platformMaterial(int section) {
        switch (section % 4) {
            case 0: return TileTypes.STONE;
            case 1: return TileTypes.DIRT;
            case 2: return TileTypes.STONE;
            case 3: return TileTypes.WOOD;
            default: return TileTypes.STONE;
        }
    }
}
