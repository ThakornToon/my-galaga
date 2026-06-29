package enemies;

import core.World;
import entities.Player;

import java.awt.*;

/**
 * OrbitBug — บั๊กวนลูป (วงรีสีฟ้า).
 *
 * รูปแบบการดิ่ง (สเตทแมชชีน 3 ช่วง):
 *   APPROACH  → พุ่งเข้าหาตำแหน่งที่ "ถ่ายรูป" ผู้เล่นไว้
 *   LOOP_OUT  → วนเป็นครึ่งวงกลม (180°) ออกข้าง
 *   RETURN    → กลับเข้าช่องขบวน
 */
public class OrbitBug extends Enemy {

    private double snapX, snapY; // ตำแหน่งผู้เล่นที่ "ถ่ายรูป" ไว้ตอนเริ่มดิ่ง
    private enum OrbitPhase { APPROACH, LOOP_OUT, RETURN } // 3 ช่วงของการดิ่ง
    private OrbitPhase orbitPhase = OrbitPhase.APPROACH;
    private double loopAngle  = 0;            // มุมที่วนไปแล้ว (เรเดียน)
    private double loopCenterX, loopCenterY;  // จุดศูนย์กลางของวงลูป
    private static final double LOOP_RADIUS = 180; // รัศมีวงลูป
    private static final double ORBIT_SPEED = 320; // ความเร็วการเคลื่อนที่

    public OrbitBug(World world, double x, double y) {
        // เลือด 2, คะแนน 150 (ไม่ยิงกระสุน — override updateShooting ปิดไว้)
        super(world, x, y, 30, 26, 2, 150, 2.5,
                new Color(50, 200, 255), new Color(0, 120, 200), new Color(150, 255, 255));
    }

    @Override
    public void triggerDive() {
        super.triggerDive(); // ปลดออกจากช่อง + รีเซ็ต divePhase
        // "ถ่ายรูป" ตำแหน่งผู้เล่นไว้ ณ ตอนนี้ (ถ้าไม่มีผู้เล่นใช้กลางจอล่าง)
        // → จะพุ่งไปจุดนี้ ไม่ได้ตามผู้เล่นตลอด ผู้เล่นจึงหลบได้
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
            // อยู่ในช่อง: แกว่งเป็นวงรีเบาๆ (cos=ซ้ายขวา, sin=ขึ้นลง)
            moveTowardsFormation(
                    Math.cos(patternTimer * 1.5) * 25 * speedMult,
                    Math.sin(patternTimer * 1.5) * 10 * speedMult);
            return;
        }

        double spd = ORBIT_SPEED * speedMult;

        switch (orbitPhase) {
            case APPROACH -> {
                // พุ่งเข้าหาจุดที่ถ่ายรูปไว้: หาเวกเตอร์ทิศทาง (dx,dy) แล้วทำให้เป็นหน่วย
                double dx  = snapX - x;
                double dy  = snapY - y;
                double len = Math.sqrt(dx*dx + dy*dy) + 0.001; // ระยะถึงเป้า (+กันหารศูนย์)
                vx = (dx/len) * spd; // ทิศทางหน่วย × ความเร็ว
                vy = (dy/len) * spd;
                if (len < 30) { // ถึงเป้าแล้ว → เริ่มวงลูป
                    orbitPhase  = OrbitPhase.LOOP_OUT;
                    loopAngle   = 0;
                    // เลือกวนออกฝั่งที่มีที่ว่าง (อยู่ซ้ายจอ→วนซ้าย, อยู่ขวา→วนขวา)
                    double side = (x < world.width / 2.0) ? -1 : 1;
                    loopCenterX = x + side * LOOP_RADIUS; // ศูนย์กลางวงห่างออกข้าง 1 รัศมี
                    loopCenterY = y;
                }
            }
            case LOOP_OUT -> {
                // เพิ่มมุมตามความเร็วเชิงมุม (ω = ความเร็วเส้น / รัศมี)
                loopAngle += dt * (spd / LOOP_RADIUS);
                // หาตำแหน่งบนวงกลมจากมุม (คุม x,y ตรงๆ จึงตั้ง vx=vy=0)
                x  = loopCenterX + Math.cos(loopAngle) * LOOP_RADIUS;
                y  = loopCenterY + Math.sin(loopAngle) * LOOP_RADIUS;
                vx = 0; vy = 0;
                if (loopAngle >= Math.PI) orbitPhase = OrbitPhase.RETURN; // ครบครึ่งวง (180°)
            }
            case RETURN -> {
                startSmoothReturn();              // สร้างเส้นโค้งกลับช่อง
                orbitPhase = OrbitPhase.APPROACH; // รีเซ็ตเผื่อถูกสั่งดิ่งรอบหน้า
            }
        }
    }

    @Override protected void updateShooting(double dt) {} // ไม่ยิงกระสุน

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2); // วงเรืองแสง
        g2.setColor(colorSecondary);
        g2.fillOval((int)(x-w/2+4),(int)(y-h/2+4),(int)(w-8),(int)(h-8)); // วงในเติมสี
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x-w/2),(int)(y-h/2),(int)w,(int)h); // วงนอก (ขอบ)
        // จุดเน้น 4 จุดวิ่งหมุนรอบตัว (แต่ละจุดห่างกัน 90° = π/2)
        for (int i = 0; i < 4; i++) {
            double a = patternTimer * 2 + i * Math.PI / 2;
            g2.setColor(colorAccent);
            g2.fillOval((int)(x+Math.cos(a)*w/2)-3,(int)(y+Math.sin(a)*h/2)-3,6,6);
        }
    }
}
