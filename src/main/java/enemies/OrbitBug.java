package enemies;

import core.World;
import entities.Player;

import java.awt.*;

public class OrbitBug extends Enemy {

    private double snapX, snapY;
    private enum OrbitPhase { APPROACH, LOOP_OUT, RETURN }
    private OrbitPhase orbitPhase = OrbitPhase.APPROACH;
    private double loopAngle  = 0;
    private double loopCenterX, loopCenterY;
    private static final double LOOP_RADIUS = 180;
    private static final double ORBIT_SPEED = 320;

    public OrbitBug(World world, double x, double y, int row, int col) {
        super(world, x, y, 30, 26, 2, 150, 2.5,
                new Color(50, 200, 255), new Color(0, 120, 200), new Color(150, 255, 255));
    }

    @Override
    public void triggerDive() {
        super.triggerDive();
        Player p = findPlayer();
        snapX = (p != null) ? p.getX() : world.width / 2.0;
        snapY = (p != null) ? p.getY() : world.height - 80;
        orbitPhase = OrbitPhase.APPROACH;
        loopAngle  = 0;
    }

    @Override
    protected void updateAI(double dt) {
        // Handle post-entry swoop
        if (swoopActive) { runSwoop(dt); return; }

        if (inFormation) {
            moveTowardsFormation(dt,
                    Math.cos(patternTimer * 1.5) * 25 * speedMult,
                    Math.sin(patternTimer * 1.5) * 10 * speedMult);
            return;
        }

        double spd = ORBIT_SPEED * speedMult;

        switch (orbitPhase) {
            case APPROACH -> {
                double dx  = snapX - x;
                double dy  = snapY - y;
                double len = Math.sqrt(dx*dx + dy*dy) + 0.001;
                vx = (dx/len) * spd;
                vy = (dy/len) * spd;
                if (len < 30) {
                    orbitPhase  = OrbitPhase.LOOP_OUT;
                    loopAngle   = 0;
                    double side = (x < world.width / 2.0) ? -1 : 1;
                    loopCenterX = x + side * LOOP_RADIUS;
                    loopCenterY = y;
                }
            }
            case LOOP_OUT -> {
                loopAngle += dt * (spd / LOOP_RADIUS);
                x  = loopCenterX + Math.cos(loopAngle) * LOOP_RADIUS;
                y  = loopCenterY + Math.sin(loopAngle) * LOOP_RADIUS;
                vx = 0; vy = 0;
                if (loopAngle >= Math.PI) orbitPhase = OrbitPhase.RETURN;
            }
            case RETURN -> {
                startSmoothReturn();
                orbitPhase = OrbitPhase.APPROACH;
            }
        }
    }

    @Override protected void updateShooting(double dt) {}

    @Override
    public void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGlow(g2);
        g2.setColor(colorSecondary);
        g2.fillOval((int)(x-w/2+4),(int)(y-h/2+4),(int)(w-8),(int)(h-8));
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x-w/2),(int)(y-h/2),(int)w,(int)h);
        for (int i = 0; i < 4; i++) {
            double a = patternTimer * 2 + i * Math.PI / 2;
            g2.setColor(colorAccent);
            g2.fillOval((int)(x+Math.cos(a)*w/2)-3,(int)(y+Math.sin(a)*h/2)-3,6,6);
        }
        g2.dispose();
    }
}