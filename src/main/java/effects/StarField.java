package effects;

import core.Updatable;
import core.Drawable;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StarField implements Updatable, Drawable {
    private record Star(double x, double y, double speed, float bright, float size) {}
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
        for (int i = 0; i < stars.size(); i++) {
            Star s = stars.get(i);
            if (s.y + s.speed * dt > H) {
                stars.set(i, new Star(rng.nextDouble() * W, -2, s.speed, s.bright, s.size));
            } else {
                stars.set(i, new Star(s.x, s.y + s.speed * dt, s.speed, s.bright, s.size));
            }
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