/**
 * A simple constant gravity force generator.
 *
 * This applies the same gravitational acceleration to any body
 * it is registered to. The actual force depends on the body's mass:
 *
 * F = m * g
 *
 * This class demonstrates the modular force-generator design
 * used in the Level 3 engine.
 */
public class GravityForce implements ForceGenerator {
    /** Gravity vector (for example, 0, 900 for downward gravity). */
    private final Vec2 g;

    /**
     * Creates a gravity force generator with the given x/y acceleration.
     */
    public GravityForce(float gx, float gy) {
        this.g = new Vec2(gx, gy);
    }

    /**
     * Applies gravitational force to the body.
     *
     * invMass is stored instead of mass, so:
     * mass = 1 / invMass
     */
    @Override
    public void applyForce(Body body) {
        if (body.invMass <= 0) return;

        // Convert inverse mass back into mass.
        float mass = 1.0f / body.invMass;

        // Apply F = m * g to the force accumulator.
        body.addForce(new Vec2(g.x * mass, g.y * mass));
    }
}