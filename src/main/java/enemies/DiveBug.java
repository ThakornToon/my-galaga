package enemies;

import core.World;
import entities.EnergyWave;
import entities.Player;

import java.awt.*;

/**
 * DiveBug (orange diamond) — บั๊กดิ่งยิงคลื่นพลังงาน (รูปข้าวหลามตัดสีส้ม).
 * No shooting. Energy wave = cone/fan shape pointing downward (see EnergyWave).
 *
 * ไม่ยิงกระสุนปกติ แต่ปล่อย "คลื่นพลังงาน" รูปพัดลงล่างแทน.
 * รูปแบบการดิ่ง (สเตทแมชชีน 4 ช่วง):
 *   ADVANCE → พุ่งไปจุดเหนือผู้เล่น
 *   PAUSE   → หยุดนิ่งเล็ง (มีวงเตือนกระพริบ) ให้ผู้เล่นมีเวลาหลบ
 *   FIRE    → ปล่อยคลื่น 1 ครั้ง
 *   RETURN  → กลับเข้าช่อง
 */
public class DiveBug extends Enemy {

    private enum DivePhaseState { ADVANCE, PAUSE, FIRE, RETURN } // 4 ช่วงของการดิ่ง
    private DivePhaseState dpState = DivePhaseState.ADVANCE;

    private double targetX, targetY;                    // จุดเป้าที่จะพุ่งไปหยุด
    private double pauseTimer = 0;                       // จับเวลาช่วงหยุด/ยิง
    private static final double PAUSE_DURATION = 0.55;   // หยุดเล็งนานเท่านี้ก่อนยิง
    private static final double ADVANCE_SPEED  = 240;    // ความเร็วพุ่ง
    private boolean waveFired = false;                   // ยิงคลื่นไปแล้วหรือยัง (กันยิงซ้ำ)

    public DiveBug(World world, double x, double y) {
        // เลือด 2, คะแนน 200 (ไม่ยิงกระสุน — override updateShooting ปิดไว้)
        super(world, x, y, 30, 28, 2, 200, 3.5,
                new Color(255, 100, 50), new Color(200, 50, 0), new Color(255, 200, 100));
    }

    @Override
    public void triggerDive() {
        super.triggerDive(); // ปลดออกจากช่อง + รีเซ็ต divePhase
        // "ถ่ายรูป" ตำแหน่งผู้เล่นไว้ แล้วตั้งเป้าหยุด "เหนือ" ผู้เล่น 1/4 จอ
        // (ไม่พุ่งชนตรงๆ แต่ไปหยุดข้างบนเพื่อยิงคลื่นลงมา)
        Player p = findPlayer();
        double snapX = (p != null) ? p.getX() : world.width / 2.0;
        double snapY = (p != null) ? p.getY() : world.height - 80;
        targetX = snapX;
        targetY = snapY - world.height / 4.0;
        dpState    = DivePhaseState.ADVANCE;
        pauseTimer = 0;
        waveFired  = false;
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }              // กำลังพุ่งหลังเข้าฉาก
        if (inFormation) { moveTowardsFormation(0, 0); return; } // อยู่ในช่อง

        double spd = ADVANCE_SPEED * speedMult;

        switch (dpState) {
            case ADVANCE -> {
                // พุ่งเข้าหาเป้า: เวกเตอร์ทิศทางหน่วย × ความเร็ว
                double dx  = targetX - x;
                double dy  = targetY - y;
                double len = Math.sqrt(dx*dx + dy*dy) + 0.001; // +กันหารศูนย์
                vx = (dx/len) * spd;
                vy = (dy/len) * spd;
                if (len < 18) { // ถึงเป้า → หยุดสนิทแล้วเข้าช่วง PAUSE
                    vx = 0; vy = 0; x = targetX; y = targetY;
                    dpState = DivePhaseState.PAUSE; pauseTimer = 0;
                }
            }
            case PAUSE -> { // นิ่งเล็ง (วงเตือนกระพริบใน draw)
                vx = 0; vy = 0;
                pauseTimer += dt;
                if (pauseTimer >= PAUSE_DURATION) dpState = DivePhaseState.FIRE;
            }
            case FIRE -> { // ปล่อยคลื่นพลังงาน 1 ครั้ง แล้วรออีกนิดก่อนกลับ
                vx = 0; vy = 0;
                if (!waveFired) {
                    world.add(new EnergyWave(world, x, y));
                    waveFired = true;
                }
                pauseTimer += dt;
                if (pauseTimer >= PAUSE_DURATION + 0.4) dpState = DivePhaseState.RETURN;
            }
            case RETURN -> {
                startSmoothReturn();              // สร้างเส้นโค้งกลับช่อง
                waveFired = false;
                dpState   = DivePhaseState.ADVANCE; // รีเซ็ตเผื่อถูกสั่งดิ่งรอบหน้า
            }
        }
    }

    @Override protected void updateShooting(double dt) {} // ไม่ยิงกระสุน (ใช้คลื่นแทน)

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2); // วงเรืองแสง
        // รูปข้าวหลามตัด 4 จุด: บน, ซ้าย, ล่าง, ขวา
        int[] px = {iX(), iX()-(int)(w/2), iX(), iX()+(int)(w/2)};
        int[] py = {iY()-(int)(h/2), iY(), iY()+(int)(h/2), iY()};
        g2.setColor(colorSecondary); g2.fillPolygon(px, py, 4); // เติมสี
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1.5f)); g2.drawPolygon(px, py, 4); // ขอบ
        g2.setColor(colorAccent); g2.fillOval(iX()-4, iY()-4, 8, 8); // จุดกลาง

        // วงเตือนกระพริบ — แสดงเฉพาะตอนหยุดเล็ง/ยิง (เตือนผู้เล่นว่าจะยิงคลื่น)
        if (!inFormation && (dpState == DivePhaseState.PAUSE || dpState == DivePhaseState.FIRE)) {
            // sin term dips below zero, so clamp the alpha into [0,255]
            // (a raw negative alpha throws IllegalArgumentException and, on the
            // render thread, would freeze the game).
            // หมายเหตุ: sin ให้ค่าติดลบได้ ต้อง clamp alpha ให้อยู่ใน [0,255]
            // ไม่งั้น Java โยน IllegalArgumentException กลางเธรดวาด → เกมค้าง
            int a = Math.max(0, Math.min(255, (int)((0.3 + 0.4 * Math.sin(patternTimer * 12)) * 255)));
            g2.setColor(new Color(255, 200, 50, a));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(iX()-(int)(w/2)-6, iY()-(int)(h/2)-6, (int)w+12, (int)h+12);
        }
    }
    // ตัวช่วยแปลงพิกัด double → int สำหรับการวาด
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}
