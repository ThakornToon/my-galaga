package entities;

import core.World;
import core.Updatable;
import core.Drawable;
import core.Collidable;
import java.awt.geom.Rectangle2D;

public abstract class GameObject implements Updatable, Drawable, Collidable {
    protected double x, y, w, h;
    protected double vx, vy;
    protected double rotation;
    protected boolean alive = true;
    protected World world;

    public GameObject(World world, double x, double y, double w, double h) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // ========== GETTERS ==========
    public double getX() { return x; }
    public double getY() { return y; }
    public double getW() { return w; }
    public double getH() { return h; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public double getRotation() { return rotation; }

    // ========== SETTERS ==========
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setVx(double vx) { this.vx = vx; }
    public void setVy(double vy) { this.vy = vy; }
    public void setRotation(double rotation) { this.rotation = rotation; }

    @Override
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(x - w / 2, y - h / 2, w, h);
    }

    @Override
    public boolean isAlive() { return alive; }

    public void destroy() { alive = false; }

    protected void applyVelocity(double dt) {
        x += vx * dt;
        y += vy * dt;
    }
}