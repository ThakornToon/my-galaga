package entities;

import core.World;

import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * A transient, purely-cosmetic ring that visualises the destruction radius of a
 * special-bullet detonation: a faint translucent disc plus a brighter outline
 * sitting at the exact AoE radius, fading out over ~0.45s. It takes no part in
 * collisions — CollisionManager already applied the damage when it was spawned.
 */
public class BlastRing extends GameObject {
    private final double radius;
    private final Color color;
    private double life;
    private final double maxLife = 0.45;

    public BlastRing(World world, double x, double y, double radius, Color color) {
        super(world, x, y, radius * 2, radius * 2);
        this.radius = radius;
        this.color  = color;
        this.life   = maxLife;
    }

    @Override
    public void update(double dt) {
        life -= dt;
        if (life <= 0) destroy();
    }

    @Override
    public void draw(Graphics2D g) {
        double f = Math.max(0, life / maxLife);          // 1 → 0
        // Ring snaps to the true radius instantly then fades, so the player can
        // read exactly how far the blast reached.
        double r = radius * (0.82 + 0.18 * (1 - f));      // quick settle to full R

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Faint filled disc
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (f * 45)));
        g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

        // Brighter rim at the destruction edge
        g2.setStroke(new BasicStroke((float) (2 + 3 * f)));
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (f * 200)));
        g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

        // Inner white flash early on
        g2.setColor(new Color(255, 255, 255, (int) (f * f * 120)));
        g2.fill(new Ellipse2D.Double(x - r * 0.35, y - r * 0.35, r * 0.7, r * 0.7));

        g2.dispose();
    }
}
