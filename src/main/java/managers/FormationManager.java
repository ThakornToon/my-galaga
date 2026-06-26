package managers;

import core.World;
import core.Updatable;
import enemies.Enemy;
import enemies.Boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class FormationManager implements Updatable {
    private double offsetX = 60;
    private boolean goRight = true;
    private double diveTimer = 0;
    private final World world;
    private final Random rng;

    public FormationManager(World world) {
        this.world = world;
        this.rng = world.rng();
    }

    @Override
    public void update(double dt) {
        double speed = 40 + world.wave * 5;
        if (goRight) offsetX += speed * dt;
        else offsetX -= speed * dt;

        if (offsetX > 90) goRight = false;
        if (offsetX < -90) goRight = true;

        world.setFormationOffset(offsetX);

        diveTimer -= dt;
        double interval = Math.max(1.0, 2.5 - world.wave * 0.2);
        if (diveTimer <= 0) {
            diveTimer = interval;
            triggerDive();
        }
    }

    private void triggerDive() {
        List<Enemy> candidates = new ArrayList<>();
        for (Enemy e : world.allOf(Enemy.class))
            if (e.isInFormation() && !(e instanceof Boss)) candidates.add(e);
        if (candidates.isEmpty()) return;
        int count = 1 + rng.nextInt(Math.min(2, candidates.size()));
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < count; i++) candidates.get(i).triggerDive();
    }
}