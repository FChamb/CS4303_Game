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
 *   Level 1 → difficulty 2  (introductory, wide platforms, small gaps, 1 enemy)
 *   Level 2 → difficulty 4  (comfortable, moderate gaps, 2 enemies)
 *   Level 3 → difficulty 6  (challenging, some build-requiring gaps, 3 enemies)
 *   Level 4 → difficulty 8  (hard, most gaps need blocks/grapple, 4–5 enemies)
 *   Level 5 → difficulty 10 (maximum, minimal resources, 6 enemies)
 *
 * The curve was chosen empirically so each transition feels like a noticeable
 * step up: platform length drops, gap size grows, and enemy count increases.
 */
public class LevelManager {

    private static final int   TOTAL_LEVELS  = 5;
    private static final int[] DIFFICULTIES  = {2, 4, 6, 8, 10};

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
