public class GravityForce implements ForceGenerator {
    private final Vec2 g;

    public GravityForce(float gx, float gy) {
        this.g = new Vec2(gx, gy);
    }

    @Override
    public void applyForce(Body body) {
        if (body.invMass <= 0) return;
        // F = m * g. With invMass, mass = 1/invMass
        float mass = 1.0f / body.invMass;
        body.addForce(new Vec2(g.x * mass, g.y * mass));
    }
}