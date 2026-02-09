import processing.core.PApplet;

public class TileMap {
    public final int tileSize;
    public final int cols, rows;
    private final boolean[][] solid;

    public TileMap(int screenW, int screenH, int tileSize) {
        this.tileSize = tileSize;
        this.cols = (int)Math.ceil(screenW / (float)tileSize);
        this.rows = (int)Math.ceil(screenH / (float)tileSize);
        this.solid = new boolean[rows][cols];

        generateTestLevel();
    }

    private void generateTestLevel() {
        // Ground: last 2 rows solid
        for (int c = 0; c < cols; c++) {
            solid[rows - 1][c] = true;
            solid[rows - 2][c] = true;
        }

        // Platforms
        for (int c = 6; c < 12; c++) solid[rows - 6][c] = true;
        for (int c = 14; c < 19; c++) solid[rows - 9][c] = true;

        // Small wall
        for (int r = rows - 7; r < rows - 2; r++) solid[r][3] = true;
    }

    public boolean isSolidTile(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return true; // outside = solid
        return solid[r][c];
    }

    public void draw(PApplet p) {
        p.rectMode(PApplet.CORNER);
        p.noStroke();
        p.fill(55);

        for (int r = 0; r < rows; r++) {
            int y = r * tileSize;
            for (int c = 0; c < cols; c++) {
                if (!solid[r][c]) continue;
                int x = c * tileSize;
                p.rect(x, y, tileSize, tileSize);
            }
        }
    }
}