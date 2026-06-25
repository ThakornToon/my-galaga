package patterns;

import core.World;
import entities.Bullet;
import java.util.List;

public interface BulletPattern {
    List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer);
}