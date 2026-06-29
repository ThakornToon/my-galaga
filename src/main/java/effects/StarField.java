package effects;

import core.Updatable;
import core.Drawable;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StarField implements Updatable, Drawable {
    // Mutable so stars can drift + wrap in place. As an immutable record this
    // allocated a fresh Star for all 120 stars every frame (~29k objects/sec) for
    // no benefit — the field set is fixed and only x/y change.
    private static final class Star {
        double x, y;
        final double speed;
        final float bright, size;
        Star(double x, double y, double speed, float bright, float size) {
            this.x = x; this.y = y; this.speed = speed; this.bright = bright; this.size = size;
        }
    }
    private final List<Star> stars = new ArrayList<>();
    private final int W, H;
    private final Random rng = new Random();

    public StarField(int W, int H) {
        this.W = W;
        this.H = H;
        for (int i = 0; i < 120; i++) addStar(rng.nextDouble() * H);
    }

    private void addStar(double startY) {
        stars.add(new Star(
                rng.nextDouble() * W, startY,
                20 + rng.nextFloat() * 60,
                0.3f + rng.nextFloat() * 0.7f,
                0.5f + rng.nextFloat() * 1.5f));
    }

    @Override
    public void update(double dt) {
        for (Star s : stars) {
            s.y += s.speed * dt;
            if (s.y > H) { s.x = rng.nextDouble() * W; s.y = -2; }
        }
    }

    @Override
    public void draw(Graphics2D g) {
        for (Star s : stars) {
            int a = (int) (s.bright * 255);
            g.setColor(new Color(a, a, Math.min(255, a + 30), a));
            g.fill(new Ellipse2D.Double(s.x - s.size / 2, s.y - s.size / 2, s.size, s.size));
        }
    }
}