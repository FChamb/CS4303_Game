import java.util.ArrayList;
import java.util.List;

/**
 * Maps bodies to the force generators that affect them.
 *
 * This is part of the Level 3 engine design:
 * force generation is separated from body integration so that new
 * forces can be added without rewriting the Body class.
 */
public class ForceRegistry {

    /**
     * A single pairing of:
     * - one body
     * - one force generator acting on that body
     */
    private static class Registration {
        Body body;
        ForceGenerator fg;

        Registration(Body body, ForceGenerator fg) {
            this.body = body;
            this.fg = fg;
        }
    }

    /** All active body/force generator pairings. */
    private final List<Registration> regs = new ArrayList<>();

    /**
     * Registers a force generator to act on a body.
     */
    public void add(Body body, ForceGenerator fg) {
        regs.add(new Registration(body, fg));
    }

    /**
     * Calls every registered force generator so it can apply its force
     * to its corresponding body for the current frame.
     */
    public void updateForces() {
        for (Registration r : regs) {
            r.fg.applyForce(r.body);
        }
    }
}