import processing.core.PApplet;

public class Portal {
    public final Vec2 pos = new Vec2();
    public final float radius;

    public Portal(float x, float y, float radius) {
        pos.set(x, y);
        this.radius = radius;
    }

    public void draw(PApplet p) {
        float pulse = 0.5f + 0.5f * PApplet.sin(p.millis() * 0.006f);
        float outer = radius * (2.3f + 0.35f * pulse);
        float inner = radius * (1.2f + 0.18f * pulse);

        p.noStroke();
        p.fill(180, 120, 255, 55 + (int)(65 * pulse));
        p.circle(pos.x, pos.y, outer * 1.45f);

        p.noFill();
        p.stroke(210, 140, 255);
        p.strokeWeight(5);
        p.circle(pos.x, pos.y, outer);

        p.stroke(245, 210, 255);
        p.strokeWeight(2);
        p.circle(pos.x, pos.y, inner);
    }
}
