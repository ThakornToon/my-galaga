package enemies;

import core.World;
import entities.GameObject;
import entities.Player;
import entities.Bullet;
import patterns.BulletPattern;
import patterns.StraightPattern;

import java.awt.*;
import java.util.List;

public abstract class Enemy extends GameObject {
    protected final Color colorPrimary, colorSecondary, colorAccent;
    protected int hp, maxHp, scoreValue;
    protected double formationX, formationY;
    protected boolean inFormation = true;
    protected double diveTimer, divePhase;
    protected double patternTimer;
    protected double shootTimer, shootCooldown;
    protected boolean invulnerable;
    protected double invulnerableTimer;
    protected int wave;
    protected double speedMult = 1.0;

    // ── Path travel speeds (pixels / second) ────────────────────────────────
    // Paths are walked by real arc-length distance and interpolated between
    // waypoints, so movement is frame-rate independent and the pixel speed is
    // constant — both within a path and across paths of different lengths.
    protected static final double ENTRY_SPEED  = 400.0;
    protected static final double SWOOP_SPEED  = 460.0;
    protected static final double RETURN_SPEED = 380.0;

    // ── Entry path (follow-the-leader) ──────────────────────────────────────
    protected boolean inEntryPath = true;
    protected EntryPath entryPath = null;
    // progress along entryPath as an arc-length fraction in [0,1]
    protected double pathT = 0.0;

    // ── Swoop state (post-entry) ─────────────────────────────────────────────
    protected boolean willSwoop   = false;
    protected boolean swoopActive = false;
    protected EntryPath swoopPath = null;
    protected double swoopT        = 0.0;
    protected double swoopTargetX, swoopTargetY;

    // ── Smooth return to formation ───────────────────────────────────────────
    protected boolean returningToFormation = false;
    protected EntryPath returnPath = null;
    protected double returnT        = 0.0;

    protected Enemy(World world, double x, double y, double w, double h,
                    int hp, int scoreValue, double shootCooldown,
                    Color primary, Color secondary, Color accent) {
        super(world, x, y, w, h);
        this.hp           = hp;
        this.maxHp        = hp;
        this.scoreValue   = scoreValue;
        this.shootCooldown = shootCooldown;
        this.colorPrimary  = primary;
        this.colorSecondary = secondary;
        this.colorAccent   = accent;
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setFormationTarget(double fx, double fy) { formationX = fx; formationY = fy; }
    public double getFormationX()  { return formationX; }
    public double getFormationY()  { return formationY; }
    public void setWave(int w)     {
        this.wave = w;
        // Difficulty scaling: every full 10 waves, every enemy gains +1 HP.
        int bonus = w / 10;
        if (bonus > 0) { hp += bonus; maxHp += bonus; }
    }
    public boolean isInFormation() { return inFormation; }
    public boolean isInEntryPath() { return inEntryPath; }
    public int  getScoreValue()    { return scoreValue; }
    public void setSpeedMult(double m) { speedMult = m; }

    // ── Entry path setup ─────────────────────────────────────────────────────
    /**
     * Assign a shared EntryPath and this enemy's starting index in it.
     * startIndex lets each member of the convoy be offset (follow-the-leader).
     */
    public void setupEntryPath(EntryPath path, int startIndex,
                               boolean willSwoop,
                               double swoopX, double swoopY) {
        this.entryPath  = path;
        this.inEntryPath = true;
        this.willSwoop   = willSwoop;
        this.swoopTargetX = swoopX;
        this.swoopTargetY = swoopY;
        // Convert the follow-the-leader start index into an arc-length fraction
        // so trailing members keep a constant pixel gap behind the leader.
        this.pathT = path.fractionAtIndex(startIndex);
        if (!path.points.isEmpty()) {
            EntryPath.Pt p0 = path.atFraction(pathT);
            x = p0.x();
            y = p0.y();
        }
    }

    // ── Dive ─────────────────────────────────────────────────────────────────
    public void triggerDive() {
        if (inEntryPath) return;
        inFormation = false;
        diveTimer   = 0;
        divePhase   = 0;
    }

    // ── Smooth return (called by subclass when dive ends) ────────────────────
    protected void startSmoothReturn() {
        returningToFormation = true;
        inFormation          = false;
        // Build a short Bézier from current position back to formation
        double tx  = formationX + world.getFormationOffset();
        double ty  = formationY;
        // Control points: curve up then drop into slot
        double cp1x = x + (tx - x) * 0.3;
        double cp1y = y - 80;
        double cp2x = tx;
        double cp2y = ty - 60;
        int    N    = 50;
        java.util.List<EntryPath.Pt> pts = new java.util.ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            double t  = i / (double)(N - 1);
            double u  = 1 - t;
            double px = u*u*u*x   + 3*u*u*t*cp1x + 3*u*t*t*cp2x + t*t*t*tx;
            double py = u*u*u*y   + 3*u*u*t*cp1y + 3*u*t*t*cp2y + t*t*t*ty;
            pts.add(new EntryPath.Pt(px, py));
        }
        returnPath  = new EntryPath(pts, 1);
        returnT     = 0.0;
    }

    // ── Damage ───────────────────────────────────────────────────────────────
    public void absorbHit(int damage) {
        if (invulnerable) return;
        hp -= damage;
        if (hp <= 0) {
            onDeath();
            world.spawnExplosion(x, y, colorPrimary);
            world.score += scoreValue * world.scoreMultiplier;
            world.onEnemyKilled();
            world.maybeDropPowerUp(x, y);
            destroy();
        } else {
            world.spawnHitSparks(x, y, colorPrimary);
        }
    }

    protected void onDeath() {}

    protected void startInvulnerability(double d) { invulnerable = true; invulnerableTimer = d; }
    protected void tickInvulnerability(double dt) {
        if (invulnerable) { invulnerableTimer -= dt; if (invulnerableTimer <= 0) invulnerable = false; }
    }

    // ── Formation snap ───────────────────────────────────────────────────────
    protected void moveTowardsFormation(double dt, double extraVx, double extraVy) {
        double tx = formationX + world.getFormationOffset();
        double ty = formationY;
        vx = (tx - x) * 5 + extraVx;
        vy = (ty - y) * 5 + extraVy;
    }

    // ── Bullet helpers ───────────────────────────────────────────────────────
    protected void firePattern(BulletPattern p) {
        for (Bullet b : p.createBullets(world, x, y, false)) world.add(b);
    }
    protected Player findPlayer() {
        List<Player> ps = world.allOf(Player.class);
        return ps.isEmpty() ? null : ps.get(0);
    }
    protected void drawGlow(Graphics2D g2) {
        g2.setColor(new Color(colorPrimary.getRed(), colorPrimary.getGreen(),
                colorPrimary.getBlue(), 40));
        g2.fillOval((int)(x-w/2-4),(int)(y-h/2-4),(int)(w+8),(int)(h+8));
    }

    // ── Main update ──────────────────────────────────────────────────────────
    @Override
    public void update(double dt) {
        patternTimer += dt;
        tickInvulnerability(dt);

        if (inEntryPath) {
            updateEntryFollow(dt);
            return;
        }

        if (returningToFormation) {
            updateSmoothReturn(dt);
            return;
        }

        divePhase += dt;
        if (!inFormation) diveTimer += dt;
        updateAI(dt);
        applyVelocity(dt);
        updateShooting(dt);
    }

    // ── Follow-the-leader path traversal ────────────────────────────────────
    private void updateEntryFollow(double dt) {
        if (entryPath == null || entryPath.points.isEmpty()) {
            inEntryPath = false; inFormation = true; return;
        }
        // Advance along the path by real pixel distance, then interpolate.
        double len = entryPath.length();
        pathT += (len > 1e-6) ? (ENTRY_SPEED * speedMult * dt) / len : 1.0;
        pathT  = Math.min(pathT, 1.0);

        EntryPath.Pt pt = entryPath.atFraction(pathT);
        x = pt.x();
        y = pt.y();
        vx = 0; vy = 0;

        // Reached end of path
        if (pathT >= 1.0) {
            inEntryPath = false;
            inFormation = true;
            // No hard snap: the path already ends on the slot, and
            // moveTowardsFormation eases the enemy into its (swaying) slot —
            // teleporting by the current formation offset looked jarring.

            // 20% swoop immediately after arriving (if flagged)
            if (willSwoop) {
                willSwoop = false;
                // Build swoop path from current pos
                swoopPath = EntryPath.buildSwoop(x, y,
                        swoopTargetX, swoopTargetY,
                        formationX, formationY,
                        world.width, world.height);
                swoopActive = true;
                swoopT      = 0.0;
                inFormation = false;
            }
        }
    }

    // ── Post-entry swoop traversal ───────────────────────────────────────────
    protected void runSwoop(double dt) {
        if (!swoopActive || swoopPath == null) return;
        double len = swoopPath.length();
        swoopT += (len > 1e-6) ? (SWOOP_SPEED * speedMult * dt) / len : 1.0;
        swoopT  = Math.min(swoopT, 1.0);
        EntryPath.Pt pt = swoopPath.atFraction(swoopT);
        x = pt.x(); y = pt.y(); vx = 0; vy = 0;
        if (swoopT >= 1.0) {
            swoopActive = false;
            swoopT      = 0.0;
            swoopPath   = null;
            inFormation = true;
        }
    }

    // ── Smooth return traversal ──────────────────────────────────────────────
    private void updateSmoothReturn(double dt) {
        if (returnPath == null) { returningToFormation = false; inFormation = true; return; }
        double len = returnPath.length();
        returnT += (len > 1e-6) ? (RETURN_SPEED * speedMult * dt) / len : 1.0;
        returnT  = Math.min(returnT, 1.0);
        EntryPath.Pt pt = returnPath.atFraction(returnT);
        x = pt.x(); y = pt.y(); vx = 0; vy = 0;
        if (returnT >= 1.0) {
            returningToFormation = false;
            inFormation          = true;
            returnPath           = null;
            returnT              = 0.0;
            // Hand off to moveTowardsFormation (no teleport) for a smooth settle.
        }
    }

    // ── Subclass hooks ───────────────────────────────────────────────────────
    protected abstract void updateAI(double dt);

    protected void updateShooting(double dt) {
        shootTimer += dt;
        Player player = findPlayer();
        if (player == null || y >= world.height * 0.85) return;
        if (shootTimer >= shootCooldown) {
            shootTimer    = 0;
            shootCooldown = Math.max(0.5, shootCooldown - wave * 0.04);
            fireDefaultPattern(player);
        }
    }
    protected void fireDefaultPattern(Player player) {
        firePattern(new StraightPattern(260 + wave * 8));
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public Color getColorPrimary()   { return colorPrimary;   }
    public Color getColorSecondary() { return colorSecondary; }
    public Color getColorAccent()    { return colorAccent;    }
    public int   getHp()             { return hp;             }
    public int   getMaxHp()          { return maxHp;          }
}