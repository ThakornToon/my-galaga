package entities;

import core.World;
import patterns.*;
import java.awt.*;

/**
 * Bonus fighter that joins the player after rescuing the ghost ship.
 * Extends GameObject so world.add/allOf work normally.
 *
 * - Flies alongside the player at a fixed horizontal offset.
 * - Fires when the player fires (called by Player.update).
 * - 2 HP; shield absorbs 1 hit before HP counts.
 * - Tinted yellow-green to distinguish from main ship.
 */
public class DualFighter extends GameObject {

    private final double offsetX;   // horizontal offset from player centre
    private int hp = 2;
    private boolean hasShield = false;
    private double shieldTimer = 0;

    private static final double FW = 32, FH = 28;
    private static final Color COLOR_BODY        = new Color(100, 255, 120);
    private static final Color COLOR_WING        = new Color(40,  160,  60);
    private static final Color COLOR_ACCENT      = new Color(180, 255, 180);
    private static final Color COLOR_BODY_DMG    = new Color(255,  80,  80);
    private static final Color COLOR_WING_DMG    = new Color(160,  30,  30);
    private static final Color COLOR_ACCENT_DMG  = new Color(255, 180, 180);

    public DualFighter(World world, double offsetX) {
        super(world, world.width / 2.0 + offsetX, world.height - 80, FW, FH);
        this.offsetX = offsetX;
    }

    public double getOffsetX() { return offsetX; }

    public void applyShield(double duration) {
        hasShield   = true;
        shieldTimer = duration;
    }

    public void absorbHit() {
        if (hasShield) {
            hasShield   = false;
            shieldTimer = 0;
            return;
        }
        hp--;
        if (hp <= 0) destroy();
    }

    /** Called every frame from Player to keep position in sync */
    public void syncPosition(double playerX, double playerY) {
        this.x = Math.max(FW/2,
                Math.min(world.width - FW/2, playerX + offsetX));
        this.y = playerY;
    }

    /** Called when player fires — mirrors the player's current shot-type pattern */
    public void tryShoot(Player.PowerUpState powerUp) {
        BulletPattern pattern = switch (powerUp) {
            case DOUBLE -> new DoubleStraightPattern(520, 10);
            case SPREAD -> new SpreadPattern(520, 3, 30);
            default     -> new StraightPattern(520);
        };
        for (Bullet b : pattern.createBullets(world, x, y, true))
            world.add(b);
    }

    @Override
    public void update(double dt) {
        if (shieldTimer > 0) {
            shieldTimer -= dt;
            if (shieldTimer <= 0) hasShield = false;
        }
    }

    @Override
    public void draw(Graphics2D g2) {
        if (!alive) return;

        Color cBody   = (hp <= 1) ? COLOR_BODY_DMG   : COLOR_BODY;
        Color cWing   = (hp <= 1) ? COLOR_WING_DMG   : COLOR_WING;
        Color cAccent = (hp <= 1) ? COLOR_ACCENT_DMG : COLOR_ACCENT;

        // Engine glow
        float[] frac  = {0f, 1f};
        Color glowOn  = (hp <= 1) ? new Color(200, 50, 50, 180) : new Color(50, 200, 80, 180);
        Color glowOff = (hp <= 1) ? new Color(200, 50, 50, 0)   : new Color(50, 200, 80, 0);
        g2.setPaint(new LinearGradientPaint(
                (float)x, (float)(y + FH/2),
                (float)x, (float)(y + FH/2 + 20), frac, new Color[]{glowOn, glowOff}));
        g2.fillOval((int)(x - 5), (int)(y + FH/2 - 3), 10, 20);

        // Body
        int[] bx = {(int)x, (int)(x - FW/2), (int)(x - FW/3),
                (int)(x + FW/3), (int)(x + FW/2)};
        int[] by = {(int)(y - FH/2), (int)(y + FH/2), (int)(y + FH/4),
                (int)(y + FH/4), (int)(y + FH/2)};
        g2.setColor(cWing);
        g2.fillPolygon(bx, by, 5);
        g2.setColor(cBody);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(bx, by, 5);

        // Cockpit
        g2.setColor(new Color(cAccent.getRed(), cAccent.getGreen(), cAccent.getBlue(), 180));
        g2.fillOval((int)(x - 6), (int)(y - FH/2 + 4), 12, 10);
        g2.setColor(cAccent);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval((int)(x - 6), (int)(y - FH/2 + 4), 12, 10);

        // HP pips (small dots below cockpit)
        for (int i = 0; i < hp; i++) {
            g2.setColor(cBody);
            g2.fillOval((int)(x - 5 + i * 6), (int)(y + FH/2 - 8), 4, 4);
        }

        // Shield
        if (hasShield) {
            g2.setColor(new Color(255, 220, 0, 70));
            g2.fillOval((int)(x - FW/2 - 8), (int)(y - FH/2 - 8),
                    (int)(FW + 16), (int)(FH + 16));
            g2.setColor(new Color(255, 220, 0, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int)(x - FW/2 - 8), (int)(y - FH/2 - 8),
                    (int)(FW + 16), (int)(FH + 16));
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        world.spawnExplosion(x, y, hp <= 1 ? COLOR_BODY_DMG : COLOR_BODY);
    }
}
