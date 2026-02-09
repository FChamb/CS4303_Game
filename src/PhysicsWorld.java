import java.util.ArrayList;
import java.util.List;

public class PhysicsWorld {
    public final List<Body> bodies = new ArrayList<>();
    public final ForceRegistry forceRegistry = new ForceRegistry();

    public void addBody(Body b) { bodies.add(b); }

    public void step(float dt) {
        // Apply continuous forces (gravity, wind, etc.)
        forceRegistry.updateForces();

        // Integrate bodies
        for (Body b : bodies) {
            b.integrate(dt);
        }
    }
}