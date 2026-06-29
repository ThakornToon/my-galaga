package entities;

import core.Updatable;
import core.Drawable;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public class Particle implements Updatable, Drawable {
    private double x, y, vx, vy;
    private final Color color;
    private double lifetime, maxLifetime;
    private final double size;
    private boolean alive = true;

    public boolean isAlive() { return alive; }

    public Particle(double x, double y, double vx, double vy, Color color, double life, double size) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.lifetime = life;
        this.maxLifetime = life;
        this.size = size;
    }

    @Override
    public void update(double dt) {
        x += vx * dt;
        y += vy * dt;
        vx *= 0.90;
        vy *= 0.90;
        lifetime -= dt;
        if (lifetime <= 0) alive = false;
    }

    @Override
    public void draw(Graphics2D g) {
        float alpha = (float) Math.max(0, lifetime / maxLifetime);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
        g.fill(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
    }
}