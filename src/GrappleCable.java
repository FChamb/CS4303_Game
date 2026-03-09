/**
 * Represents the Stage 4 grappling hook cable constraint.
 *
 * Unlike a spring, this does not pull the player elastically.
 * Instead, it behaves like a cable:
 * - the player may move freely while inside the cable length
 * - if the player moves beyond the cable length, they are projected
 *   back onto the cable radius
 * - outward radial velocity is removed, while tangential velocity is kept
 *
 * This allows pendulum like swinging behavior.
 */
public class GrappleCable {
    /** Fixed anchor point of the cable. */
    public final Vec2 anchor = new Vec2();

    /** Maximum allowed distance between the body and the anchor. */
    public float cableLength = 0f;

    /** Whether the cable is currently attached. */
    public boolean attached = false;

    /**
     * Attaches the cable to the given world position.
     * The initial cable length is set to the body's current distance
     * from that anchor.
     */
    public void attach(Body body, float ax, float ay) {
        anchor.set(ax, ay);

        float dx = body.position.x - ax;
        float dy = body.position.y - ay;
        cableLength = (float)Math.sqrt(dx * dx + dy * dy);

        attached = true;
    }

    /**
     * Detaches the cable.
     */
    public void detach() {
        attached = false;
    }

    /**
     * Enforces the cable constraint on the body.
     *
     * If the body is farther than the cable length from the anchor:
     * 1. Project the body back onto the cable radius
     * 2. Remove any outward radial velocity
     * 3. Preserve tangential velocity so the body can swing naturally
     */
    public void enforce(Body body) {
        if (!attached) return;
        if (body.invMass <= 0) return;

        float dx = body.position.x - anchor.x;
        float dy = body.position.y - anchor.y;

        float distSq = dx * dx + dy * dy;
        if (distSq < 0.0001f) return;

        float dist = (float)Math.sqrt(distSq);

        // If the body is still inside the cable radius, no correction is needed.
        if (dist <= cableLength) return;

        float dirX = dx / dist;
        float dirY = dy / dist;

        // Project the body back onto the circle defined by the cable length.
        body.position.x = anchor.x + dirX * cableLength;
        body.position.y = anchor.y + dirY * cableLength;

        // Remove only the outward radial component of velocity.
        // Tangential velocity is preserved so the player can swing.
        float radialVel = body.velocity.x * dirX + body.velocity.y * dirY;
        if (radialVel > 0) {
            body.velocity.x -= radialVel * dirX;
            body.velocity.y -= radialVel * dirY;
        }
    }
}