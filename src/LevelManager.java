/**
 * Level 3 – difficulty progression and level sequencing.
 *
 * Manages a five-level campaign where each level uses a higher difficulty,
 * producing measurably harder generated content as the player progresses.
 * Each level also receives a unique seed derived from the base seed so that
 * repeated playthroughs of the same level number still look different from
 * one session to the next.
 *
 * Difficulty curve:
 *   Level 1 → difficulty 1  (tutorial-like opener, widest platforms, light pressure)
 *   Level 2 → difficulty 3  (comfortable, moderate spacing, low enemy pressure)
 *   Level 3 → difficulty 5  (challenging, clearer need for tool usage)
 *   Level 4 → difficulty 7  (hard, frequent build/grapple decisions)
 *   Level 5 → difficulty 9  (near-maximum challenge without abrupt spike)
 *
 * The curve was chosen empirically so each transition feels like a noticeable
 * step up: platform length drops, gap size grows, and enemy count increases.
 */
public class LevelManager {

    private static final int   TOTAL_LEVELS  = 5;
    private static final int[] DIFFICULTIES  = {1, 3, 5, 7, 9};

    private int  currentIndex;   // 0-based
    private long baseSeed;

    public LevelManager(long baseSeed) {
        this.baseSeed     = baseSeed;
        this.currentIndex = 0;
    }

    // ------------------------------------------------------------------ state

    public boolean hasNextLevel() {
        return currentIndex < TOTAL_LEVELS - 1;
    }

    public void advanceLevel() {
        if (hasNextLevel()) currentIndex++;
    }

    public void reset() {
        currentIndex = 0;
    }

    // ------------------------------------------------------------------ info

    /** 1-based level number for display. */
    public int getLevelNumber() {
        return currentIndex + 1;
    }

    public int getDifficulty() {
        return DIFFICULTIES[Math.min(currentIndex, DIFFICULTIES.length - 1)];
    }

    public int getNextDifficulty() {
        int next = Math.min(currentIndex + 1, DIFFICULTIES.length - 1);
        return DIFFICULTIES[next];
    }

    public int getTotalLevels() {
        return TOTAL_LEVELS;
    }

    // ------------------------------------------------------------------ factory

    /**
     * Creates a LevelGenerator configured for the current level.
     * Each level gets a deterministic but unique seed so layouts vary.
     */
    public LevelGenerator createGenerator(int worldW, int worldH, int tileSize) {
        long levelSeed = baseSeed ^ (long)(currentIndex * 0x9E3779B97F4A7C15L);
        int  diff      = DIFFICULTIES[Math.min(currentIndex, DIFFICULTIES.length - 1)];
        return new LevelGenerator(worldW, worldH, tileSize, diff, levelSeed);
    }
}
