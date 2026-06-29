package enemies;

import core.World;
import java.awt.*;

/**
 * Drone — ศัตรูที่เรียบง่ายที่สุดในเกม (รูปลูกศร 5 เหลี่ยมสีม่วง).
 *
 * พฤติกรรม: อยู่ในช่องขบวน → เมื่อถูกสั่งดิ่งจะพุ่งลงตรงๆ → หลุดล่างจอแล้ว
 * กลับเข้าช่อง. เป็นศัตรูตัวเดียวที่ยิงกระสุนพื้นฐาน (ใช้ updateShooting ของ
 * คลาสแม่ Enemy โดยไม่ override).
 */
public class Drone extends Enemy {
    public Drone(World world, double x, double y) {
        // super(world, x, y, กว้าง, สูง, เลือด, คะแนน, คูลดาวน์ยิง(วินาที), สีหลัก, สีรอง, สีเน้น)
        super(world, x, y, 28, 24, 1, 100, 3.2,
                new Color(200, 80, 255), new Color(140, 40, 200), new Color(255, 150, 255));
    }

    @Override
    protected void updateAI(double dt) {
        // กำลังพุ่งโจมตี (swoop) หลังเข้าฉาก → ให้คลาสแม่เดินตามเส้น swoop
        if (swoopActive) { runSwoop(dt); return; }
        // อยู่ในช่องขบวน → ค่อยๆ เคลื่อนเข้าประจำที่ (ไม่ส่าย จึงส่ง 0,0)
        if (inFormation) { moveTowardsFormation(0, 0); return; }
        // กำลังดิ่ง → ลงตรงๆ (vx=0) ความเร็วลง 180 px/วินาที
        vy = 180 * speedMult;
        vx = 0;
        // หลุดพ้นขอบล่างจอ (เผื่อ 40px) → จบการดิ่ง กลับเข้าช่องแบบนุ่มนวล
        if (y > world.height + 40) startSmoothReturn();
    }

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2); // วงเรืองแสงจางๆ รอบตัว
        // รูปลูกศร 5 จุด (หัวชี้ขึ้น): บน, ซ้ายล่าง, กลางซ้าย, กลางขวา, ขวาล่าง
        int[] px={iX(),iX()-(int)(w/2),iX()-(int)(w/4),iX()+(int)(w/4),iX()+(int)(w/2)};
        int[] py={iY()-(int)(h/2),iY()+(int)(h/4),iY()+(int)(h/2),iY()+(int)(h/2),iY()+(int)(h/4)};
        g2.setColor(colorSecondary); g2.fillPolygon(px,py,5);   // เติมสีตัว
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1f)); g2.drawPolygon(px,py,5); // ขอบ
        g2.setColor(colorAccent);
        g2.fillOval(iX()-6,iY()-2,5,5); g2.fillOval(iX()+2,iY()-2,5,5); // ตา 2 ดวง
    }
    // ตัวช่วยแปลงพิกัด double → int (เมธอดวาดของ Java ต้องการ int)
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}
