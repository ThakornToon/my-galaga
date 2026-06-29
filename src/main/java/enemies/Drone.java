package enemies;

import core.World;
import java.awt.*;

public class Drone extends Enemy {
    public Drone(World world, double x, double y) {
        super(world, x, y, 28, 24, 1, 100, 2.5,
                new Color(200, 80, 255), new Color(140, 40, 200), new Color(255, 150, 255));
        shootCooldown = 3.2;
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }
        if (inFormation) { moveTowardsFormation(0, 0); return; }
        vy = 180 * speedMult;
        vx = 0;
        if (y > world.height + 40) startSmoothReturn();
    }

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2);
        int[] px={iX(),iX()-(int)(w/2),iX()-(int)(w/4),iX()+(int)(w/4),iX()+(int)(w/2)};
        int[] py={iY()-(int)(h/2),iY()+(int)(h/4),iY()+(int)(h/2),iY()+(int)(h/2),iY()+(int)(h/4)};
        g2.setColor(colorSecondary); g2.fillPolygon(px,py,5);
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1f)); g2.drawPolygon(px,py,5);
        g2.setColor(colorAccent);
        g2.fillOval(iX()-6,iY()-2,5,5); g2.fillOval(iX()+2,iY()-2,5,5);
    }
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}