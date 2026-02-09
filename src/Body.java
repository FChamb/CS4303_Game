public class Body {
    public Vec2 position = new Vec2();
    public Vec2 velocity = new Vec2();
    public Vec2 acceleration = new Vec2();

    // Using inverse mass avoids division. invMass=0 means “infinite mass” (immovable).
    public float invMass = 1.0f;

    // Accumulate forces each frame then clear after integration
    private Vec2 forceAccum = new Vec2();

    public Body(float x, float y) {
        position.set(x, y);
    }

    public void addForce(Vec2 f) {
        forceAccum.add(f);
    }

    public void integrate(float dt) {
        if (invMass <= 0) return; // immovable

        // a = F * invMass
        acceleration.set(forceAccum.x * invMass, forceAccum.y * invMass);

        // Semi-implicit Euler:
        // v += a * dt
        velocity.add(Vec2.mult(acceleration, dt));

        // Optional damping to prevent infinite drifting
        float damping = 0.98f;
        velocity.mult((float)Math.pow(damping, dt * 60.0f));

        // x += v * dt
        position.add(Vec2.mult(velocity, dt));

        // clear forces
        forceAccum.set(0, 0);
    }
}