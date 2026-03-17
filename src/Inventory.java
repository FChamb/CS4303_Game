/**
 * Simple fixed size hotbar inventory.
 *
 * The player only has two hotbar slots, and each slot can hold a small number
 * of blocks. I added these limits so the player cannot simply build all the
 * way to the goal without engaging with the level layout.
 */
public class Inventory {
    private static final int MAX_STACK_PER_SLOT = 4;

    private final int[] types;
    private final int[] counts;

    public Inventory(int slots) {
        types = new int[slots];
        counts = new int[slots];

        for (int i = 0; i < slots; i++) {
            types[i] = TileTypes.AIR;
            counts[i] = 0;
        }
    }

    /**
     * Number of hotbar slots.
     */
    public int size() {
        return types.length;
    }

    /**
     * Returns the block type stored in the given slot, or AIR if empty.
     */
    public int peekType(int slot) {
        if (slot < 0 || slot >= types.length) return TileTypes.AIR;
        return types[slot];
    }

    /**
     * Returns the number of blocks stored in the given slot.
     */
    public int peekCount(int slot) {
        if (slot < 0 || slot >= counts.length) return 0;
        return counts[slot];
    }

    /**
     * Returns true if the inventory has room for one block of the given type.
     *
     * A block can be added if:
     * - there is already a stack of that type with count < MAX_STACK_PER_SLOT
     * - or there is an empty slot
     */
    public boolean canAddBlock(int blockType) {
        if (blockType == TileTypes.AIR) return false;

        // Existing matching stack with room
        for (int i = 0; i < types.length; i++) {
            if (types[i] == blockType && counts[i] < MAX_STACK_PER_SLOT) {
                return true;
            }
        }

        // Empty slot available
        for (int i = 0; i < types.length; i++) {
            if (counts[i] == 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds one block of the given type if there is space.
     *
     * Priority:
     * 1. Add to an existing matching stack if it has room
     * 2. Otherwise place into an empty slot
     *
     * Returns true if successful.
     */
    public boolean addBlock(int blockType) {
        if (blockType == TileTypes.AIR) return false;

        // Add to existing stack first
        for (int i = 0; i < types.length; i++) {
            if (types[i] == blockType && counts[i] < MAX_STACK_PER_SLOT) {
                counts[i]++;
                return true;
            }
        }

        // Otherwise use empty slot
        for (int i = 0; i < types.length; i++) {
            if (counts[i] == 0) {
                types[i] = blockType;
                counts[i] = 1;
                return true;
            }
        }

        return false;
    }

    /**
     * Consumes one block from the given slot and returns its type.
     * Returns AIR if the slot is empty or invalid.
     */
    public int consumeFromSlot(int slot) {
        if (slot < 0 || slot >= types.length) return TileTypes.AIR;
        if (counts[slot] <= 0) return TileTypes.AIR;

        int t = types[slot];
        counts[slot]--;

        if (counts[slot] == 0) {
            types[slot] = TileTypes.AIR;
        }

        return t;
    }
}