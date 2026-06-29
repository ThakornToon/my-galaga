package enemies;

import core.World;
import entities.Player;
import patterns.CircularBurstPattern;
import patterns.AimedPattern;

import java.awt.*;

/**
 * Boss – firing interval reworked:
 *   Phase 0 = 8s, Phase 1 = 7s, Phase 2 = 6s  (base for first spawn)
 *   When HP < 50%: each phase cooldown −1s (min 2s)
 *   Each new Boss spawn: all phase cooldowns −1s (min 2s)
 *   Speed: +5% per 5 waves.
 *
 * บอส (รูปหกเหลี่ยมหมุนสีส้มแดง) — เลือดเยอะ มี 3 เฟสตามสัดส่วนเลือด.
 * ยิ่งเฟสสูง/เลือดน้อย ยิ่งเคลื่อนเร็วและยิงถี่ขึ้น และยิ่งเจอบอสบ่อยครั้ง
 * คูลดาวน์ยิงก็ยิ่งสั้นลง (ดู spawnCount).
 */
public class Boss extends Enemy {
    private int phase = 0;                              // เฟสปัจจุบัน (0/1/2)
    private static final int   BASE_HP        = 60;    // HP at the first boss (wave 5)
    private static final double HP_GROWTH_TIER = 0.15;  // +15% compounding per 10-wave tier

    // Per-spawn base cooldowns (seconds); decrease each respawn, floor at 2s
    // คูลดาวน์ยิงของแต่ละเฟส (วินาที) — ลดลงทุกครั้งที่บอสเกิดใหม่ ต่ำสุด 2s
    private double[] phaseCooldown;
    // Accumulated spawn count tracked via World
    // นับจำนวนบอสที่เคยเกิดทั้งเกม (static = จำข้ามตัว) → ใช้เพิ่มความยาก
    private static int spawnCount = 0;

    public Boss(World world) {
        // เกิดกลางบนจอ (y=-80 คือเหนือจอ), ตัวใหญ่ 70×60, เลือด 60, คะแนน 5000
        super(world, world.width / 2.0, -80, 70, 60, BASE_HP, 5000, 99,
                new Color(255, 80, 0), new Color(180, 30, 0), new Color(255, 220, 0));
        spawnCount++;
        // Base: 8, 7, 6 – reduce by (spawnCount-1) per phase, floor 2
        // ฐาน 8/7/6 วินาที ลบด้วย (จำนวนบอสที่เกิดมาแล้ว) แต่ไม่ต่ำกว่า 2
        int reduction = spawnCount - 1;
        phaseCooldown = new double[]{
                Math.max(2, 8 - reduction),
                Math.max(2, 7 - reduction),
                Math.max(2, 6 - reduction)
        };
        this.vy = 60; // ลอยลงมาจากบนจอตอนเกิด
    }

    /** Reset spawn counter when starting a new game */
    /** รีเซ็ตตัวนับบอสตอนเริ่มเกมใหม่ (ไม่งั้นความยากจะค้างจากเกมก่อน) */
    public static void resetSpawnCount() { spawnCount = 0; }

    /**
     * Boss HP scales differently from regular enemies: instead of a flat +1 per
     * 10 waves, it grows by +15% COMPOUNDING per 10-wave tier off its wave-5
     * baseline (wave5 ×1.0 = 60, wave10/15 ×1.15 ≈ 69, wave20/25 ×1.32 ≈ 79…).
     * We override setWave so the generic per-enemy bonus does NOT also apply.
     *
     * เลือดบอสโตต่างจากศัตรูทั่วไป: แทนการ +1 ต่อ 10 ด่าน บอสโต +15% แบบ
     * ทบต้นต่อทุก 10 ด่าน (อ้างอิงฐานด่าน 5). override เพื่อไม่ให้โดนสูตร
     * +1 ของคลาสแม่ซ้ำซ้อน (จึงไม่เรียก super.setWave).
     */
    @Override
    public void setWave(int w) {
        this.wave = w;
        int tier  = w / 10;
        maxHp = (int) Math.round(BASE_HP * Math.pow(1.0 + HP_GROWTH_TIER, tier)); // 60 × 1.15^tier
        hp    = maxHp;
    }

    /** On death, reward the player with +1 life and special (explosive) ammo:
     *  usually 1 round, occasionally 2. */
    /** ตอนตาย: ให้รางวัลผู้เล่น +1 ชีวิต และกระสุนพิเศษ (ระเบิด) ปกติ 1 นัด นานๆ ที 2. */
    @Override
    protected void onDeath() {
        Player p = findPlayer();
        if (p != null) {
            p.gainLife();   // killing the boss restores 1 life
            // 90% ได้กระสุนพิเศษ 1 นัด, 10% ได้ 2 นัด
            p.addSpecialAmmo(world.rng().nextDouble() < 0.90 ? 1 : 2);
        }
    }

    @Override
    protected void updateAI(double dt) {
        rotation += dt * 30; // หมุนตัวตลอดเวลา
        // Phase thresholds as fractions of maxHp so they track scaled HP
        // (was absolute 40/20, tuned only to the original 60-HP boss).
        // กำหนดเฟสจาก "สัดส่วน" เลือด (ไม่ใช่ตัวเลขตายตัว) เพื่อให้ตามเลือดที่
        // โตขึ้นตามด่านได้ถูก: >2/3=เฟส0, >1/3=เฟส1, ที่เหลือ=เฟส2
        int newPhase = hp > maxHp * 2 / 3 ? 0 : hp > maxHp / 3 ? 1 : 2;
        if (newPhase != phase) {
            phase = newPhase;
            startInvulnerability(1.2); // เปลี่ยนเฟส → อมตะ 1.2 วิ (กันโดนรัวขณะเปลี่ยน)
        }
        double spd = speedMult;
        // ยิ่งเฟสสูง เคลื่อนเร็ว/กว้างขึ้น (sin = ส่ายไปมาเป็นจังหวะ)
        switch (phase) {
            case 0 -> {
                vx = Math.sin(patternTimer * 0.8) * 120 * spd;
                vy = Math.sin(patternTimer * 0.3) * 40  * spd;
            }
            case 1 -> {
                vx = Math.sin(patternTimer * 1.2) * 150 * spd;
                vy = Math.sin(patternTimer * 0.5) * 60  * spd;
            }
            case 2 -> {
                vx = Math.sin(patternTimer * 1.8) * 180 * spd;
                vy = Math.sin(patternTimer * 0.8) * 80  * spd;
            }
        }
        // จำกัดไม่ให้หลุดขอบจอ และอยู่ครึ่งบน (y 60..200)
        x = Math.max(50, Math.min(world.width - 50, x));
        y = Math.max(60, Math.min(200, y));
    }

    @Override
    protected void updateShooting(double dt) {
        shootTimer += dt;

        // Effective cooldown: base − 1 if HP < 50%
        // คูลดาวน์จริง: ฐานของเฟส ลบ 1 วินาทีถ้าเลือด < 50% (ยิงถี่ขึ้นตอนใกล้ตาย)
        double hpFrac    = (double)hp / maxHp;
        double effective = phaseCooldown[phase] - (hpFrac < 0.5 ? 1.0 : 0.0);
        effective        = Math.max(2.0, effective); // ต่ำสุด 2 วินาที

        if (shootTimer >= effective) {
            shootTimer = 0;
            // แต่ละเฟสยิงรูปแบบต่างกัน (ยิ่งเฟสสูงยิ่งโหด)
            switch (phase) {
                case 0 -> firePattern(new CircularBurstPattern(200, 6, patternTimer)); // กระจาย 6 ทิศ
                case 1 -> {
                    firePattern(new AimedPattern(300));                          // เล็งผู้เล่น
                    firePattern(new CircularBurstPattern(180, 4, patternTimer));  // + กระจาย 4
                }
                case 2 -> {
                    firePattern(new CircularBurstPattern(220, 8, patternTimer * 2)); // กระจาย 8 (ถี่)
                    if (world.rng().nextBoolean())
                        firePattern(new AimedPattern(320));                          // สุ่มเล็งเพิ่ม
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g2) {
        // ออร่ารอบตัว (สีหลักแบบโปร่ง alpha=60)
        g2.setColor(new Color(colorPrimary.getRed(), colorPrimary.getGreen(),
                colorPrimary.getBlue(), 60));
        g2.fillOval((int)(x - w/2 - 12), (int)(y - h/2 - 12),
                (int)(w + 24), (int)(h + 24));
        // ตัวหกเหลี่ยมหมุน: คำนวณ 6 มุมจากมุมหมุนปัจจุบัน
        double rot  = Math.toRadians(rotation);
        int sides   = 6;
        int[] bx    = new int[sides];
        int[] by    = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = rot + i * Math.PI * 2 / sides; // แบ่งวงกลม 360° เป็น 6 ส่วน
            bx[i] = (int)(x + Math.cos(a) * w/2);
            by[i] = (int)(y + Math.sin(a) * h/2);
        }
        g2.setColor(colorSecondary);
        g2.fillPolygon(bx, by, sides); // เติมสีตัว
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(3f));
        g2.drawPolygon(bx, by, sides); // ขอบ
        g2.setColor(colorAccent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x - w/4), (int)(y - h/4), (int)(w/2), (int)(h/2)); // วงในประดับ
        // แกนกลางเปลี่ยนสีตามเฟส (เหลือง → ส้ม → แดง)
        Color coreColor = switch (phase) {
            case 0 -> new Color(255, 200, 50);
            case 1 -> new Color(255, 100, 50);
            default -> new Color(255, 50, 50);
        };
        g2.setColor(coreColor);
        g2.fillOval((int)(x - 12), (int)(y - 12), 24, 24);
        // แถบเลือดใต้ตัวบอส
        double hpFrac = (double)hp / maxHp;
        g2.setColor(new Color(40, 10, 10, 200));
        g2.fillRoundRect((int)(x - w/2), (int)(y + h/2 + 8), (int)w, 10, 4, 4); // พื้นหลังแถบ
        // สีแถบตามสัดส่วนเลือด: เขียว(>50%) → เหลือง(>25%) → แดง
        Color hpCol = hpFrac > 0.5 ? new Color(50, 220, 50)
                : hpFrac > 0.25 ? new Color(220, 180, 0)
                : new Color(220, 50, 50);
        g2.setColor(hpCol);
        g2.fillRoundRect((int)(x - w/2), (int)(y + h/2 + 8),
                (int)(w * hpFrac), 10, 4, 4); // ความยาวแถบ = สัดส่วนเลือด
        g2.setColor(hpCol.brighter());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect((int)(x - w/2), (int)(y + h/2 + 8), (int)w, 10, 4, 4); // ขอบแถบ
    }
}
