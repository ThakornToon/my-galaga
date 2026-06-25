package patterns;

import core.World;
import entities.Bullet;
import java.util.List;

public class DoubleStraightPattern implements BulletPattern {
    private final double speed;
    private final double offset;

    public DoubleStraightPattern(double speed, double offset) {
        this.speed = speed;
        this.offset = offset;
    }

    @Override
    public List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer) {
        return List.of(
                new Bullet(world, srcX - offset, srcY - 20, 0, -speed, true, 1),
                new Bullet(world, srcX + offset, srcY - 20, 0, -speed, true, 1)
        );
    }
}