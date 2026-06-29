package entities;

import core.World;

import java.awt.*;

public class PowerUp extends GameObject {
    public enum Type { DOUBLE_SHOT, SPREAD_SHOT, SHIELD, SCORE_MULT, RAPID_FIRE }

    private final Type type;
    // Lifetime must exceed the time to fall the full screen height, otherwise a
    // power-up dropped high up (e.g. from a top-row enemy) would expire in mid-air
    // BEFORE it ever reaches the player at the bottom. At vy=90 a full-height fall
    // (~760px) takes ~8.4s, so 12s guarantees it always falls past the player and
    // is removed by the off-screen check below — never vanishing within reach.
    private double lifetime = 12.0;
    private final Color color;

    public PowerUp(World world, double x, double y, Type type) {
        super(world, x, y, 18, 18);
        this.type = type;
        this.vy = 90;
        this.color = switch (type) {
            case DOUBLE_SHOT -> new Color(100, 255, 100);
            case SPREAD_SHOT -> new Color(100, 200, 255);
            case SHIELD -> new Color(255, 220, 0);
            case SCORE_MULT -> new Color(255, 100, 255);
            case RAPID_FIRE -> new Color(255, 150, 50);
        };
    }

    public Type getType() { return type; }

    @Override
    public void update(double dt) {
        applyVelocity(dt);
        rotation += dt * 120;
        lifetime -= dt;
        if (lifetime <= 0 || y > world.height + 30) destroy();
    }

    @Override
    public void draw(Graphics2D g2) {
        g2.rotate(Math.toRadians(rotation), x, y);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
        g2.fillOval((int) (x - w / 2 - 4), (int) (y - h / 2 - 4), (int) (w + 8), (int) (h + 8));
        g2.setColor(color.darker());
        g2.fillRect((int) (x - w / 2), (int) (y - h / 2), (int) w, (int) h);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect((int) (x - w / 2), (int) (y - h / 2), (int) w, (int) h);
        String label = switch (type) {
            case DOUBLE_SHOT -> "2X";
            case SPREAD_SHOT -> "SP";
            case SHIELD -> "SH";
            case SCORE_MULT -> "SC";
            case RAPID_FIRE -> "RF";
        };
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, (int) (x - fm.stringWidth(label) / 2.0), (int) (y + fm.getAscent() / 2.0 - 1));
    }
}