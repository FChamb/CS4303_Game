/**
 * Maps a difficulty value (1–10) to concrete generation parameters.
 *
 * All sub-generators receive one of these objects so every aspect of
 * generation scales together from a single knob.
 *
 * Physics constraints (from player):
 *   max jump height  ≈ 3 tiles  (v0=430, g=900  →  v²/2g ≈ 103px)
 *   max horiz reach  ≈ 7 tiles  (speed=220, air_time=0.96s → 211px)
 *
 * At difficulty ≥ 7 some gaps intentionally exceed safe-jump distance,
 * requiring the player to place blocks or use the grapple hook.
 * resourcePileCount scales inversely so there are always enough materials
 * to make even the hardest level completable.
 */
public class DifficultyConfig {
    public final int difficulty;

    // Platform dimensions
    public final int platformLenMin;
    public final int platformLenMax;

    // Horizontal gap between platforms (tiles)
    public final int gapMin;
    public final int gapMax;

    // Vertical rise per step (tiles) — capped at 3, the physics jump limit
    public final int riseMin;
    public final int riseMax;

    // Enemy generation
    public final int   enemyCount;
    public final float enemySpeedScale;

    // Item / resource generation
    public final int resourcePileCount;

    public DifficultyConfig(int difficulty) {
        this.difficulty = clamp(difficulty, 1, 10);
        float t = (this.difficulty - 1) / 9.0f;   // 0.0 at diff=1 … 1.0 at diff=10

        // Wider platforms on easy, narrower on hard
        platformLenMin = lerpi(6, 3, t);
        platformLenMax = lerpi(8, 4, t);

        // Gaps form canyons between mesas — min 3 tiles so canyons feel open.
        // At diff ≥ 7 the maximum intentionally exceeds safe-jump (~6 tiles),
        // forcing block placement or grapple use.
        gapMin = lerpi(3, 4, t);
        gapMax = lerpi(5, 7, t);

        // Rises stay within the physics jump ceiling (3 tiles)
        riseMin = lerpi(1, 2, t);
        riseMax = 3;

        // 1 enemy at diff 1, up to 6 at diff 10
        enemyCount = 1 + Math.round(t * 5);

        // Enemies move 70 % of base at diff 1, 150 % at diff 10
        enemySpeedScale = lerpf(0.70f, 1.50f, t);

        // Many resources on easy levels, few on hard ones
        resourcePileCount = lerpi(10, 2, t);
    }

    // ------------------------------------------------------------------ helpers

    private static int lerpi(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static float lerpf(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
