package entities;

import core.World;
import java.awt.*;
import java.awt.geom.*;

/**
 * EnergyWave — cone/fan shape pointing downward (like Galaga '88).
 * Multiple arcs expand outward from the source enemy.
 * Damages the player if their X falls within the cone's X span.
 */
public class EnergyWave extends GameObject {

    private final double cx, cy;          // origin (enemy centre)
    private static final int   RING_COUNT  = 14;   // number of arcs
    private static final double ARC_SPACING = 18;  // px between rings
    private static final double CONE_HALF_ANGLE = Math.toRadians(35); // half-width of cone
    private static final double CONE_TAN        = Math.tan(CONE_HALF_ANGLE);
    private static final double EXPAND_SPEED    = 200; // px/sec
    private static final double MAX_REACH       = RING_COUNT * ARC_SPACING + 60;

    private double progress = 0;   // 0..1 lifetime
    private boolean hasHit  = false;

    public EnergyWave(World world, double cx, double cy) {
        super(world, cx, cy, MAX_REACH * 2, MAX_REACH);
        this.cx = cx;
        this.cy = cy;
    }

    @Override
    public void update(double dt) {
        if (!alive) return;
        progress += dt * (EXPAND_SPEED / MAX_REACH);

        // Check player hit: player must be below origin AND within X cone
        if (!hasHit) {
            double currentReach = progress * MAX_REACH;
            Player p = world.player();
            if (p != null) {
                double relY = p.getY() - cy;
                double relX = Math.abs(p.getX() - cx);
                double coneW = CONE_TAN * relY;
                // Player is below origin, within cone's depth, within X span
                if (relY > 0 && relY <= currentReach && relX <= coneW + 20) {
                    p.absorbHit();
                    hasHit = true;
                }
            }
        }
        if (progress >= 1.0) destroy();
    }

    @Override
    public void draw(Graphics2D g2) {
        if (!alive) return;

        double currentReach = progress * MAX_REACH;

        for (int i = 1; i <= RING_COUNT; i++) {
            double ringDist = i * ARC_SPACING;
            if (ringDist > currentReach) break;

            // Alpha fades with distance and lifetime
            float alpha = (float)((1.0 - ringDist / MAX_REACH) * (1.0 - progress * 0.5));
            if (alpha <= 0) continue;

            // Cone width at this ring
            double halfW = CONE_TAN * ringDist;
            double arcX  = cx - halfW;
            double arcY  = cy + ringDist - halfW * 0.4;
            double arcW  = halfW * 2;
            double arcH  = halfW * 0.8;  // flattened arc

            // Outer glow
            g2.setColor(new Color(100, 180, 255, (int)(alpha * 55)));
            g2.setStroke(new BasicStroke(5f));
            g2.draw(new Arc2D.Double(arcX - 4, arcY - 4, arcW + 8, arcH + 8,
                    180, 180, Arc2D.OPEN));

            // Main arc — gradient from cyan to blue
            float hue   = 0.58f + (float)(ringDist / MAX_REACH) * 0.08f;
            Color arcCol = Color.getHSBColor(hue, 0.9f, 1.0f);
            g2.setColor(new Color(arcCol.getRed(), arcCol.getGreen(), arcCol.getBlue(),
                    (int)(alpha * 220)));
            g2.setStroke(new BasicStroke(2.2f));
            g2.draw(new Arc2D.Double(arcX, arcY, arcW, arcH, 180, 180, Arc2D.OPEN));
        }

        // Centre beam: thin vertical line from origin
        float beamAlpha = (float)(1.0 - progress);
        g2.setColor(new Color(200, 230, 255, (int)(beamAlpha * 160)));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Line2D.Double(cx, cy, cx, cy + currentReach * 0.6));
    }

    @Override
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(cx - MAX_REACH, cy, MAX_REACH * 2, MAX_REACH);
    }
}