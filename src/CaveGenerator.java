import java.util.ArrayList;
import java.util.Random;

/**
 * Level 4 sub-generator – cave tunnel carving.
 *
 * Carves horizontal cave tunnels through mesa bodies, giving the player a
 * ground-level alternate route through each section.  This separates cave
 * concern from the main terrain generation, and depends on the platform list
 * produced by TerrainGenerator (a Level 4 data-dependency).
 *
 * Design
 * ------
 * Every cave entrance sits 3 tiles above the flat ground layer — just within
 * the player's jump height (103 px max ≈ 3.4 tiles).  The player can jump
 * into a cave from the canyon floor without needing to climb the mesa first.
 *
 * Cave is 2 tiles tall (just fits the 34 px player inside 60 px = 2 tiles).
 *
 *   Canyon view:
 *
 *     ║ S ║                 ← solid mesa body above
 *     ║   ║ ← entrance      ← cave ceiling row (groundTopRow - 3)
 *     ║   ║                 ← cave floor row   (groundTopRow - 2)
 *     ╠═══╣                 ← solid tile below cave (groundTopRow - 1)
 *     [ G ]                 ← GRASS ground layer
 *
 * The cave passes through the full width of the mesa and exits the other side,
 * so a player who fell into the canyon can tunnel straight through to the next
 * section instead of having to backtrack or climb.
 *
 * 35 % of qualifying mesas also get a small cavern room expanded from the
 * tunnel, rewarding exploration with a larger interior space.
 *
 * Placement rules (so caves don't break playability)
 * ---------------------------------------------------
 *  - Only mesas ≥ 5 tiles wide and ≥ 5 tiles tall get a cave.
 *  - The starting mesa and summit mesa are always excluded.
 *  - Each mesa has a 60 % chance of getting a cave.
 *  - If random rolls produce none, one eligible mesa is forced to get a cave.
 *  - Caves never carve through the BORDER tiles.
 */
public class CaveGenerator {

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
     * @param platforms    ordered critical-path platform list
     * @param groundTopRow ground surface row (from TerrainGenerator)
     */
    public void carve(int[][] tiles, ArrayList<int[]> platforms, int groundTopRow) {
        int caveCeiling = groundTopRow - 3; // 3 tiles above ground
        int caveFloor   = groundTopRow - 2; // 2 tiles above ground
        int carvedCount = 0;
        int fallbackIdx = -1;

        // Skip index 0 (start) and last (summit)
        for (int i = 1; i < platforms.size() - 1; i++) {
            int[] plat  = platforms.get(i);
            int platRow = plat[1];
            int platCol = plat[0];
            int platLen = plat[2];
            int height  = groundTopRow - platRow;

            // Need width ≥ 5 and enough mesa body to contain a cave
            if (platLen < 5 || height < 5) continue;

            // Cave must sit inside the mesa fill (below the platform surface)
            if (caveCeiling <= platRow) continue;

            if (fallbackIdx < 0) fallbackIdx = i;

            // 60 % chance
            if (rng.nextFloat() >= 0.60f) continue;
            carveTunnel(tiles, platCol, platLen, platRow, caveCeiling, caveFloor);
            carvedCount++;
        }

        // Guarantee at least one cave where possible.
        if (carvedCount == 0 && fallbackIdx >= 0) {
            int[] plat = platforms.get(fallbackIdx);
            carveTunnel(tiles, plat[0], plat[2], plat[1], caveCeiling, caveFloor);
        }
    }

    private void carveTunnel(int[][] tiles, int platCol, int platLen, int platRow,
                             int caveCeiling, int caveFloor) {
        // Carve the 2-row tunnel across the full platform width
        for (int r = caveCeiling; r <= caveFloor; r++) {
            for (int c = platCol; c < platCol + platLen && c < cols - 1; c++) {
                if (c >= 1 && r >= 1 && r < rows - 1
                        && tiles[r][c] != TileTypes.BORDER)
                    tiles[r][c] = TileTypes.AIR;
            }
        }

        // 35 % chance: expand a small cavern room in the middle of the tunnel
        if (rng.nextFloat() < 0.35f) {
            int roomW  = 3 + rng.nextInt(2);         // 3–4 tiles wide
            int roomC1 = platCol + 1 + rng.nextInt(Math.max(1, platLen - roomW - 1));
            int roomC2 = Math.min(platCol + platLen - 2, roomC1 + roomW - 1);
            int roomTop = caveCeiling - 1;             // one row higher

            if (roomTop >= 1 && roomTop > platRow) {
                for (int c = roomC1; c <= roomC2 && c < cols - 1; c++) {
                    if (tiles[roomTop][c] != TileTypes.BORDER)
                        tiles[roomTop][c] = TileTypes.AIR;
                }
            }
        }
    }
}
