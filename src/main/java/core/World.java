package core;

import entities.GameObject;
import entities.Particle;
import entities.PowerUp;

import java.awt.Color;
import java.util.*;

/**
 * World — simplified back to original structure.
 * EnergyWave, GhostShip, DualFighter all extend GameObject,
 * so world.add() / world.allOf() work for them as normal.
 */
public class World {
    public final int width, height;
    public int score           = 0;
    public int wave            = 1;
    public int kills           = 0;
    public int scoreMultiplier = 1;
    public boolean gameOver    = false;
    public boolean bossDefeated = false;
    public double totalTime    = 0;

    private double formationOffset = 0;
    // Plain ArrayLists: the game loop runs on a single thread (see GamePanel),
    // so it owns all mutation + iteration. CopyOnWriteArrayList copied the whole
    // backing array on every particle/object add, which collapsed the frame rate
    // (O(n) per add → O(n²) per frame) once explosions filled the particle list.
    private final List<GameObject> objects    = new ArrayList<>();
    private final List<GameObject> pendingAdd = new ArrayList<>();
    private final List<Particle>   particles  = new ArrayList<>();
    private final Random rng = new Random();

    public World(int w, int h) {
        this.width  = w;
        this.height = h;
    }

    public void setFormationOffset(double offset) { this.formationOffset = offset; }
    public double getFormationOffset()             { return formationOffset;        }

    public void add(GameObject go) { pendingAdd.add(go); }

    public void flush() {
        objects.addAll(pendingAdd);
        pendingAdd.clear();
        objects.removeIf(go -> !go.isAlive());
    }

    public List<GameObject> all() { return objects; }

    public <T extends GameObject> List<T> allOf(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (GameObject go : objects)
            if (type.isInstance(go) && go.isAlive()) result.add(type.cast(go));
        return result;
    }

    public void clear() {
        objects.clear();
        pendingAdd.clear();
        particles.clear();
    }

    public void spawnParticles(double x, double y, Color color, int count,
                               double speedMin, double speedMax,
                               double lifeMin,  double lifeMax,
                               double sizeMin,  double sizeMax) {
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double speed = speedMin + rng.nextDouble() * (speedMax - speedMin);
            double life  = lifeMin  + rng.nextDouble() * (lifeMax  - lifeMin);
            double size  = sizeMin  + rng.nextDouble() * (sizeMax  - sizeMin);
            particles.add(new Particle(x, y,
                    Math.cos(angle)*speed, Math.sin(angle)*speed,
                    color, life, size));
        }
    }

    public void spawnHitSparks(double x, double y, Color color) {
        spawnParticles(x, y, color, 4, 40, 120, 0.2, 0.4, 2, 4);
    }

    public void spawnExplosion(double x, double y, Color color) {
        spawnParticles(x, y, color,      14, 60,  200, 0.5, 1.0, 3, 7);
        spawnParticles(x, y, Color.WHITE, 5, 100, 260, 0.2, 0.5, 2, 4);
    }

    /** A large shockwave blast for the special bullet's area detonation. The
     *  particle spread scales with the destruction radius so it visually fills
     *  the area it actually damages. */
    public void spawnBigExplosion(double x, double y, double radius, Color color) {
        double fast = radius * 2.2;     // outward speed ~ reaches the radius edge
        spawnParticles(x, y, color,            60, fast * 0.4, fast, 0.5, 1.1, 4, 9);
        spawnParticles(x, y, Color.WHITE,      24, fast * 0.5, fast, 0.25, 0.6, 3, 6);
        spawnParticles(x, y, new Color(120, 240, 255), 30, fast * 0.3, fast * 0.9, 0.4, 0.9, 3, 7);
    }

    public List<Particle> particles() { return particles; }
    public Random rng()               { return rng;       }

    public void maybeDropPowerUp(double x, double y) {
        if (rng.nextDouble() > 0.20) return;
        PowerUp.Type[] types = PowerUp.Type.values();
        add(new PowerUp(this, x, y, types[rng.nextInt(types.length)]));
    }
}