package patterns;

import core.World;
import entities.Bullet;
import java.util.ArrayList;
import java.util.List;

public class SpreadPattern implements BulletPattern {
    private final double speed;
    private final int count;
    private final double spreadDeg;

    public SpreadPattern(double speed, int count, double spreadDeg) {
        this.speed = speed;
        this.count = count;
        this.spreadDeg = spreadDeg;
    }

    @Override
    public List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer) {
        List<Bullet> bullets = new ArrayList<>();
        double baseAngle = fromPlayer ? -Math.PI / 2 : Math.PI / 2;
        double spread = Math.toRadians(spreadDeg);
        for (int i = 0; i < count; i++) {
            double a = baseAngle + spread * (i / (double) (count - 1) - 0.5);
            bullets.add(new Bullet(world, srcX, srcY, Math.cos(a) * speed, Math.sin(a) * speed, fromPlayer, 1));
        }
        return bullets;
    }
}