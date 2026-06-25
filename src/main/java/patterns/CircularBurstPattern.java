package patterns;

import core.World;
import entities.Bullet;
import java.util.ArrayList;
import java.util.List;

public class CircularBurstPattern implements BulletPattern {
    private final double speed;
    private final int count;
    private final double offsetAngle;

    public CircularBurstPattern(double speed, int count, double offsetAngle) {
        this.speed = speed;
        this.count = count;
        this.offsetAngle = offsetAngle;
    }

    @Override
    public List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer) {
        List<Bullet> bullets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = offsetAngle + 2 * Math.PI * i / count;
            bullets.add(new Bullet(world, srcX, srcY, Math.cos(a) * speed, Math.sin(a) * speed, false, 1));
        }
        return bullets;
    }
}