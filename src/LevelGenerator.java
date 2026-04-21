import java.util.ArrayList;

/**
 * Level 4 – Generator Combinator.
 *
 * Coordinates five distinct sub-generators so each handles one concern while
 * respecting the outputs of the generators that ran before it:
 *
 *  1. TerrainGenerator   – fills the tile grid with a constraint-based critical
 *                          path and background rocky detail (Level 1).
 *
 *  2. BiomeDecorator     – post-processes tile types by height zone to produce
 *                          three visually distinct biomes (Level 2).
 *
 *  3. CaveGenerator      – carves alternate tunnels through mesa bodies so
 *                          players can choose safer paths (Level 2/4).
 *
 *  4. EnemySpawner       – reads the platform list from TerrainGenerator to
 *                          place enemies with appropriate spacing (Level 2).
 *
 *  5. ItemDistributor    – reads both the platform list and the tile grid to
 *                          place the grapple pickup and resource piles at
 *                          locations that enhance gameplay (Level 2).
 *
 * Difficulty-aware generators receive a DifficultyConfig derived from the
 * difficulty parameter so the core challenge scales together (Level 3).
 *
 * After generate() returns, all spawn-position metadata is available as public
 * fields so Main and TileMap can read them without keeping the sub-generators
 * alive.
 */
public class LevelGenerator {

    private final int            cols;
    private final int            rows;
    private final int            tileSize;
    private final DifficultyConfig config;
    private final long           seed;

    // ---- metadata published after generate() ----
    public float   playerSpawnX;
    public float   playerSpawnY;
    public float   portalX;
    public float   portalY;
    public float   grappleX;
    public float   grappleY;
    public float[][] enemySpawns;     // [i][0]=x, [i][1]=y
    public float   enemySpeedScale;
    public int     groundTopRow;
    public float   upperGoalY;
    public int     difficulty;

    public LevelGenerator(int worldW, int worldH, int tileSize,
                          int difficulty, long seed) {
        this.tileSize = tileSize;
        this.cols     = (int)Math.ceil(worldW / (float)tileSize);
        this.rows     = (int)Math.ceil(worldH / (float)tileSize);
        this.config   = new DifficultyConfig(difficulty);
        this.seed     = seed;
        this.difficulty = difficulty;
    }

    /**
     * Runs all sub-generators in dependency order and returns the populated
     * tile grid.  All metadata fields are set before this method returns.
     */
    public int[][] generate() {
        int[][] tiles = new int[rows][cols];

        // ---- Sub-generator 1: Terrain (Level 1) ----
        TerrainGenerator terrain = new TerrainGenerator(
                cols, rows, tileSize, config, seed);
        ArrayList<int[]> platforms = terrain.generate(tiles);
        groundTopRow = terrain.groundTopRow;
        upperGoalY   = terrain.upperGoalY;

        // ---- Sub-generator 2: Biome decoration (Level 2) ----
        // Depends on: tile grid, groundTopRow, portalRow from terrain
        BiomeDecorator biomes = new BiomeDecorator(cols, rows, seed + 1L);
        biomes.decorate(tiles, terrain.groundTopRow, terrain.portalRow);

        // ---- Sub-generator 3: Cave routing (Level 2 / Level 4 dependency layering) ----
        // Runs after biome tinting so cave interiors keep rock colors, not grass tops.
        CaveGenerator caves = new CaveGenerator(cols, rows, seed + 2L);
        caves.carve(tiles, platforms, terrain.groundTopRow);

        // ---- Sub-generator 4: Enemy placement (Level 2) ----
        // Depends on: platform list (layout must exist before spacing can be calculated)
        EnemySpawner enemies = new EnemySpawner(tileSize, seed + 3L);
        enemySpawns    = enemies.spawn(platforms, config, tiles, cols, rows);
        enemySpeedScale = config.enemySpeedScale;

        // ---- Sub-generator 5: Item distribution (Level 2) ----
        // Depends on: platform list, tile state, AND groundTopRow (alcove placement)
        ItemDistributor items = new ItemDistributor(tileSize, seed + 4L);
        items.distribute(tiles, platforms, config, cols, rows, terrain.groundTopRow);
        grappleX = items.grappleX;
        grappleY = items.grappleY;

        // ---- Derive spawn / portal world positions ----
        playerSpawnX = 5 * tileSize + tileSize * 0.5f;
        playerSpawnY = terrain.groundTopRow * (float)tileSize - 120f;

        // Portal sits in the middle of the summit platform, slightly above it
        portalX = (terrain.portalCol + 4) * (float)tileSize;
        portalY = terrain.portalRow  * (float)tileSize - 40f;

        return tiles;
    }
}
