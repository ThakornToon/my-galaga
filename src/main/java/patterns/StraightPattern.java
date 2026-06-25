package patterns;

import core.World;
import entities.Bullet;
import java.util.List;

public class StraightPattern implements BulletPattern {
    private final double speed;

    public StraightPattern(double speed) { this.speed = speed; }

    @Override
    public List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer) {
        double dir = fromPlayer ? -1 : 1;
        return List.of(new Bullet(world, srcX, srcY + dir * 20, 0, dir * speed, fromPlayer, 1));
    }
}