import processing.core.PApplet;

public class Main extends PApplet {

    public static void main(String[] args) {
        PApplet.main("Main");
    }

    public void settings() {
        size(800, 800);
    }

    public void setup() {
        background(0);
    }

    public void draw() {
        ellipse(mouseX, mouseY, 50, 50);
    }
}