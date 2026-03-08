public class Inventory {
    public static class Slot {
        public int type = TileTypes.AIR;
        public int count = 0;
    }

    private final Slot[] slots;

    public Inventory(int slotCount) {
        slots = new Slot[slotCount];
        for (int i = 0; i < slotCount; i++) slots[i] = new Slot();
    }

    public int size() { return slots.length; }

    public Slot get(int idx) { return slots[idx]; }

    // Add one block of a given type.
    // If type already exists in a slot -> increment.
    // Else first empty slot becomes that type.
    // Returns true if added, false if inventory full for that type.
    public boolean addBlock(int type) {
        if (!TileTypes.isPlaceable(type)) return false;

        // existing slot
        for (Slot s : slots) {
            if (s.type == type) {
                s.count++;
                return true;
            }
        }

        // empty slot
        for (Slot s : slots) {
            if (s.type == TileTypes.AIR || s.count == 0) {
                s.type = type;
                s.count = 1;
                return true;
            }
        }

        return false; // full
    }

    // Consume one from selected slot. Returns consumed block type, or AIR if none.
    public int consumeFromSlot(int idx) {
        if (idx < 0 || idx >= slots.length) return TileTypes.AIR;
        Slot s = slots[idx];
        if (s.type == TileTypes.AIR || s.count <= 0) return TileTypes.AIR;

        int t = s.type;
        s.count--;
        if (s.count == 0) s.type = TileTypes.AIR;
        return t;
    }

    public int peekType(int idx) {
        if (idx < 0 || idx >= slots.length) return TileTypes.AIR;
        Slot s = slots[idx];
        if (s.count <= 0) return TileTypes.AIR;
        return s.type;
    }

    public int peekCount(int idx) {
        if (idx < 0 || idx >= slots.length) return 0;
        return slots[idx].count;
    }
}