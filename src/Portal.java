import processing.core.PApplet;

public class Portal {
    public final Vec2 pos = new Vec2();
    public final float radius;

    public Portal(float x, float y, float radius) {
        pos.set(x, y);
        this.radius = radius;
    }

    public void draw(PApplet p) {
        p.noFill();
        p.stroke(190, 100, 255);
        p.strokeWeight(4);
        p.circle(pos.x, pos.y, radius * 2.2f);
        p.strokeWeight(2);
        p.circle(pos.x, pos.y, radius * 1.3f);
    }
}