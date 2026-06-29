package enemies;

import core.World;
import entities.Bullet;
import entities.Player;
import patterns.AimedPattern;

import java.awt.*;

public class WaveBug extends Enemy {

    private static final double WAVE_AMPLITUDE = 55;
    private static final double WAVE_PERIOD    = 3.5;
    private static final double DIVE_SPEED     = 220;

    public WaveBug(World world, double x, double y) {
        super(world, x, y, 30, 26, 2, 160, 2.5,
                new Color(255, 180, 0), new Color(200, 120, 0), new Color(255, 240, 100));
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }

        if (inFormation) {
            double waveOff = Math.sin(patternTimer * (2*Math.PI / WAVE_PERIOD)) * WAVE_AMPLITUDE;
            double tx = formationX + world.getFormationOffset();
            double ty = formationY + waveOff;
            vx = (tx - x) * 5;
            vy = (ty - y) * 5;
            return;
        }
        double spd = DIVE_SPEED * speedMult;
        vx = Math.sin(divePhase * 3) * 200 * speedMult;
        vy = spd + wave * 10 * speedMult;
        if (y > world.height + 40) startSmoothReturn();
    }

    @Override protected void updateShooting(double dt) {}

    @Override
    protected void onDeath() {
        Player p = findPlayer();
        if (p == null) return;
        double spd = 260 + wave * 8;
        firePattern(new AimedPattern(spd));
        double dx  = p.getX() - x;
        double dy  = p.getY() - y;
        double len = Math.sqrt(dx*dx + dy*dy) + 0.001;
        double ang = Math.atan2(dy, dx) + Math.toRadians(15);
        world.add(new Bullet(world, x, y,
                Math.cos(ang)*spd, Math.sin(ang)*spd, false, 1));
    }

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2);
        int[] lx={iX(),iX()-(int)(w/2),iX()-(int)(w/3),iX()-2};
        int[] ly={iY()-(int)(h/4),iY()-(int)(h/2),iY()+(int)(h/2),iY()+2};
        int[] rx={iX(),iX()+(int)(w/2),iX()+(int)(w/3),iX()+2};
        int[] ry={iY()-(int)(h/4),iY()-(int)(h/2),iY()+(int)(h/2),iY()+2};
        g2.setColor(colorPrimary);
        g2.fillPolygon(lx,ly,4); g2.fillPolygon(rx,ry,4);
        g2.setColor(colorSecondary);
        g2.fillOval(iX()-5,iY()-(int)(h/2),10,(int)h);
        g2.setColor(colorAccent); g2.fillOval(iX()-3,iY()-4,6,8);
    }
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}