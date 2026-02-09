import java.util.ArrayList;
import java.util.List;

public class ForceRegistry {
    private static class Registration {
        Body body;
        ForceGenerator fg;
        Registration(Body body, ForceGenerator fg) { this.body = body; this.fg = fg; }
    }

    private final List<Registration> regs = new ArrayList<>();

    public void add(Body body, ForceGenerator fg) {
        regs.add(new Registration(body, fg));
    }

    public void updateForces() {
        for (Registration r : regs) {
            r.fg.applyForce(r.body);
        }
    }
}