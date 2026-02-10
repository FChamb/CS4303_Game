import processing.core.PApplet;

public class TileMap {
    public final int tileSize;
    public final int cols, rows;

    // 0 = air, >0 = solid block type
    private final int[][] tiles;

    public TileMap(int screenW, int screenH, int tileSize) {
        this.tileSize = tileSize;
        this.cols = (int)Math.ceil(screenW / (float)tileSize);
        this.rows = (int)Math.ceil(screenH / (float)tileSize);
        this.tiles = new int[rows][cols];

        generateTestLevel();
    }

    private void generateTestLevel() {
        // Ground: last 2 rows solid (type 1)
        for (int c = 0; c < cols; c++) {
            tiles[rows - 1][c] = 1;
            tiles[rows - 2][c] = 1;
        }

        // Platforms
        for (int c = 6; c < 12; c++) tiles[rows - 6][c] = 1;
        for (int c = 14; c < 19; c++) tiles[rows - 9][c] = 1;

        // Small wall
        for (int r = rows - 7; r < rows - 2; r++) tiles[r][3] = 1;
    }

    public boolean inBounds(int r, int c) {
        return !(r < 0 || r >= rows || c < 0 || c >= cols);
    }

    public int getTile(int r, int c) {
        if (!inBounds(r, c)) return 1; // outside world treated as solid
        return tiles[r][c];
    }

    public void setTile(int r, int c, int type) {
        if (!inBounds(r, c)) return;
        tiles[r][c] = type;
    }

    public boolean isSolidTile(int r, int c) {
        return getTile(r, c) != 0;
    }

    public int worldToTileCol(float x) {
        return (int)Math.floor(x / tileSize);
    }

    public int worldToTileRow(float y) {
        return (int)Math.floor(y / tileSize);
    }

    public void draw(PApplet p) {
        p.rectMode(PApplet.CORNER);
        p.noStroke();

        for (int r = 0; r < rows; r++) {
            int y = r * tileSize;
            for (int c = 0; c < cols; c++) {
                int t = tiles[r][c];
                if (t == 0) continue;

                // Simple coloring by type (expand later)
                if (t == 1) p.fill(55);     // dirt-ish
                else p.fill(90);            // other types

                int x = c * tileSize;
                p.rect(x, y, tileSize, tileSize);
            }
        }
    }
}