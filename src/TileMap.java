import processing.core.PApplet;

public class TileMap {
    public final int tileSize;
    public final int cols, rows;

    private final int[][] tiles;

    public TileMap(int screenW, int screenH, int tileSize) {
        this.tileSize = tileSize;
        this.cols = (int)Math.ceil(screenW / (float)tileSize);
        this.rows = (int)Math.ceil(screenH / (float)tileSize);
        this.tiles = new int[rows][cols];

        generateTestLevel();
    }

    private void generateTestLevel() {
        // Start all air
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = TileTypes.AIR;
            }
        }

        // Unbreakable border
        for (int c = 0; c < cols; c++) {
            tiles[0][c] = TileTypes.BORDER;
            tiles[rows - 1][c] = TileTypes.BORDER;
        }
        for (int r = 0; r < rows; r++) {
            tiles[r][0] = TileTypes.BORDER;
            tiles[r][cols - 1] = TileTypes.BORDER;
        }

        // Ground layers
        int groundTop = rows - 6;
        for (int c = 1; c < cols - 1; c++) {
            tiles[groundTop][c] = TileTypes.GRASS;
            tiles[groundTop + 1][c] = TileTypes.DIRT;
            tiles[groundTop + 2][c] = TileTypes.DIRT;
            for (int r = groundTop + 3; r < rows - 1; r++) {
                tiles[r][c] = TileTypes.STONE;
            }
        }

        // Wooden platforms
        for (int c = 6; c < 12; c++) tiles[rows - 10][c] = TileTypes.WOOD;
        for (int c = 14; c < 19; c++) tiles[rows - 13][c] = TileTypes.WOOD;

        // Stone pillar
        for (int r = rows - 12; r < rows - 6; r++) tiles[r][3] = TileTypes.STONE;
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
        if (tiles[r][c] == TileTypes.BORDER) return; // never overwrite border
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