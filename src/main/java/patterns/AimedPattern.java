package patterns;

import core.World;
import entities.Bullet;
import entities.Player;
import java.util.List;

public class AimedPattern implements BulletPattern {
    private final double speed;

    public AimedPattern(double speed) { this.speed = speed; }

    @Override
    public List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer) {
        List<Player> players = world.allOf(Player.class);
        if (players.isEmpty()) return List.of();
        Player target = players.get(0);
        double dx = target.getX() - srcX;
        double dy = target.getY() - srcY;
        double len = Math.sqrt(dx * dx + dy * dy) + 0.001;
        return List.of(new Bullet(world, srcX, srcY, (dx / len) * speed, (dy / len) * speed, false, 1));
    }
}