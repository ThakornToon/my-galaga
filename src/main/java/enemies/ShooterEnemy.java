package enemies;

import core.World;
import entities.GhostShip;
import entities.Player;
import patterns.AimedPattern;

import java.awt.*;

public class ShooterEnemy extends Enemy {

    public enum Mode { IDLE, CAPTURE_BEAM, CAPTURE_BOB, BOUNCER }
    private Mode mode = Mode.IDLE;

    private GhostShip ghost = null;
    private static final double GHOST_TRAIL_DIST  = 36;

    // Tractor beam
    private static final double BEAM_GROW_SPEED = 320;  // px/sec
    private static final double BEAM_TIMEOUT    = 4.0;  // cancel beam after this long
    private double beamLength = 0;
    private double beamTimer  = 0;

    private double bounceShotTimer = 0;
    private static final double BOUNCE_SHOT_INTERVAL = 5.0;
    private static final double BOUNCE_SPEED_X       = 140;

    public ShooterEnemy(World world, double x, double y) {
        super(world, x, y, 34, 30, 1, 250, 99,
                new Color(255, 50, 100), new Color(180, 0, 60), new Color(255, 150, 180));
    }

    @Override
    public void triggerDive() {
        if (inEntryPath) return;
        Player p = findPlayer();
        boolean playerAlive = (p != null && p.getLives() > 0);
        if (world.rng().nextDouble() < 0.5 && playerAlive) {
            // Stay in place — fire tractor beam downward toward player
            inFormation = false;
            divePhase   = 0;
            mode        = Mode.CAPTURE_BEAM;
            beamLength  = 0;
            beamTimer   = 0;
            vx = 0;
            vy = 0;
        } else {
            super.triggerDive();
            startBouncer();
        }
    }

    private void startBouncer() {
        mode            = Mode.BOUNCER;
        vx              = BOUNCE_SPEED_X * speedMult * (world.rng().nextBoolean() ? 1 : -1);
        bounceShotTimer = 0;
        inFormation     = false;
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }

        switch (mode) {
            case IDLE         -> moveTowardsFormation(0, 0);
            case CAPTURE_BEAM -> handleCaptureBeam(dt);
            case CAPTURE_BOB  -> handleCaptureBob();
            case BOUNCER      -> handleBouncer(dt);
        }

        // Tow ghost behind us while alive. The ghost is in the world's object
        // list, so the game loop already calls ghost.update(dt) — we only steer
        // its position here (calling update() again would double its blink rate).
        if (ghost != null && ghost.isAlive()) {
            ghost.setPosition(x, y - GHOST_TRAIL_DIST);
        } else if (ghost != null) {
            ghost = null;
        }
    }

    private void handleCaptureBeam(double dt) {
        vx = 0; vy = 0;   // hover in place

        beamTimer += dt;
        Player p = findPlayer();
        if (p == null || beamTimer >= BEAM_TIMEOUT) {
            startSmoothReturn();
            mode = Mode.IDLE;
            return;
        }

        // Grow beam toward current player position
        double dx   = p.getX() - x;
        double dy   = p.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
        beamLength += BEAM_GROW_SPEED * dt;

        if (beamLength >= dist) {
            // Only steal a life when the player has more than one left — taking
            // their last life this way would be an instant kill, so the beam
            // gives up and returns instead.
            if (p.getLives() <= 1) {
                startSmoothReturn();
                mode = Mode.IDLE;
                return;
            }
            // Beam reached player — capture!
            world.spawnParticles(p.getX(), p.getY(),
                    new Color(255, 220, 50), 20, 60, 220, 0.4, 1.0, 3, 7);
            world.spawnParticles(p.getX(), p.getY(),
                    new Color(255, 255, 200), 8, 100, 260, 0.2, 0.6, 2, 4);
            p.absorbHit();
            ghost      = new GhostShip(world, x, y - GHOST_TRAIL_DIST);
            world.add(ghost);
            mode        = Mode.CAPTURE_BOB;
            inFormation = true;
            beamLength  = 0;
        }
    }

    private void handleCaptureBob() {
        moveTowardsFormation(Math.sin(patternTimer * 1.4) * 45 * speedMult, 0);
    }

    private void handleBouncer(double dt) {
        x += vx * dt;
        if (x <= w/2)               { x = w/2;               vx =  Math.abs(vx); }
        if (x >= world.width - w/2) { x = world.width - w/2;  vx = -Math.abs(vx); }
        vy = Math.sin(patternTimer * 0.8) * 40 * speedMult;
        y += vy * dt;
        y  = Math.max(50, Math.min(world.height * 0.55, y));
        vx = (vx > 0 ? 1 : -1) * BOUNCE_SPEED_X * speedMult;
        bounceShotTimer += dt;
        if (bounceShotTimer >= BOUNCE_SHOT_INTERVAL) {
            bounceShotTimer = 0;
            firePattern(new AimedPattern(280 + wave * 8));
        }
    }

    @Override
    public void absorbHit(int damage) {
        if (invulnerable) return;
        if (ghost != null && ghost.isAlive()) {
            ghost.destroy(); ghost = null;
            Player p = findPlayer();
            if (p != null) { p.gainLife(); p.awardDualFighter(); }
        }
        super.absorbHit(damage);
    }

    public void ghostHit() { if (ghost != null) { ghost.destroy(); ghost = null; } }
    public GhostShip getGhost() { return ghost; }

    @Override protected void updateShooting(double dt) {}

    @Override
    public void draw(Graphics2D g2) {
        // Tractor beam — drawn first (behind the ship body)
        if (mode == Mode.CAPTURE_BEAM && beamLength > 0) {
            Player target = findPlayer();
            if (target != null) {
                double dx   = target.getX() - x;
                double dy   = target.getY() - y;
                double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
                double nx   = dx / dist;
                double ny   = dy / dist;
                double draw = Math.min(beamLength, dist);

                // Animated dotted beam
                int steps = Math.max(1, (int)(draw / 10));
                for (int i = 0; i <= steps; i++) {
                    double t     = i / (double) steps;
                    double bx    = x + nx * draw * t;
                    double by    = y + ny * draw * t;
                    double phase = ((patternTimer * 10) - i * 0.5) % 1.0;
                    int    a     = (int)(Math.max(0, Math.sin(phase * Math.PI)) * 230);
                    int    r     = (i % 3 == 0) ? 5 : 3;
                    g2.setColor(new Color(255, 220, 50, a));
                    g2.fillOval((int)(bx - r), (int)(by - r), r * 2, r * 2);
                }
                // Glowing nozzle at base of beam
                float pulse = (float)(0.5 + 0.5 * Math.sin(patternTimer * 15));
                g2.setColor(new Color(255, 240, 100, (int)(pulse * 255)));
                g2.fillOval((int)(x - 6), (int)(y + h / 2 - 5), 12, 12);
                // Charging ring around ship
                g2.setColor(new Color(255, 180, 50, (int)(pulse * 160)));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval((int)(x - w/2 - 10), (int)(y - h/2 - 10),
                        (int)(w + 20), (int)(h + 20));
            }
        }

        // Ship body
        drawGlow(g2);
        g2.setColor(colorSecondary);
        g2.fillRect((int)(x - w/2), (int)(y - h/6), (int)w,     (int)(h / 3));
        g2.fillRect((int)(x - w/6), (int)(y - h/2), (int)(w/3), (int)h);
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect((int)(x - w/2), (int)(y - h/6), (int)w,     (int)(h / 3));
        g2.drawRect((int)(x - w/6), (int)(y - h/2), (int)(w/3), (int)h);
        g2.setColor(colorAccent);
        g2.fillOval((int)(x - 6), (int)(y - 6), 12, 12);
    }
}
