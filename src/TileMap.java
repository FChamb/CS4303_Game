import processing.core.PApplet;

public class TileMap {
    public final int tileSize;
    public final int cols, rows;

    private final int[][] tiles;

    // Useful landmark values for world layout
    private int groundTopRow;
    private float hiddenRewardX;
    private float hiddenRewardY;
    private float upperGoalY;

    public TileMap(int worldW, int worldH, int tileSize) {
        this.tileSize = tileSize;
        this.cols = (int)Math.ceil(worldW / (float)tileSize);
        this.rows = (int)Math.ceil(worldH / (float)tileSize);
        this.tiles = new int[rows][cols];

        generateTestLevel();
    }

    /**
     * Generates a tall mountain-like world with:
     * - an easier early route made mostly of jumpable platforms
     * - a harder late route with larger gaps and some building required
     * - more cohesive-looking ledges, cliffs, and summit geometry
     * - a hidden grapple reward area
     */
    private void generateTestLevel() {
        // Fill everything with air first
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = TileTypes.AIR;
            }
        }

        // World border
        for (int c = 0; c < cols; c++) {
            tiles[0][c] = TileTypes.BORDER;
            tiles[rows - 1][c] = TileTypes.BORDER;
        }
        for (int r = 0; r < rows; r++) {
            tiles[r][0] = TileTypes.BORDER;
            tiles[r][cols - 1] = TileTypes.BORDER;
        }

        // Main ground layer near the bottom
        groundTopRow = rows - 8;

        for (int c = 1; c < cols - 1; c++) {
            tiles[groundTopRow][c] = TileTypes.GRASS;
            tiles[groundTopRow + 1][c] = TileTypes.DIRT;
            tiles[groundTopRow + 2][c] = TileTypes.DIRT;
            for (int r = groundTopRow + 3; r < rows - 1; r++) {
                tiles[r][c] = TileTypes.STONE;
            }
        }

        // --- Start area: easier and more natural-looking ---
        // A few small cliffs and ledges to make the left side feel like a mountain base.
        makeColumn(4, groundTopRow - 1, 3, TileTypes.STONE);
        makeColumn(7, groundTopRow - 1, 4, TileTypes.STONE);
        makeColumn(11, groundTopRow - 1, 2, TileTypes.STONE);

        // Hidden grapple alcove in upper-left mountain side
        int alcoveBaseCol = 10;
        int alcoveRow = groundTopRow - 18;

        makeSolidPlatform(alcoveBaseCol, alcoveBaseCol + 6, alcoveRow, TileTypes.WOOD);
        makeColumn(alcoveBaseCol, alcoveRow, 6, TileTypes.STONE);
        makeColumn(alcoveBaseCol + 6, alcoveRow, 6, TileTypes.STONE);
        makeSolidPlatform(alcoveBaseCol, alcoveBaseCol + 6, alcoveRow - 5, TileTypes.STONE);

        // Opening into the alcove from the left side
        tiles[alcoveRow - 1][alcoveBaseCol] = TileTypes.AIR;
        tiles[alcoveRow - 2][alcoveBaseCol] = TileTypes.AIR;

        hiddenRewardX = (alcoveBaseCol + 3) * tileSize + tileSize * 0.5f;
        hiddenRewardY = (alcoveRow - 1) * tileSize + tileSize * 0.5f;

        // --- Main ascent route ---
        // The route moves upward-right and becomes harder over time.
        int currentCol = 8;
        int currentRow = groundTopRow - 4;

        int section = 0;
        while (currentCol < cols - 20 && currentRow > 10) {
            boolean early = section < 5;
            boolean mid = section >= 5 && section < 10;
            boolean late = section >= 10;

            int platformLen;
            int gap;
            int rise;

            if (early) {
                // Early route: mostly jumpable, no need to build
                platformLen = 5 + (section % 2);   // 5-6
                gap = 2 + (section % 2);           // 2-3
                rise = 1 + (section % 2);          // 1-2
            } else if (mid) {
                // Mid route: more awkward, more vertical
                platformLen = 4 + (section % 2);   // 4-5
                gap = 3 + (section % 3);           // 3-5
                rise = 2 + (section % 2);          // 2-3
            } else {
                // Late route: harder, bigger gaps, often requires building
                platformLen = 3 + (section % 2);   // 3-4
                gap = 5 + (section % 3);           // 5-7
                rise = 2 + (section % 3);          // 2-4
            }

            int type = (section % 3 == 0) ? TileTypes.WOOD : TileTypes.STONE;

            // Main ledge
            makeSolidPlatform(currentCol, currentCol + platformLen - 1, currentRow, type);

            // Add support under ledges so they look attached to cliff faces
            if (section % 2 == 0) {
                int supportCol = currentCol + 1;
                int supportHeight = early ? 3 : 4;
                for (int h = 1; h <= supportHeight; h++) {
                    if (currentRow + h < rows - 1) {
                        tiles[currentRow + h][supportCol] = TileTypes.STONE;
                    }
                }
            } else {
                int supportCol = currentCol + platformLen - 2;
                int supportHeight = early ? 3 : 4;
                for (int h = 1; h <= supportHeight; h++) {
                    if (currentRow + h < rows - 1) {
                        tiles[currentRow + h][supportCol] = TileTypes.STONE;
                    }
                }
            }

            // Add occasional little side shelf to make the world feel more deliberate
            if (!late && section % 3 == 1) {
                int shelfCol = currentCol - 3;
                if (shelfCol > 1) {
                    makeSolidPlatform(shelfCol, shelfCol + 1, currentRow + 2, TileTypes.DIRT);
                }
            }

            // In late sections, place a marker block or tiny landing hint,
            // but leave the real gap large enough that building helps.
            if (late && section % 2 == 1) {
                int markerCol = Math.min(currentCol + platformLen + 2, cols - 2);
                int markerRow = Math.max(2, currentRow - 1);
                tiles[markerRow][markerCol] = TileTypes.DIRT;
            }

            currentCol += platformLen + gap;
            currentRow -= rise;
            section++;
        }

        // --- Mountain shelves and ridges ---
        // These are placed around the world to make it feel like a mountain climb,
        // not just a single staircase of floating blocks.
        for (int start = 16; start < cols - 16; start += 18) {
            int ridgeRow = groundTopRow - 7 - (start % 6);
            int ridgeLen = 3 + (start % 2);

            // only add shelves that don't interfere too much with the main path
            makeSolidPlatform(start, Math.min(start + ridgeLen, cols - 2), ridgeRow, TileTypes.DIRT);

            if (ridgeRow + 1 < rows - 1) {
                for (int c = start + 1; c < Math.min(start + ridgeLen - 1, cols - 1); c++) {
                    tiles[ridgeRow + 1][c] = TileTypes.DIRT;
                }
            }
        }

        // Mountain spires / cliff pillars as landmarks
        for (int baseCol = 18; baseCol < cols - 8; baseCol += 17) {
            int h = 5 + (baseCol % 6);
            for (int k = 0; k < h; k++) {
                int rr = groundTopRow - 1 - k;
                if (rr > 1) tiles[rr][baseCol] = TileTypes.STONE;
                if (rr > 2 && k < h - 2 && baseCol + 1 < cols - 1 && k % 2 == 0) {
                    tiles[rr][baseCol + 1] = TileTypes.STONE;
                }
            }
        }

        // --- Portal summit zone ---
        // This should feel visually distinct and like the top of the mountain.
        int portalRow = Math.max(7, currentRow - 1);
        int summitStart = cols - 15;
        int summitEnd = cols - 4;

        // Main summit platform
        makeSolidPlatform(summitStart, summitEnd, portalRow, TileTypes.WOOD);

        // Add a broader stone summit mass underneath
        for (int r = portalRow + 1; r < Math.min(portalRow + 7, rows - 1); r++) {
            for (int c = summitStart + 1; c < summitEnd - 1; c++) {
                tiles[r][c] = TileTypes.STONE;
            }
        }

        // Add snowy-looking edge accents using wood/dirt/grass layering pattern substitute
        // since current tiles are limited. We'll use GRASS as the bright summit edge.
        for (int c = summitStart + 1; c < summitEnd - 1; c++) {
            tiles[portalRow - 1][c] = TileTypes.GRASS;
        }

        // Jagged summit silhouette around portal
        makeColumn(summitStart + 1, portalRow - 1, 3, TileTypes.STONE);
        makeColumn(summitStart + 4, portalRow - 1, 5, TileTypes.STONE);
        makeColumn(summitStart + 8, portalRow - 1, 4, TileTypes.STONE);

        // Final approach: intentionally awkward so the very end is harder
        tiles[portalRow][summitStart - 2] = TileTypes.AIR;
        tiles[portalRow][summitStart - 1] = TileTypes.AIR;
        tiles[portalRow + 1][summitStart - 3] = TileTypes.STONE;

        upperGoalY = portalRow * tileSize - 14.0f;
    }

    /**
     * Creates a horizontal platform from c1 to c2 inclusive.
     */
    private void makeSolidPlatform(int c1, int c2, int row, int type) {
        if (row < 1 || row >= rows - 1) return;
        int start = Math.max(1, c1);
        int end = Math.min(cols - 2, c2);
        for (int c = start; c <= end; c++) {
            tiles[row][c] = type;
        }
    }

    /**
     * Creates a vertical column downward from the given top row.
     */
    private void makeColumn(int col, int topRow, int height, int type) {
        if (col < 1 || col >= cols - 1) return;
        for (int i = 0; i < height; i++) {
            int r = topRow + i;
            if (r > 0 && r < rows - 1) {
                tiles[r][col] = type;
            }
        }
    }

    public boolean inBounds(int r, int c) {
        return !(r < 0 || r >= rows || c < 0 || c >= cols);
    }

    public int getTile(int r, int c) {
        if (!inBounds(r, c)) return TileTypes.BORDER;
        return tiles[r][c];
    }

    public void setTile(int r, int c, int type) {
        if (!inBounds(r, c)) return;
        if (tiles[r][c] == TileTypes.BORDER) return;
        tiles[r][c] = type;
    }

    public boolean isSolidTile(int r, int c) {
        return TileTypes.isSolid(getTile(r, c));
    }

    public int worldToTileCol(float x) {
        return (int)Math.floor(x / tileSize);
    }

    public int worldToTileRow(float y) {
        return (int)Math.floor(y / tileSize);
    }

    public int getWorldWidth() {
        return cols * tileSize;
    }

    public int getWorldHeight() {
        return rows * tileSize;
    }

    public float getGroundTopY() {
        return groundTopRow * tileSize;
    }

    public float getUpperGoalY() {
        return upperGoalY;
    }

    public float getHiddenRewardX() {
        return hiddenRewardX;
    }

    public float getHiddenRewardY() {
        return hiddenRewardY;
    }

    public void draw(PApplet p) {
        p.rectMode(PApplet.CORNER);
        p.noStroke();

        for (int r = 0; r < rows; r++) {
            int y = r * tileSize;
            for (int c = 0; c < cols; c++) {
                int t = tiles[r][c];
                if (t == TileTypes.AIR) continue;

                int rgb = TileTypes.color(t);
                p.fill((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);

                int x = c * tileSize;
                p.rect(x, y, tileSize, tileSize);
            }
        }
    }
}