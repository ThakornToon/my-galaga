package managers;

import core.World;
import core.Updatable;
import entities.*;
import enemies.Boss;
import enemies.Enemy;
import enemies.ShooterEnemy;

import java.awt.Color;
import java.util.List;

public class CollisionManager implements Updatable {
    private final World world;

    public CollisionManager(World world) { this.world = world; }

    @Override
    public void update(double dt) {
        List<Bullet>      bullets   = world.allOf(Bullet.class);
        List<Enemy>       enemies   = world.allOf(Enemy.class);
        List<Player>      players   = world.allOf(Player.class);
        List<PowerUp>     powerUps  = world.allOf(PowerUp.class);
        List<DualFighter> duals     = world.allOf(DualFighter.class);
        List<GhostShip>   ghosts    = world.allOf(GhostShip.class);

        // ── Player bullets → enemies ──────────────────────────────────────────
        for (Bullet b : bullets) {
            if (!b.isAlive() || !b.isFromPlayer()) continue;
            for (Enemy e : enemies) {
                if (!e.isAlive()) continue;
                if (b.getBounds().intersects(e.getBounds())) {
                    if (b.isSpecial()) {
                        detonateSpecial(b, enemies);
                    } else {
                        e.absorbHit(b.getDamage());
                        if (e instanceof Boss && !e.isAlive()) world.bossDefeated = true;
                    }
                    b.destroy();
                    break;
                }
            }
        }

        // ── Player bullets → ghost ships ─────────────────────────────────────
        // Ghost hit → ghost destroyed only (no life refund)
        for (Bullet b : bullets) {
            if (!b.isAlive() || !b.isFromPlayer()) continue;
            for (GhostShip gs : ghosts) {
                if (!gs.isAlive()) continue;
                if (b.getBounds().intersects(gs.getBounds())) {
                    // Notify owning ShooterEnemy so it nulls its reference
                    for (Enemy e : enemies) {
                        if (e instanceof ShooterEnemy se && se.getGhost() == gs) {
                            se.ghostHit();
                            break;
                        }
                    }
                    gs.destroy();
                    b.destroy();
                    break;
                }
            }
        }

        // ── Enemy bullets → player ────────────────────────────────────────────
        for (Bullet b : bullets) {
            if (!b.isAlive() || b.isFromPlayer()) continue;
            for (Player p : players) {
                if (!p.isAlive()) continue;
                if (b.getBounds().intersects(p.getBounds())) {
                    p.absorbHit();
                    b.destroy();
                    break;
                }
            }
        }

        // ── Enemy bullets → dual fighters ────────────────────────────────────
        for (Bullet b : bullets) {
            if (!b.isAlive() || b.isFromPlayer()) continue;
            for (DualFighter df : duals) {
                if (!df.isAlive()) continue;
                if (b.getBounds().intersects(df.getBounds())) {
                    df.absorbHit();
                    b.destroy();
                    break;
                }
            }
        }

        // ── Enemy body → player ───────────────────────────────────────────────
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            for (Player p : players) {
                if (!p.isAlive()) continue;
                if (e.getBounds().intersects(p.getBounds())) {
                    world.score += e.getScoreValue() / 2;
                    world.onEnemyKilled();
                    world.spawnExplosion(e.getX(), e.getY(), e.getColorPrimary());
                    e.destroy();
                    p.absorbHit();
                    break;
                }
            }
        }

        // ── Enemy body → dual fighters ────────────────────────────────────────
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            for (DualFighter df : duals) {
                if (!df.isAlive()) continue;
                if (e.getBounds().intersects(df.getBounds())) {
                    world.onEnemyKilled();
                    world.spawnExplosion(e.getX(), e.getY(), e.getColorPrimary());
                    e.destroy();
                    df.absorbHit();
                    break;
                }
            }
        }

        // ── Power-ups → player ────────────────────────────────────────────────
        // Use a slightly inflated player hitbox so near-misses still count — a
        // strict box-intersect made fast-passing pickups feel uncollectible.
        final double PICKUP_MARGIN = 7;
        for (PowerUp pu : powerUps) {
            if (!pu.isAlive()) continue;
            for (Player p : players) {
                if (!p.isAlive()) continue;
                java.awt.geom.Rectangle2D pb = p.getBounds();
                java.awt.geom.Rectangle2D grab = new java.awt.geom.Rectangle2D.Double(
                        pb.getX() - PICKUP_MARGIN, pb.getY() - PICKUP_MARGIN,
                        pb.getWidth() + 2 * PICKUP_MARGIN, pb.getHeight() + 2 * PICKUP_MARGIN);
                if (pu.getBounds().intersects(grab)) {
                    p.applyPowerUp(pu.getType());
                    pu.destroy();
                    break;
                }
            }
        }
        // ── Power-ups → dual fighters (apply to player so all share the buff) ──
        for (PowerUp pu : powerUps) {
            if (!pu.isAlive()) continue;
            for (DualFighter df : duals) {
                if (!df.isAlive()) continue;
                if (pu.getBounds().intersects(df.getBounds())) {
                    for (Player p : players) {
                        if (p.isAlive()) { p.applyPowerUp(pu.getType()); break; }
                    }
                    pu.destroy();
                    break;
                }
            }
        }
        // Note: EnergyWave handles player collision internally in its own update()
    }

    /**
     * Detonate a special bullet at its current position: every enemy whose
     * centre lies within the bullet's destruction radius takes the full AoE
     * damage (20 = twenty normal rounds). A boss caught and killed in the blast
     * still counts as defeated.
     */
    private void detonateSpecial(Bullet b, List<Enemy> enemies) {
        double bx = b.getX(), by = b.getY();
        double r  = b.getAoeRadius(), r2 = r * r;
        Color blastColor = new Color(150, 240, 255);
        world.spawnBigExplosion(bx, by, r, blastColor);
        world.add(new BlastRing(world, bx, by, r, blastColor));   // faint radius ring
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            double dx = e.getX() - bx, dy = e.getY() - by;
            if (dx * dx + dy * dy <= r2) {
                e.absorbHit(b.getAoeDamage());
                if (e instanceof Boss && !e.isAlive()) world.bossDefeated = true;
            }
        }
    }
}