package enemies;

import core.World;
import entities.EnergyWave;
import entities.Player;

import java.awt.*;

/**
 * DiveBug (orange diamond).
 * No shooting. Energy wave = cone/fan shape pointing downward (see EnergyWave).
 */
public class DiveBug extends Enemy {

    private enum DivePhaseState { ADVANCE, PAUSE, FIRE, RETURN }
    private DivePhaseState dpState = DivePhaseState.ADVANCE;

    private double snapX, snapY;
    private double targetX, targetY;
    private double pauseTimer = 0;
    private static final double PAUSE_DURATION = 0.55;
    private static final double ADVANCE_SPEED  = 240;
    private boolean waveFired = false;

    public DiveBug(World world, double x, double y, int row, int col) {
        super(world, x, y, 30, 28, 2, 200, 3.5,
                new Color(255, 100, 50), new Color(200, 50, 0), new Color(255, 200, 100));
    }

    @Override
    public void triggerDive() {
        super.triggerDive();
        Player p = findPlayer();
        snapX   = (p != null) ? p.getX() : world.width / 2.0;
        snapY   = (p != null) ? p.getY() : world.height - 80;
        targetX = snapX;
        targetY = snapY - world.height / 4.0;
        dpState    = DivePhaseState.ADVANCE;
        pauseTimer = 0;
        waveFired  = false;
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }

        if (inFormation) { moveTowardsFormation(dt, 0, 0); return; }

        double spd = ADVANCE_SPEED * speedMult;

        switch (dpState) {
            case ADVANCE -> {
                double dx  = targetX - x;
                double dy  = targetY - y;
                double len = Math.sqrt(dx*dx + dy*dy) + 0.001;
                vx = (dx/len) * spd;
                vy = (dy/len) * spd;
                if (len < 18) {
                    vx = 0; vy = 0; x = targetX; y = targetY;
                    dpState = DivePhaseState.PAUSE; pauseTimer = 0;
                }
            }
            case PAUSE -> {
                vx = 0; vy = 0;
                pauseTimer += dt;
                if (pauseTimer >= PAUSE_DURATION) dpState = DivePhaseState.FIRE;
            }
            case FIRE -> {
                vx = 0; vy = 0;
                if (!waveFired) {
                    world.add(new EnergyWave(world, x, y));
                    waveFired = true;
                }
                pauseTimer += dt;
                if (pauseTimer >= PAUSE_DURATION + 0.4) dpState = DivePhaseState.RETURN;
            }
            case RETURN -> {
                startSmoothReturn();
                waveFired = false;
                dpState   = DivePhaseState.ADVANCE;
            }
        }
    }

    @Override protected void updateShooting(double dt) {}

    @Override
    public void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGlow(g2);
        int[] px = {iX(), iX()-(int)(w/2), iX(), iX()+(int)(w/2)};
        int[] py = {iY()-(int)(h/2), iY(), iY()+(int)(h/2), iY()};
        g2.setColor(colorSecondary); g2.fillPolygon(px, py, 4);
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1.5f)); g2.drawPolygon(px, py, 4);
        g2.setColor(colorAccent); g2.fillOval(iX()-4, iY()-4, 8, 8);
        if (!inFormation && (dpState == DivePhaseState.PAUSE || dpState == DivePhaseState.FIRE)) {
            // sin term dips below zero, so clamp the alpha into [0,255]
            // (a raw negative alpha throws IllegalArgumentException and, on the
            // render thread, would freeze the game).
            int a = Math.max(0, Math.min(255, (int)((0.3 + 0.4 * Math.sin(patternTimer * 12)) * 255)));
            g2.setColor(new Color(255, 200, 50, a));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(iX()-(int)(w/2)-6, iY()-(int)(h/2)-6, (int)w+12, (int)h+12);
        }
        g2.dispose();
    }
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}