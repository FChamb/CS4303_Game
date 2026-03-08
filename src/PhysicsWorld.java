import java.util.ArrayList;
import java.util.List;

/**
 * The main physics container for the game.
 *
 * This class owns:
 * - all simulated bodies
 * - the registry of force generators acting on those bodies
 *
 * Its job is to advance the simulation by one timestep.
 * This separation helps keep the engine modular and supports the
 * Level 3 requirement for cleaner software architecture.
 */
public class PhysicsWorld {
    /** All dynamic bodies currently being simulated. */
    public final List<Body> bodies = new ArrayList<>();

    /** Stores which force generators act on which bodies. */
    public final ForceRegistry forceRegistry = new ForceRegistry();

    /**
     * Adds a body to the simulation.
     */
    public void addBody(Body b) {
        bodies.add(b);
    }

    /**
     * Advances the entire world by dt seconds.
     *
     * Order of operations:
     * 1. Update all continuous forces (gravity, etc.)
     * 2. Integrate every body forward in time
     *
     * Collision handling is done outside this class in the game loop,
     * after integration has occurred.
     */
    public void step(float dt) {
        // Apply continuous forces such as gravity.
        forceRegistry.updateForces();

        // Integrate every body using its accumulated forces.
        for (Body b : bodies) {
            b.integrate(dt);
        }
    }
}