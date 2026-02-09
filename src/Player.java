import processing.core.PApplet;

public class Player {
    public final Body body;
    public final Vec2 thrust = new Vec2();

    // AABB size
    public float w = 26;
    public float h = 34;

    public boolean grounded = false;

    public Player(float x, float y) {
        body = new Body(x, y);
    }

    public float left()   { return body.position.x - w / 2f; }
    public float right()  { return body.position.x + w / 2f; }
    public float top()    { return body.position.y - h / 2f; }
    public float bottom() { return body.position.y + h / 2f; }

    public void draw(PApplet p) {
        p.rectMode(PApplet.CENTER);
        p.noStroke();
        p.fill(80, 170, 255);
        p.rect(body.position.x, body.position.y, w, h);

        if (grounded) {
            p.fill(0, 220, 120);
            p.circle(body.position.x, body.position.y - h/2f - 6, 6);
        }
    }

    public boolean isAtPortal(Portal portal) {
        float dx = body.position.x - portal.pos.x;
        float dy = body.position.y - portal.pos.y;
        float distSq = dx*dx + dy*dy;
        float r = Math.max(w, h) * 0.5f + portal.radius;
        return distSq <= r*r;
    }
}