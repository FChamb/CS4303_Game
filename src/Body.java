/**
 * Represents a single dynamic body in the physics engine.
 *
 * This class stores the physical state needed for Level 1 physics:
 * - position
 * - velocity
 * - acceleration
 * - mass (stored as inverse mass)
 *
 * It also stores a force accumulator so that multiple forces can be
 * applied during a frame and then integrated together.
 */
public class Body {
    /** Current position of the body in world space. */
    public Vec2 position = new Vec2();

    /** Current linear velocity of the body. */
    public Vec2 velocity = new Vec2();

    /** Current acceleration, computed from the accumulated forces. */
    public Vec2 acceleration = new Vec2();

    /**
     * Inverse mass is used instead of mass so that:
     * - multiplication can be used instead of division
     * - invMass = 0 represents an immovable/infinite mass object
     */
    public float invMass = 1.0f;

    /**
     * Stores all forces applied during the current timestep.
     * This is cleared after integration.
     */
    private Vec2 forceAccum = new Vec2();

    /**
     * Creates a body at the given starting position.
     */
    public Body(float x, float y) {
        position.set(x, y);
    }

    /**
     * Adds a force to the body for the current frame.
     * Multiple systems (gravity, movement, springs, etc.) can all call this
     * before integration occurs.
     */
    public void addForce(Vec2 f) {
        forceAccum.add(f);
    }

    /**
     * Advances the body forward by dt seconds using semi-implicit Euler integration.
     *
     * Steps:
     * 1. Convert accumulated force into acceleration using F = ma
     *    => a = F / m = F * invMass
     * 2. Update velocity from acceleration
     * 3. Apply damping to reduce endless drifting
     * 4. Update position from velocity
     * 5. Clear accumulated forces ready for the next frame
     *
     * Semi-implicit Euler is used because it is straightforward and more stable
     * than explicit Euler for this type of game physics.
     */
    public void integrate(float dt) {
        // Bodies with zero inverse mass are treated as immovable.
        if (invMass <= 0) return;

        // Compute acceleration from the total force applied this frame.
        acceleration.set(forceAccum.x * invMass, forceAccum.y * invMass);

        // Update velocity first.
        velocity.add(Vec2.mult(acceleration, dt));

        // Apply simple damping so the player does not drift forever.
        float damping = 0.98f;
        velocity.mult((float)Math.pow(damping, dt * 60.0f));

        // Then update the position using the new velocity.
        position.add(Vec2.mult(velocity, dt));

        // Reset the force accumulator for the next timestep.
        forceAccum.set(0, 0);
    }
}