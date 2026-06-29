package enemies;

import core.World;
import entities.Bullet;
import entities.Player;
import patterns.AimedPattern;

import java.awt.*;

/**
 * WaveBug — บั๊กบินเป็นคลื่น (รูปผีเสื้อสีเหลือง).
 *
 * จุดเด่น 2 อย่าง:
 *  1. แม้อยู่ในช่องขบวนก็ยังส่ายขึ้น-ลงเป็นคลื่นไซน์ (ดูมีชีวิต).
 *  2. ยิงตอบโต้ "ตอนตาย" (onDeath) แทนที่จะยิงระหว่างมีชีวิต.
 */
public class WaveBug extends Enemy {

    private static final double WAVE_AMPLITUDE = 55;  // ความสูงของการส่าย (px)
    private static final double WAVE_PERIOD    = 3.5; // เวลาส่ายครบ 1 รอบ (วินาที)
    private static final double DIVE_SPEED     = 220; // ความเร็วดิ่งลงพื้นฐาน

    public WaveBug(World world, double x, double y) {
        // เลือด 2, คะแนน 160, คูลดาวน์ 2.5 (แต่ override updateShooting ปิดการยิงปกติ)
        super(world, x, y, 30, 26, 2, 160, 2.5,
                new Color(255, 180, 0), new Color(200, 120, 0), new Color(255, 240, 100));
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; } // กำลังพุ่งหลังเข้าฉาก

        if (inFormation) {
            // อยู่ในช่อง: บวก offset แบบไซน์ให้ส่ายขึ้น-ลง
            // sin(patternTimer * 2π/period) แกว่ง -1..1 ครบรอบทุก WAVE_PERIOD วินาที
            double waveOff = Math.sin(patternTimer * (2*Math.PI / WAVE_PERIOD)) * WAVE_AMPLITUDE;
            double tx = formationX + world.getFormationOffset(); // ช่อง X (รวมการส่ายของขบวน)
            double ty = formationY + waveOff;                    // ช่อง Y + การส่ายเฉพาะตัว
            // ดึงเข้าเป้าแบบสปริง (ยิ่งไกลยิ่งเร่ง ×5)
            vx = (tx - x) * 5;
            vy = (ty - y) * 5;
            return;
        }
        // กำลังดิ่ง: ส่ายซ้าย-ขวาตาม divePhase พร้อมพุ่งลง (เร็วขึ้นตามด่าน)
        double spd = DIVE_SPEED * speedMult;
        vx = Math.sin(divePhase * 3) * 200 * speedMult;
        vy = spd + wave * 10 * speedMult;
        if (y > world.height + 40) startSmoothReturn(); // หลุดล่างจอ → กลับช่อง
    }

    @Override protected void updateShooting(double dt) {} // ปิดการยิงปกติ (ยิงตอนตายแทน)

    /** ตอนตาย: ยิงตอบโต้ผู้เล่น 2 นัด (เล็งตรง + เฉียง 15°). */
    @Override
    protected void onDeath() {
        Player p = findPlayer();
        if (p == null) return;
        double spd = 260 + wave * 8; // เร็วขึ้นตามด่าน
        firePattern(new AimedPattern(spd)); // นัดที่ 1: เล็งตรงผู้เล่น
        // นัดที่ 2: เล็งผู้เล่นแล้วเบนมุม +15° (กระสุนเฉียง กันผู้เล่นยืนนิ่ง)
        double dx  = p.getX() - x;
        double dy  = p.getY() - y;
        double len = Math.sqrt(dx*dx + dy*dy) + 0.001;        // +0.001 กันหารศูนย์ (ไม่ได้ใช้ len ต่อ แต่กันพลาด)
        double ang = Math.atan2(dy, dx) + Math.toRadians(15); // มุมไปผู้เล่น + 15°
        // แปลงมุม+ความเร็ว เป็นเวกเตอร์ความเร็ว (cos=แกน X, sin=แกน Y)
        world.add(new Bullet(world, x, y,
                Math.cos(ang)*spd, Math.sin(ang)*spd, false, 1));
    }

    @Override
    public void draw(Graphics2D g2) {
        drawGlow(g2); // วงเรืองแสง
        // ปีกซ้าย (สามเหลี่ยม 4 จุด) และปีกขวา (สะท้อนกระจก)
        int[] lx={iX(),iX()-(int)(w/2),iX()-(int)(w/3),iX()-2};
        int[] ly={iY()-(int)(h/4),iY()-(int)(h/2),iY()+(int)(h/2),iY()+2};
        int[] rx={iX(),iX()+(int)(w/2),iX()+(int)(w/3),iX()+2};
        int[] ry={iY()-(int)(h/4),iY()-(int)(h/2),iY()+(int)(h/2),iY()+2};
        g2.setColor(colorPrimary);
        g2.fillPolygon(lx,ly,4); g2.fillPolygon(rx,ry,4); // ปีกทั้งสองข้าง
        g2.setColor(colorSecondary);
        g2.fillOval(iX()-5,iY()-(int)(h/2),10,(int)h); // ลำตัว (วงรีกลาง)
        g2.setColor(colorAccent); g2.fillOval(iX()-3,iY()-4,6,8); // จุดเน้นกลางตัว
    }
    // ตัวช่วยแปลงพิกัด double → int สำหรับการวาด
    private int iX() { return (int)x; }
    private int iY() { return (int)y; }
}
