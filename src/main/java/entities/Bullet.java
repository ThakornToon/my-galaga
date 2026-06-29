package entities;

import core.World;

import java.awt.*;

public class Bullet extends GameObject {
    private final boolean fromPlayer;
    private final int damage;
    private final Color colorPrimary, colorAccent;

    // ── Special (explosive) bullet ───────────────────────────────────────────
    // A special bullet detonates on its first enemy contact, dealing aoeDamage
    // to every enemy within aoeRadius (handled in CollisionManager).
    private final boolean special;
    private final double  aoeRadius;
    private final int     aoeDamage;
    private double anim = 0;

    public Bullet(World world, double x, double y, double vx, double vy, boolean fromPlayer, int damage) {
        this(world, x, y, vx, vy, fromPlayer, damage, false, 0, 0);
    }

    private Bullet(World world, double x, double y, double vx, double vy,
                   boolean fromPlayer, int damage,
                   boolean special, double aoeRadius, int aoeDamage) {
        super(world, x, y, special ? 48 : (fromPlayer ? 4 : 5), special ? 48 : (fromPlayer ? 10 : 12));
        this.vx = vx;
        this.vy = vy;
        this.fromPlayer = fromPlayer;
        this.damage = damage;
        this.special = special;
        this.aoeRadius = aoeRadius;
        this.aoeDamage = aoeDamage;
        this.colorPrimary = fromPlayer ? new Color(100, 255, 180) : new Color(255, 80, 80);
        this.colorAccent = fromPlayer ? new Color(0, 200, 120) : new Color(200, 30, 30);
    }

    /** Build a player special (explosive) bullet rising at the given speed. */
    public static Bullet special(World world, double x, double y, double vy,
                                 double aoeRadius, int aoeDamage) {
        return new Bullet(world, x, y, 0, vy, true, aoeDamage, true, aoeRadius, aoeDamage);
    }

    public boolean isFromPlayer() { return fromPlayer; }
    public int getDamage() { return damage; }
    public boolean isSpecial()   { return special; }
    public double getAoeRadius() { return aoeRadius; }
    public int    getAoeDamage() { return aoeDamage; }

    @Override
    public void update(double dt) {
        anim += dt;
        applyVelocity(dt);
        if (y < -20 || y > world.height + 20 || x < -20 || x > world.width + 20) destroy();
    }

    @Override
    public void draw(Graphics2D g2) {
        if (special) {
            // Sprite scaled ×3 to match the enlarged 48px hitbox.
            double pulse = 1 + 0.25 * Math.sin(anim * 18);
            int r = (int) (33 * pulse);
            g2.setColor(new Color(120, 240, 255, 60));
            g2.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
            g2.setColor(new Color(180, 250, 255, 130));
            g2.fillOval((int) (x - 21), (int) (y - 21), 42, 42);
            g2.setColor(Color.WHITE);
            g2.fillOval((int) (x - 9), (int) (y - 9), 18, 18);
            return;
        }
        g2.setColor(new Color(colorPrimary.getRed(), colorPrimary.getGreen(), colorPrimary.getBlue(), 70));
        g2.fillOval((int) (x - 5), (int) (y - 7), 10, 14);
        g2.setColor(colorPrimary);
        g2.fillOval((int) (x - 2), (int) (y - 5), 4, 10);
        g2.setColor(colorAccent);
        g2.fillOval((int) (x - 1), (int) (y - 3), 2, 6);
    }
}