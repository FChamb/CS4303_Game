public class Vec2 {
    public float x, y;

    public Vec2() { this(0, 0); }
    public Vec2(float x, float y) { this.x = x; this.y = y; }

    public Vec2 copy() { return new Vec2(x, y); }

    public Vec2 set(float x, float y) { this.x = x; this.y = y; return this; }
    public Vec2 add(Vec2 o) { x += o.x; y += o.y; return this; }
    public Vec2 sub(Vec2 o) { x -= o.x; y -= o.y; return this; }
    public Vec2 mult(float s) { x *= s; y *= s; return this; }

    public float magSq() { return x*x + y*y; }

    public static Vec2 add(Vec2 a, Vec2 b) { return new Vec2(a.x + b.x, a.y + b.y); }
    public static Vec2 mult(Vec2 a, float s) { return new Vec2(a.x * s, a.y * s); }
}