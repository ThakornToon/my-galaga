package enemies;

import core.World;
import entities.GhostShip;
import entities.Player;
import patterns.AimedPattern;

import java.awt.*;

/**
 * ShooterEnemy — ศัตรูที่ "จับยาน" ผู้เล่นได้ (รูปยานสีชมพู) — ท่าไม้ตายของ Galaga.
 *
 * มี 4 โหมด (Mode):
 *   IDLE         = อยู่ในช่องขบวนตามปกติ
 *   CAPTURE_BEAM = ลอยนิ่งยิงลำแสงดูดลงหาผู้เล่น
 *   CAPTURE_BOB  = จับยานได้แล้ว ลอยส่ายโชว์พร้อมลากยาน (ghost) กลับช่อง
 *   BOUNCER      = เด้งซ้าย-ขวา พร้อมยิงเล็งเป็นระยะ
 *
 * ถ้าผู้เล่นยิงตัวที่กำลังลากยานเราอยู่ → ได้ยานคืน + ยานคู่ (ดู absorbHit).
 */
public class ShooterEnemy extends Enemy {

    public enum Mode { IDLE, CAPTURE_BEAM, CAPTURE_BOB, BOUNCER }
    private Mode mode = Mode.IDLE;

    private GhostShip ghost = null;               // ยานผู้เล่นที่จับได้ (ลากตามหลัง)
    private static final double GHOST_TRAIL_DIST  = 36; // ระยะที่ ghost ลากห่างจากตัว

    // Tractor beam — ลำแสงดูด
    private static final double BEAM_GROW_SPEED = 320;  // px/sec — ลำแสงยืดเร็วแค่ไหน
    private static final double BEAM_TIMEOUT    = 4.0;  // cancel beam after this long — ยิงเกินเวลานี้ไม่โดน → เลิก
    private double beamLength = 0;                 // ความยาวลำแสงปัจจุบัน
    private double beamTimer  = 0;                 // จับเวลาที่ยิงลำแสงมาแล้ว

    private double bounceShotTimer = 0;            // จับเวลายิงในโหมดเด้ง
    private static final double BOUNCE_SHOT_INTERVAL = 5.0; // ยิงทุกๆ 5 วินาที
    private static final double BOUNCE_SPEED_X       = 140; // ความเร็วเด้งแนวนอน

    public ShooterEnemy(World world, double x, double y) {
        // เลือด 1, คะแนน 250 (สูง — เพราะอันตราย/จับยานได้)
        super(world, x, y, 34, 30, 1, 250, 99,
                new Color(255, 50, 100), new Color(180, 0, 60), new Color(255, 150, 180));
    }

    @Override
    public void triggerDive() {
        if (inEntryPath) return; // ยังเข้าฉากไม่เสร็จ → ห้ามดิ่ง
        // Already towing a captured ghost → don't initiate another steal/dive.
        // Without this, a ShooterEnemy in CAPTURE_BOB is still in formation, so
        // FormationManager can re-trigger it: it would fire a second beam, steal
        // another life, and overwrite `ghost` — orphaning the first ghost (left
        // alive on screen but un-towed and impossible to rescue).
        // กันบั๊ก: ถ้ากำลังลากยานอยู่แล้ว ห้ามดิ่งซ้ำ ไม่งั้นจะยิงลำแสงรอบสอง
        // ขโมยชีวิตอีก แล้วเขียนทับตัวแปร ghost → ghost ตัวเดิมค้างจอ กู้คืนไม่ได้
        if (ghost != null) return;
        Player p = findPlayer();
        boolean playerAlive = (p != null && p.getLives() > 1);
        if (world.rng().nextDouble() < 0.5 && playerAlive) {
            // Stay in place — fire tractor beam downward toward player
            // 50% (ถ้าผู้เล่นเหลือ > 1 ชีวิต): อยู่นิ่งยิงลำแสงดูด
            inFormation = false;
            divePhase   = 0;
            mode        = Mode.CAPTURE_BEAM;
            beamLength  = 0;
            beamTimer   = 0;
            vx = 0;
            vy = 0;
        } else {
            // อีก 50% (หรือผู้เล่นเหลือชีวิตน้อย): เข้าโหมดเด้ง
            super.triggerDive();
            startBouncer();
        }
    }

    private void startBouncer() {
        mode            = Mode.BOUNCER;
        // สุ่มทิศเริ่มเด้ง ซ้าย(-1) หรือ ขวา(+1)
        vx              = BOUNCE_SPEED_X * speedMult * (world.rng().nextBoolean() ? 1 : -1);
        bounceShotTimer = 0;
        inFormation     = false;
    }

    @Override
    protected void updateAI(double dt) {
        if (swoopActive) { runSwoop(dt); return; }

        // ทำงานตามโหมดปัจจุบัน
        switch (mode) {
            case IDLE         -> moveTowardsFormation(0, 0);
            case CAPTURE_BEAM -> handleCaptureBeam(dt);
            case CAPTURE_BOB  -> handleCaptureBob();
            case BOUNCER      -> handleBouncer(dt);
        }

        // Tow ghost behind us while alive. The ghost is in the world's object
        // list, so the game loop already calls ghost.update(dt) — we only steer
        // its position here (calling update() again would double its blink rate).
        // ลากยาน (ghost) ตามหลังตลอดถ้ายังมีชีวิต — ghost อยู่ใน list ของเกมแล้ว
        // เกมจึงเรียก ghost.update(dt) ให้เอง เราแค่ย้ายตำแหน่ง (ถ้าเรียก update
        // ซ้ำ มันจะกระพริบเร็วเป็น 2 เท่า)
        if (ghost != null && ghost.isAlive()) {
            ghost.setPosition(x, y - GHOST_TRAIL_DIST);
        } else if (ghost != null) {
            ghost = null; // ghost ตายไปแล้ว → ปล่อยอ้างอิง
        }
    }

    private void handleCaptureBeam(double dt) {
        vx = 0; vy = 0;   // hover in place — ลอยนิ่ง

        beamTimer += dt;
        Player p = findPlayer();
        if (p == null || beamTimer >= BEAM_TIMEOUT) { // หมดเวลา/ไม่มีผู้เล่น → เลิก กลับช่อง
            startSmoothReturn();
            mode = Mode.IDLE;
            return;
        }

        // Grow beam toward current player position — ลำแสงค่อยๆ ยืดเข้าหาผู้เล่น
        double dx   = p.getX() - x;
        double dy   = p.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy) + 0.001; // ระยะถึงผู้เล่น
        beamLength += BEAM_GROW_SPEED * dt;

        if (beamLength >= dist) { // ลำแสงยาวพอถึงตัวผู้เล่นแล้ว
            // Only steal a life when the player has more than one left — taking
            // their last life this way would be an instant kill, so the beam
            // gives up and returns instead.
            // จับได้เฉพาะตอนผู้เล่นเหลือ > 1 ชีวิต — ถ้าเหลือชีวิตเดียวแล้วจับ
            // จะเท่ากับฆ่าทันที (โหดเกินไป) จึงยอมเลิกแล้วกลับช่อง
            if (p.getLives() <= 1) {
                startSmoothReturn();
                mode = Mode.IDLE;
                return;
            }
            // Beam reached player — capture! — จับสำเร็จ!
            world.spawnParticles(p.getX(), p.getY(), // เอฟเฟกต์อนุภาคตอนดูด
                    new Color(255, 220, 50), 20, 60, 220, 0.4, 1.0, 3, 7);
            world.spawnParticles(p.getX(), p.getY(),
                    new Color(255, 255, 200), 8, 100, 260, 0.2, 0.6, 2, 4);
            p.absorbHit();                                  // ผู้เล่นเสีย 1 ชีวิต
            ghost      = new GhostShip(world, x, y - GHOST_TRAIL_DIST); // สร้างยานผีลากตาม
            world.add(ghost);
            mode        = Mode.CAPTURE_BOB;
            inFormation = true;
            beamLength  = 0;
        }
    }

    private void handleCaptureBob() {
        // จับได้แล้ว: ลอยกลับช่องพร้อมส่ายซ้ายขวาโชว์ (sin ให้การแกว่ง)
        moveTowardsFormation(Math.sin(patternTimer * 1.4) * 45 * speedMult, 0);
    }

    private void handleBouncer(double dt) {
        x += vx * dt; // เคลื่อนแนวนอน
        // ชนขอบจอ → เด้งกลับ (Math.abs บังคับทิศให้สะท้อนถูกด้าน)
        if (x <= w/2)               { x = w/2;               vx =  Math.abs(vx); }
        if (x >= world.width - w/2) { x = world.width - w/2;  vx = -Math.abs(vx); }
        vy = Math.sin(patternTimer * 0.8) * 40 * speedMult; // ส่ายขึ้นลงเบาๆ
        y += vy * dt;
        y  = Math.max(50, Math.min(world.height * 0.55, y)); // จำกัดอยู่ครึ่งบนจอ
        vx = (vx > 0 ? 1 : -1) * BOUNCE_SPEED_X * speedMult;  // คงความเร็วแนวนอนคงที่
        bounceShotTimer += dt;
        if (bounceShotTimer >= BOUNCE_SHOT_INTERVAL) { // ยิงเล็งทุก 5 วินาที
            bounceShotTimer = 0;
            firePattern(new AimedPattern(280 + wave * 8));
        }
    }

    @Override
    public void absorbHit(int damage) {
        if (invulnerable) return;
        // ★ ถ้าตัวนี้กำลังลากยานผู้เล่นอยู่ → ทำลาย ghost แล้วคืนรางวัล
        if (ghost != null && ghost.isAlive()) {
            ghost.destroy(); ghost = null;
            Player p = findPlayer();
            if (p != null) { p.gainLife(); p.awardDualFighter(); } // +1 ชีวิต + ยานคู่
        }
        super.absorbHit(damage); // แล้วค่อยรับดาเมจปกติ (ตามด้วยการตาย)
    }

    /** ยิงโดน ghost โดยตรง (เรียกจาก CollisionManager) → ทำลาย ghost. */
    public void ghostHit() { if (ghost != null) { ghost.destroy(); ghost = null; } }
    public GhostShip getGhost() { return ghost; }

    @Override protected void updateShooting(double dt) {} // ไม่ยิงกระสุนปกติ (ยิงเองในโหมด)

    @Override
    public void draw(Graphics2D g2) {
        // Tractor beam — drawn first (behind the ship body)
        // วาดลำแสงก่อน (ให้อยู่หลังตัวยาน)
        if (mode == Mode.CAPTURE_BEAM && beamLength > 0) {
            Player target = findPlayer();
            if (target != null) {
                double dx   = target.getX() - x;
                double dy   = target.getY() - y;
                double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
                double nx   = dx / dist; // เวกเตอร์ทิศทางหน่วยไปผู้เล่น
                double ny   = dy / dist;
                double draw = Math.min(beamLength, dist); // วาดยาวเท่าที่ยืดได้ (ไม่เกินตัวผู้เล่น)

                // Animated dotted beam — ลำแสงเป็นจุดประวิ่งกระพริบ
                int steps = Math.max(1, (int)(draw / 10));
                for (int i = 0; i <= steps; i++) {
                    double t     = i / (double) steps;
                    double bx    = x + nx * draw * t; // จุดที่ i บนลำแสง
                    double by    = y + ny * draw * t;
                    double phase = ((patternTimer * 10) - i * 0.5) % 1.0; // เฟสกระพริบไหลตามแนว
                    int    a     = (int)(Math.max(0, Math.sin(phase * Math.PI)) * 230); // ความทึบ
                    int    r     = (i % 3 == 0) ? 5 : 3; // จุดใหญ่สลับจุดเล็ก
                    g2.setColor(new Color(255, 220, 50, a));
                    g2.fillOval((int)(bx - r), (int)(by - r), r * 2, r * 2);
                }
                // Glowing nozzle at base of beam — หัวฉายเรืองแสงที่โคนลำแสง
                float pulse = (float)(0.5 + 0.5 * Math.sin(patternTimer * 15)); // เต้นเป็นจังหวะ
                g2.setColor(new Color(255, 240, 100, (int)(pulse * 255)));
                g2.fillOval((int)(x - 6), (int)(y + h / 2 - 5), 12, 12);
                // Charging ring around ship — วงชาร์จรอบตัวยาน
                g2.setColor(new Color(255, 180, 50, (int)(pulse * 160)));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval((int)(x - w/2 - 10), (int)(y - h/2 - 10),
                        (int)(w + 20), (int)(h + 20));
            }
        }

        // Ship body — ตัวยาน (รูปกากบาท: แถบแนวนอน + แถบแนวตั้ง)
        drawGlow(g2);
        g2.setColor(colorSecondary);
        g2.fillRect((int)(x - w/2), (int)(y - h/6), (int)w,     (int)(h / 3)); // แถบนอน
        g2.fillRect((int)(x - w/6), (int)(y - h/2), (int)(w/3), (int)h);       // แถบตั้ง
        g2.setColor(colorPrimary);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect((int)(x - w/2), (int)(y - h/6), (int)w,     (int)(h / 3)); // ขอบแถบนอน
        g2.drawRect((int)(x - w/6), (int)(y - h/2), (int)(w/3), (int)h);       // ขอบแถบตั้ง
        g2.setColor(colorAccent);
        g2.fillOval((int)(x - 6), (int)(y - 6), 12, 12); // แกนกลาง
    }
}
