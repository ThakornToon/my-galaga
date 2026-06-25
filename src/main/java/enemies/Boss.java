package enemies;

import core.World;
import entities.Player;
import patterns.CircularBurstPattern;
import patterns.AimedPattern;

import java.awt.*;

/**
 * Boss – firing interval reworked:
 *   Phase 0 = 8s, Phase 1 = 7s, Phase 2 = 6s  (base for first spawn)
 *   When HP < 50%: each phase cooldown −1s (min 2s)
 *   Each new Boss spawn: all phase cooldowns −1s (min 2s)
 *   Speed: +5% per 5 waves.
 */
public class Boss extends Enemy {
    private int phase = 0;
    private static final int   BASE_HP        = 60;    // HP at the first boss (wave 5)
    private static final double HP_GROWTH_TIER = 0.15;  // +15% compounding per 10-wave tier

    // Per-spawn base cooldowns (seconds); decrease each respawn, floor at 2s
    private double[] phaseCooldown;
    // Accumulated spawn count tracked via World
    private static int spawnCount = 0;

    public Boss(World world) {
        super(world, world.width / 2.0, -80, 70, 60, BASE_HP, 5000, 99,
                new Color(255, 80, 0), new Color(180, 30, 0), new Color(255, 220, 0));
        spawnCount++;
        // Base: 8, 7, 6 – reduce by (spawnCount-1) per phase, floor 2
        int reduction = spawnCount - 1;
        phaseCooldown = new double[]{
                Math.max(2, 8 - reduction),
                Math.max(2, 7 - reduction),
                Math.max(2, 6 - reduction)
        };
        this.vy = 60;
    }

    /** Reset spawn counter when starting a new game */
    public static void resetSpawnCount() { spawnCount = 0; }

    /**
     * Boss HP scales differently from regular enemies: instead of a flat +1 per
     * 10 waves, it grows by +15% COMPOUNDING per 10-wave tier off its wave-5
     * baseline (wave5 ×1.0 = 60, wave10/15 ×1.15 ≈ 69, wave20/25 ×1.32 ≈ 79…).
     * We override setWave so the generic per-enemy bonus does NOT also apply.
     */
    @Override
    public void setWave(int w) {
        this.wave = w;
        int tier  = w / 10;
        maxHp = (int) Math.round(BASE_HP * Math.pow(1.0 + HP_GROWTH_TIER, tier));
        hp    = maxHp;
    }

    /** On death, reward the player with +1 life and special (explosive) ammo:
     *  usually 1 round, occasionally 2. */
    @Override
    protected void onDeath() {
        Player p = findPlayer();
        if (p != null) {
            p.gainLife();   // killing the boss restores 1 life
            p.addSpecialAmmo(world.rng().nextDouble() < 0.90 ? 1 : 2);
        }
    }

    @Override
    protected void updateAI(double dt) {
        rotation += dt * 30;
        // Phase thresholds as fractions of maxHp so they track scaled HP
        // (was absolute 40/20, tuned only to the original 60-HP boss).
        int newPhase = hp > maxHp * 2 / 3 ? 0 : hp > maxHp / 3 ? 1 : 2;
        if (newPhase != phase) {
            phase = newPhase;
            startInvulnerability(1.2);
        }
        double spd = speedMult;
        switch (phase) {
            case 0 -> {
                vx = Math.sin(patternTimer * 0.8) * 120 * spd;
                vy = Math.sin(patternTimer * 0.3) * 40  * spd;
            }
            case 1 -> {
                vx = Math.sin(patternTimer * 1.2) * 150 * spd;
                vy = Math.sin(patternTimer * 0.5) * 60  * spd;
            }
            case 2 -> {
                vx = Math.sin(patternTimer * 1.8) * 180 * spd;
                vy = Math.sin(patternTimer * 0.8) * 80  * spd;
            }
        }
        x = Math.max(50, Math.min(world.width - 50, x));
        y = Math.max(60, Math.min(200, y));
    }

    @Override
    protected void updateShooting(double dt) {
        shootTimer += dt;

        // Effective cooldown: base − 1 if HP < 50%
        double hpFrac    = (double)hp / maxHp;
        double effective = phaseCooldown[phase] - (hpFrac < 0.5 ? 1.0 : 0.0);
        effective        = Math.max(2.0, effective);

        if (shootTimer >= effective) {
            shootTimer = 0;
            switch (phase) {
                case 0 -> firePattern(new CircularBurstPattern(200, 6, patternTimer));
                case 1 -> {
                    firePattern(new AimedPattern(300));
                    firePattern(new CircularBurstPattern(180, 4, patternTimer));
                }
                case 2 -> {
                    firePattern(new CircularBurstPattern(220, 8, patternTimer * 2));
                    if (world.rng().nextBoolean())
                        firePattern(new AimedPattern(320));
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(colorPrimary.getRed(), colorPrimary.getGreen(),
                colorPrimary.getBlue(), 60));
        g2.fillOval((int)(x - w/2 - 12), (int)(y - h/2 - 12),
                (int)(w + 24), (int)(h + 24));
        double rot  = Math.toRadians(rotation);
        int sides   = 6;
        int[] bx    = new int[sides];
        int[] by    = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = rot + i * Math.PI * 2 / sides;
            bx[i] = (int)(x + Math.cos(a) * w/2);
            by[i] = (int)(y + Math.sin(a) * h/2);
        }
        g2.setColor(colorSecondary);
        g2.fillPolygon(bx, by, sides);
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(3f));
        g2.drawPolygon(bx, by, sides);
        g2.setColor(colorAccent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x - w/4), (int)(y - h/4), (int)(w/2), (int)(h/2));
        Color coreColor = switch (phase) {
            case 0 -> new Color(255, 200, 50);
            case 1 -> new Color(255, 100, 50);
            default -> new Color(255, 50, 50);
        };
        g2.setColor(coreColor);
        g2.fillOval((int)(x - 12), (int)(y - 12), 24, 24);
        double hpFrac = (double)hp / maxHp;
        g2.setColor(new Color(40, 10, 10, 200));
        g2.fillRoundRect((int)(x - w/2), (int)(y + h/2 + 8), (int)w, 10, 4, 4);
        Color hpCol = hpFrac > 0.5 ? new Color(50, 220, 50)
                : hpFrac > 0.25 ? new Color(220, 180, 0)
                : new Color(220, 50, 50);
        g2.setColor(hpCol);
        g2.fillRoundRect((int)(x - w/2), (int)(y + h/2 + 8),
                (int)(w * hpFrac), 10, 4, 4);
        g2.setColor(hpCol.brighter());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect((int)(x - w/2), (int)(y + h/2 + 8), (int)w, 10, 4, 4);
        g2.dispose();
    }
}