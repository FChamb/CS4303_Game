public final class TileTypes {
    private TileTypes() {}

    // IDs
    public static final int AIR    = 0;
    public static final int GRASS  = 1;
    public static final int DIRT   = 2;
    public static final int STONE  = 3;
    public static final int WOOD   = 4;
    public static final int BORDER = 9; // unbreakable world border

    public static boolean isSolid(int type) {
        return type != AIR;
    }

    public static boolean isBreakable(int type) {
        return type != AIR && type != BORDER;
    }

    // Seconds to mine (tune these)
    public static float breakTimeSeconds(int type) {
        return switch (type) {
            case GRASS -> 0.25f;
            case DIRT  -> 0.35f;
            case WOOD  -> 0.55f;
            case STONE -> 0.90f;
            case BORDER, AIR -> Float.POSITIVE_INFINITY;
            default -> 0.5f;
        };
    }

    // Exact colors requested:
    // Grass green, Dirt light brown, Stone grey, Wood dark brown, Border black
    public static int color(int type) {
        return switch (type) {
            case GRASS  -> 0x2ECC40; // green
            case DIRT   -> 0xC49A6C; // light brown
            case STONE  -> 0x808080; // grey
            case WOOD   -> 0x5C3A1E; // dark brown
            case BORDER -> 0x000000; // black
            default -> 0xFFFFFF;
        };
    }

    public static String name(int type) {
        return switch (type) {
            case GRASS -> "Grass";
            case DIRT -> "Dirt";
            case STONE -> "Stone";
            case WOOD -> "Wood";
            case BORDER -> "Border";
            case AIR -> "Empty";
            default -> "Unknown";
        };
    }

    public static boolean isPlaceable(int type) {
        return type != AIR && type != BORDER;
    }
}