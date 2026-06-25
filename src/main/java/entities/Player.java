package entities;

import core.World;
import core.SoundManager;
import patterns.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player extends GameObject {
    private int lives = 3;
    private boolean movingLeft, movingRight, shooting;
    private final double speed = 280;

    private double shootCooldown = 0;
    private double invulnerableTimer = 0;
    private boolean invulnerable = false;

    public static final double DOUBLE_DURATION     = 12;
    public static final double SPREAD_DURATION     = 10;
    public static final double RAPID_DURATION      = 8;
    public static final double SHIELD_DURATION     = 15;
    public static final double SCORE_MULT_DURATION = 10;

    // Shot-type and rapid are tracked independently so they can stack
    public enum PowerUpState { NONE, DOUBLE, SPREAD }
    private PowerUpState powerUp = PowerUpState.NONE;
    private double powerUpTimer = 0;
    private double rapidTimer   = 0;

    // Each collected shield gets its own timer; the one with least time absorbs the next hit
    private final List<Double> shieldTimers = new ArrayList<>();
    private double scoreMultTimer = 0;

    // ── Special (explosive) ammo, earned by defeating bosses ─────────────────
    private int specialAmmo = 0;
    // Set from the EDT on a Shift press; consumed on the game thread in update()
    // so world lists are only ever mutated by the loop thread.
    private volatile boolean specialFirePending = false;

    // ── Dual fighters ────────────────────────────────────────────────────────
    private final List<DualFighter> dualFighters = new ArrayList<>();

    private static final Color COL_BODY   = new Color(0, 200, 255);
    private static final Color COL_WING   = new Color(0, 100, 200);
    private static final Color COL_ACCENT = new Color(0, 255, 255);

    public Player(World world) {
        super(world, world.width / 2.0, world.height - 80, 36, 32);
    }

    public void setMoveLeft(boolean v)  { movingLeft  = v; }
    public void setMoveRight(boolean v) { movingRight = v; }
    public void setShooting(boolean v)  { shooting    = v; }

    public int getLives()               { return lives;                            }
    public PowerUpState getPowerUp()    { return powerUp;                          }
    public double getPowerUpTimer()     { return powerUpTimer;                     }
    public boolean isRapid()            { return rapidTimer > 0;                   }
    public double getRapidTimer()       { return rapidTimer;                       }
    public boolean hasShield()          { return !shieldTimers.isEmpty();          }
    public int getShieldCount()         { return shieldTimers.size();              }
    public double getShieldTimer()      { return hasShield() ? Collections.min(shieldTimers) : 0; }
    public double getScoreMultTimer()   { return scoreMultTimer;                   }
    public boolean isInvulnerable()     { return invulnerable;                     }
    public int getSpecialAmmo()         { return specialAmmo;                      }

    /** Award special explosive rounds (called when a boss is defeated). */
    public void addSpecialAmmo(int n)   { specialAmmo += n; }
    /** Request a special shot (Shift). Consumed next frame on the game thread. */
    public void requestSpecialFire()    { specialFirePending = true; }

    /** Restore 1 life (e.g. after killing a capturing ShooterEnemy) */
    public void gainLife() {
        lives++;
    }

    /** Add a DualFighter alongside the player */
    public void awardDualFighter() {
        // Max 2 dual fighters (one on each side)
        if (dualFighters.size() >= 2) return;
        // Place on the side that doesn't have one yet
        boolean hasLeft  = dualFighters.stream().anyMatch(d -> d.getOffsetX() < 0);
        boolean hasRight = dualFighters.stream().anyMatch(d -> d.getOffsetX() > 0);
        double offsetX   = (!hasLeft) ? -50 : 50;
        DualFighter df   = new DualFighter(world, offsetX);
        dualFighters.add(df);
        world.add(df);
    }

    public List<DualFighter> getDualFighters() { return dualFighters; }

    public void applyPowerUp(PowerUp.Type type) {
        switch (type) {
            case DOUBLE_SHOT -> { powerUp = PowerUpState.DOUBLE; powerUpTimer = DOUBLE_DURATION; }
            case SPREAD_SHOT -> { powerUp = PowerUpState.SPREAD; powerUpTimer = SPREAD_DURATION; }
            case RAPID_FIRE  -> { rapidTimer = RAPID_DURATION; }
            case SHIELD      -> {
                shieldTimers.add(SHIELD_DURATION);
                for (DualFighter df : dualFighters) df.applyShield(SHIELD_DURATION);
            }
            case SCORE_MULT  -> { world.scoreMultiplier = 2; scoreMultTimer = SCORE_MULT_DURATION; }
        }
    }

    public void absorbHit() {
        if (!shieldTimers.isEmpty()) {
            shieldTimers.remove(Collections.min(shieldTimers));
            return;
        }
        if (invulnerable) return;
        lives--;
        invulnerable      = true;
        invulnerableTimer = 2.0;
        world.spawnExplosion(x, y, COL_BODY);
        if (lives <= 0) {
            destroy();
            world.gameOver = true;
        }
    }

    @Override
    public void update(double dt) {
        vx = movingLeft ? -speed : movingRight ? speed : 0;
        applyVelocity(dt);
        x = Math.max(w/2, Math.min(world.width - w/2, x));

        if (invulnerable) {
            invulnerableTimer -= dt;
            if (invulnerableTimer <= 0) invulnerable = false;
        }
        if (powerUpTimer > 0) {
            powerUpTimer -= dt;
            if (powerUpTimer <= 0) powerUp = PowerUpState.NONE;
        }
        if (rapidTimer > 0) rapidTimer -= dt;
        shieldTimers.replaceAll(t -> t - dt);
        shieldTimers.removeIf(t -> t <= 0);
        if (scoreMultTimer > 0) {
            scoreMultTimer -= dt;
            if (scoreMultTimer <= 0) world.scoreMultiplier = 1;
        }
        if (shootCooldown > 0) shootCooldown -= dt;

        // Special explosive shot (Shift) — one per press, while ammo remains.
        if (specialFirePending) {
            specialFirePending = false;
            if (specialAmmo > 0) {
                specialAmmo--;
                double radius = world.width / 4.0;   // 1/4 of screen width
                world.add(Bullet.special(world, x, y - 20, -400, radius, 20));
            }
        }

        // Sync dual fighters and fire together
        dualFighters.removeIf(df -> !df.isAlive());
        for (DualFighter df : dualFighters) {
            df.syncPosition(x, y);
            if (shooting && shootCooldown <= 0) {
                df.tryShoot(powerUp);
            }
        }

        if (shooting && shootCooldown <= 0) {
            shootCooldown = isRapid() ? 0.1 : 0.22;
            SoundManager.playLaser();
            BulletPattern pattern = switch (powerUp) {
                case DOUBLE -> new DoubleStraightPattern(520, 10);
                case SPREAD -> new SpreadPattern(520, 3, 30);
                default     -> new StraightPattern(520);
            };
            for (Bullet b : pattern.createBullets(world, x, y, true))
                world.add(b);
        }
    }

    @Override
    public void draw(Graphics2D g) {
        if (invulnerable && (int)(invulnerableTimer * 10) % 2 == 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Engine exhaust glow
        float[] frac  = {0f, 1f};
        Color[] gcols = {new Color(0, 150, 255, 200), new Color(0, 150, 255, 0)};
        g2.setPaint(new LinearGradientPaint(
                (float)x, (float)(y + h/2),
                (float)x, (float)(y + h/2 + 22), frac, gcols));
        g2.fillOval((int)(x - 6), (int)(y + h/2 - 4), 12, 24);

        // Body
        int[] bx = {(int)x, (int)(x - w/2), (int)(x - w/3), (int)(x + w/3), (int)(x + w/2)};
        int[] by = {(int)(y - h/2), (int)(y + h/2), (int)(y + h/4), (int)(y + h/4), (int)(y + h/2)};
        g2.setColor(COL_WING);
        g2.fillPolygon(bx, by, 5);
        g2.setColor(COL_BODY);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(bx, by, 5);

        // Cockpit
        g2.setColor(new Color(0, 255, 255, 180));
        g2.fillOval((int)(x - 7), (int)(y - h/2 + 4), 14, 12);
        g2.setColor(COL_ACCENT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval((int)(x - 7), (int)(y - h/2 + 4), 14, 12);

        drawWing(g2, -1);
        drawWing(g2,  1);

        // Shield
        if (hasShield()) {
            g2.setColor(new Color(255, 220, 0, 70));
            g2.fillOval((int)(x - w/2 - 8), (int)(y - h/2 - 8),
                    (int)(w + 16), (int)(h + 16));
            g2.setColor(new Color(255, 220, 0, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int)(x - w/2 - 8), (int)(y - h/2 - 8),
                    (int)(w + 16), (int)(h + 16));
        }
        g2.dispose();
    }

    private void drawWing(Graphics2D g2, int side) {
        int[] wx = {(int)(x + side * w/3),
                (int)(x + side * (w/2 + 6)),
                (int)(x + side * w/3)};
        int[] wy = {(int)(y - h/8),
                (int)(y + h/2 - 4),
                (int)(y + h/4)};
        g2.setColor(COL_BODY.darker());
        g2.fillPolygon(wx, wy, 3);
    }
}