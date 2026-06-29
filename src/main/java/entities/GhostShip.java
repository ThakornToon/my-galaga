package entities;

import core.World;
import java.awt.*;

/**
 * Ghost ship towed behind a capturing ShooterEnemy.
 * Extends GameObject so world.add/allOf work normally.
 *
 * Hit by player bullet → ghost destroyed (player does NOT recover life).
 * ShooterEnemy is hit while ghost alive → ghost destroyed + player gains 1 life + dual fighter.
 */
public class GhostShip extends GameObject {

    private double blinkTimer = 0;
    private static final double W = 30, H = 26;

    private static final Color COLOR_BODY = new Color(100, 180, 255);
    private static final Color COLOR_WING = new Color(40,  80,  180);

    public GhostShip(World world, double x, double y) {
        super(world, x, y, W, H);
    }

    /** Called each frame by ShooterEnemy to tow the ghost */
    public void setPosition(double nx, double ny) {
        this.x = nx;
        this.y = ny;
    }

    @Override
    public void update(double dt) {
        blinkTimer += dt;
    }

    @Override
    public void draw(Graphics2D g2) {
        if (!alive) return;
        // Slow blink — fast blink made the ghost nearly invisible
        if ((int)(blinkTimer * 3) % 2 == 0) return;

        // Golden capture aura
        float auraPulse = (float)(0.5 + 0.5 * Math.sin(blinkTimer * 6));
        g2.setColor(new Color(255, 220, 50, (int)(auraPulse * 100)));
        g2.fillOval((int)(x - W/2 - 8), (int)(y - H/2 - 8),
                (int)(W + 16), (int)(H + 16));
        g2.setColor(new Color(255, 200, 30, (int)(auraPulse * 180)));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x - W/2 - 8), (int)(y - H/2 - 8),
                (int)(W + 16), (int)(H + 16));

        // Outer glow
        g2.setColor(new Color(100, 180, 255, 40));
        g2.fillOval((int)(x - W/2 - 5), (int)(y - H/2 - 5),
                (int)(W + 10), (int)(H + 10));

        // Body (same shape as Player, blue-grey tint)
        int[] bx = {(int)x, (int)(x - W/2), (int)(x - W/3),
                (int)(x + W/3), (int)(x + W/2)};
        int[] by = {(int)(y - H/2), (int)(y + H/2), (int)(y + H/4),
                (int)(y + H/4), (int)(y + H/2)};
        g2.setColor(COLOR_WING);
        g2.fillPolygon(bx, by, 5);
        g2.setColor(COLOR_BODY);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(bx, by, 5);

        // Cockpit
        g2.setColor(new Color(150, 220, 255, 160));
        g2.fillOval((int)(x - 6), (int)(y - H/2 + 3), 12, 10);
    }

    @Override
    public void destroy() {
        super.destroy();
        world.spawnExplosion(x, y, COLOR_BODY);
    }
}