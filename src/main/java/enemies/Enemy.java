package enemies;

import core.World;
import entities.GameObject;
import entities.Player;
import entities.Bullet;
import patterns.BulletPattern;
import patterns.StraightPattern;

import java.awt.*;

/**
 * Enemy — คลาสแม่แบบ(abstract)ของศัตรูทุกตัว สืบทอดจาก GameObject.
 *
 * ใช้รูปแบบ "Template Method": update() เขียนลำดับการทำงานทั้งหมดไว้แล้ว เว้น
 * ช่องว่าง updateAI() (abstract) ให้ลูกคลาสแต่ละชนิดมาเติม "นิสัยเฉพาะตัว".
 *
 * วงจรชีวิตของศัตรู 1 ตัว ถูกควบคุมด้วยธงสถานะ 3 ตัว:
 *   inEntryPath          → กำลังบินเข้าฉาก (เดินตาม entryPath)
 *   returningToFormation → กำลังกลับเข้าช่อง (เดินตาม returnPath)
 *   inFormation          → อยู่ในช่องขบวน (พร้อมถูกสั่งดิ่ง)
 * เมื่อไม่ติดธงเข้าฉาก/กลับช่อง update() จะเรียก updateAI() ทำงาน (ปกติ/ดิ่ง).
 */
public abstract class Enemy extends GameObject {
    protected final Color colorPrimary, colorSecondary, colorAccent; // สี 3 ระดับ (ตั้งครั้งเดียว)
    protected int hp, maxHp;          // เลือดปัจจุบัน / เลือดเต็ม
    private int scoreValue;           // คะแนนเมื่อถูกฆ่า
    protected double formationX, formationY; // ตำแหน่งช่องประจำตัวในขบวน
    protected boolean inFormation = true;    // อยู่ในช่องขบวนหรือไม่
    protected double divePhase;       // นาฬิกาจับเวลาตอนดิ่ง (รีเซ็ตทุกครั้งที่เริ่มดิ่ง)
    protected double patternTimer;    // นาฬิกาเดินตลอด ไม่รีเซ็ต (ป้อน sin/cos ให้แกว่ง)
    protected double shootTimer, shootCooldown; // จับเวลายิง / ต้องรอกี่วินาทีถึงยิงได้
    protected boolean invulnerable;   // อมตะชั่วคราว (เช่น บอสตอนเปลี่ยนเฟส)
    private double invulnerableTimer;  // อีกกี่วินาทีหมดฤทธิ์อมตะ
    protected int wave;               // ด่านปัจจุบัน (ใช้ปรับความยาก)
    protected double speedMult = 1.0; // ตัวคูณความเร็วรวม (ระดับความยาก)

    // ── Path travel speeds (pixels / second) ────────────────────────────────
    // Paths are walked by real arc-length distance and interpolated between
    // waypoints, so movement is frame-rate independent and the pixel speed is
    // constant — both within a path and across paths of different lengths.
    // ── ความเร็วเดินเส้นทาง (px/วินาที) ──
    // เดินตามเส้นด้วย "ระยะทางจริง" (arc-length) จึงได้ความเร็วพิกเซลคงที่
    // ไม่ขึ้นกับ frame rate และไม่ขึ้นกับว่าเส้นจะสั้นหรือยาว
    private static final double ENTRY_SPEED  = 400.0; // เข้าฉาก
    private static final double SWOOP_SPEED  = 460.0; // พุ่งโจมตี (เร็วสุด = ดุดัน)
    private static final double RETURN_SPEED = 380.0; // กลับช่อง (ช้าสุด = สบายๆ)

    // ── Entry path (follow-the-leader) ──────────────────────────────────────
    protected boolean inEntryPath = true; // กำลังบินเข้าฉากหรือไม่
    private EntryPath entryPath = null;    // เส้นทางเข้าฉาก (ใช้ร่วมกันทั้งขบวน)
    // progress along entryPath as an arc-length fraction in [0,1]
    private double pathT = 0.0; // เดินมาแล้วกี่ % ของเส้น (0=ต้น, 1=ปลาย)

    // ── Swoop state (post-entry) ─────────────────────────────────────────────
    private boolean willSwoop   = false;  // "จะ" พุ่งต่อทันทีหลังเข้าฉากไหม
    protected boolean swoopActive = false; // "กำลัง" พุ่งอยู่ไหม (ลูกคลาสเช็คใน updateAI)
    private EntryPath swoopPath = null;
    private double swoopT        = 0.0;
    private double swoopTargetX, swoopTargetY; // เป้าที่จะพุ่งใส่

    // ── Smooth return to formation ───────────────────────────────────────────
    private boolean returningToFormation = false; // กำลังกลับเข้าช่องไหม
    private EntryPath returnPath = null;
    private double returnT        = 0.0;

    protected Enemy(World world, double x, double y, double w, double h,
                    int hp, int scoreValue, double shootCooldown,
                    Color primary, Color secondary, Color accent) {
        super(world, x, y, w, h);
        this.hp           = hp;
        this.maxHp        = hp;          // เกิดมาเลือดเต็ม → maxHp = hp
        this.scoreValue   = scoreValue;
        this.shootCooldown = shootCooldown;
        this.colorPrimary  = primary;
        this.colorSecondary = secondary;
        this.colorAccent   = accent;
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setFormationTarget(double fx, double fy) { formationX = fx; formationY = fy; }
    public double getFormationX()  { return formationX; }
    public double getFormationY()  { return formationY; }
    public void setWave(int w)     {
        this.wave = w;
        // Difficulty scaling: every full 10 waves, every enemy gains +1 HP.
        // ปรับความยาก: ทุกๆ 10 ด่านเต็ม ศัตรูทุกตัวได้เลือด +1 (บอส override สูตรนี้)
        int bonus = w / 10;
        if (bonus > 0) { hp += bonus; maxHp += bonus; }
    }
    public boolean isInFormation() { return inFormation; }
    public int  getScoreValue()    { return scoreValue; }
    public void setSpeedMult(double m) { speedMult = m; }

    // ── Entry path setup ─────────────────────────────────────────────────────
    /**
     * Assign a shared EntryPath and this enemy's starting index in it.
     * startIndex lets each member of the convoy be offset (follow-the-leader).
     *
     * ผูกเส้นทางเข้าฉาก (ใช้ร่วมกันทั้งขบวน) พร้อมกำหนดจุดเริ่มของตัวเองด้วย
     * startIndex เพื่อให้สมาชิกในขบวนวิ่งตามๆ กันแบบ follow-the-leader.
     */
    public void setupEntryPath(EntryPath path, int startIndex,
                               boolean willSwoop,
                               double swoopX, double swoopY) {
        this.entryPath  = path;
        this.inEntryPath = true;
        this.willSwoop   = willSwoop;
        this.swoopTargetX = swoopX;
        this.swoopTargetY = swoopY;
        // Convert the follow-the-leader start index into an arc-length fraction
        // so trailing members keep a constant pixel gap behind the leader.
        // แปลง index เป็นเศษส่วนระยะทาง เพื่อให้ลูกขบวนเว้นระยะพิกเซลคงที่
        this.pathT = path.fractionAtIndex(startIndex);
        if (!path.points.isEmpty()) {
            EntryPath.Pt p0 = path.atFraction(pathT);
            x = p0.x(); // วาร์ปไปจุดเริ่มของตัวเองบนเส้น
            y = p0.y();
        }
    }

    // ── Dive ─────────────────────────────────────────────────────────────────
    public void triggerDive() {
        if (inEntryPath) return; // ยังเข้าฉากไม่เสร็จ → ห้ามดิ่ง (กันพฤติกรรมซ้อน)
        inFormation = false;     // ปลดออกจากช่อง → update() จะเข้าทาง updateAI()
        divePhase   = 0;         // รีเซ็ตนาฬิกาดิ่ง
    }

    // ── Smooth return (called by subclass when dive ends) ────────────────────
    /** สร้างเส้นโค้ง Bézier สั้นๆ จากตำแหน่งปัจจุบันกลับช่อง (ลูกคลาสเรียกตอนดิ่งจบ) */
    protected void startSmoothReturn() {
        returningToFormation = true;
        inFormation          = false;
        // Build a short Bézier from current position back to formation
        double tx  = formationX + world.getFormationOffset(); // ปลายทาง = ช่อง (+ การส่ายขบวน)
        double ty  = formationY;
        // Control points: curve up then drop into slot
        // จุดควบคุม: โค้งเชิดขึ้นก่อนแล้วร่อนลงช่อง (ดูสวยงาม)
        double cp1x = x + (tx - x) * 0.3;
        double cp1y = y - 80;
        double cp2x = tx;
        double cp2y = ty - 60;
        int    N    = 50; // สุ่ม 50 จุดบนเส้นโค้ง
        java.util.List<EntryPath.Pt> pts = new java.util.ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            double t  = i / (double)(N - 1);
            double u  = 1 - t;
            // สูตร Cubic Bézier ผสม 4 จุด: เริ่ม(x,y), ควบคุม1, ควบคุม2, ปลาย(tx,ty)
            double px = u*u*u*x   + 3*u*u*t*cp1x + 3*u*t*t*cp2x + t*t*t*tx;
            double py = u*u*u*y   + 3*u*u*t*cp1y + 3*u*t*t*cp2y + t*t*t*ty;
            pts.add(new EntryPath.Pt(px, py));
        }
        returnPath  = new EntryPath(pts);
        returnT     = 0.0;
    }

    // ── Damage ───────────────────────────────────────────────────────────────
    /** รับดาเมจ: หักเลือด ถ้าหมดเลือดก็ตาย (ระเบิด+คะแนน+ดรอปไอเทม) */
    public void absorbHit(int damage) {
        if (invulnerable) return; // อมตะอยู่ → กระสุนทะลุ ไม่เจ็บ
        hp -= damage;
        if (hp <= 0) { // ── เลือดหมด = ตาย ──
            onDeath();                                          // hook ให้ลูกคลาส (รางวัล/ตอบโต้)
            world.spawnExplosion(x, y, colorPrimary);           // เอฟเฟกต์ระเบิด
            world.addScore(scoreValue * world.getScoreMultiplier()); // บวกคะแนน (× ตัวคูณ)
            world.onEnemyKilled();                              // แจ้งเกม (นับ combo ฯลฯ)
            world.maybeDropPowerUp(x, y);                       // อาจดรอป power-up
            destroy();                                          // ติดธงตาย (เกมเก็บกวาดเอง)
        } else { // ── ยังไม่ตาย ──
            world.spawnHitSparks(x, y, colorPrimary);           // แค่สะเก็ดไฟ
        }
    }

    protected void onDeath() {} // ว่างไว้ — ลูกคลาส override เพื่อทำสิ่งพิเศษตอนตาย

    protected void startInvulnerability(double d) { invulnerable = true; invulnerableTimer = d; }
    private void tickInvulnerability(double dt) { // นับถอยหลังฤทธิ์อมตะทุกเฟรม
        if (invulnerable) { invulnerableTimer -= dt; if (invulnerableTimer <= 0) invulnerable = false; }
    }

    // ── Formation snap ───────────────────────────────────────────────────────
    /**
     * ดึงศัตรูเข้าหาช่องแบบ "สปริง": ความเร็ว = ระยะห่าง × 5 (ยิ่งไกลยิ่งเร่ง
     * ใกล้แล้วช้าลงเอง ถึงช่องก็หยุด). extraVx/Vy = การแกว่งเสริมให้ดูมีชีวิต.
     */
    protected void moveTowardsFormation(double extraVx, double extraVy) {
        double tx = formationX + world.getFormationOffset(); // ช่อง X (+ การส่ายของขบวน)
        double ty = formationY;
        vx = (tx - x) * 5 + extraVx;
        vy = (ty - y) * 5 + extraVy;
    }

    // ── Bullet helpers ───────────────────────────────────────────────────────
    /** ยิงกระสุนตาม "รูปแบบ" (BulletPattern) ที่ส่งมา */
    protected void firePattern(BulletPattern p) {
        for (Bullet b : p.createBullets(world, x, y, false)) world.add(b);
    }
    protected Player findPlayer() { // ขอตำแหน่งผู้เล่นจากโลก
        return world.player();
    }
    protected void drawGlow(Graphics2D g2) {
        // Two-tier halo. The body is drawn opaque right after this, so only the
        // part that extends BEYOND the body stays visible — the old +4px / a=40
        // glow was almost entirely covered and read as nearly invisible. Reach
        // ~10px past the body so a real glow ring shows.
        // ออร่า 2 ชั้น: ตัวศัตรูถูกวาดทับทันทีหลังจากนี้ จึงเห็นเฉพาะส่วนที่ล้น
        // ออกนอกตัว (วงเดิม +4px จางเกือบมองไม่เห็น เลยขยายเป็น ~10px)
        int cr = colorPrimary.getRed(), cg = colorPrimary.getGreen(), cb = colorPrimary.getBlue();
        g2.setColor(new Color(cr, cg, cb, 38)); // ชั้นนอก (จางกว่า กว้างกว่า)
        g2.fillOval((int)(x-w/2-10),(int)(y-h/2-10),(int)(w+20),(int)(h+20));
        g2.setColor(new Color(cr, cg, cb, 75)); // ชั้นใน (เข้มกว่า แคบกว่า)
        g2.fillOval((int)(x-w/2-5),(int)(y-h/2-5),(int)(w+10),(int)(h+10));
    }

    // ── Main update ──────────────────────────────────────────────────────────
    /** ★ หัวใจ: game loop เรียกทุกเฟรม → เลือกทำงานตามสถานะปัจจุบัน */
    @Override
    public void update(double dt) {
        patternTimer += dt;       // นาฬิกาเดินตลอด
        tickInvulnerability(dt);  // นับถอยหลังฤทธิ์อมตะ

        if (inEntryPath) {        // โหมด 1: กำลังบินเข้าฉาก
            updateEntryFollow(dt);
            return;               // ★ return = เฟรมนี้ทำแค่นี้ (ไม่ใช้ AI ไม่ยิง)
        }

        if (returningToFormation) { // โหมด 2: กำลังกลับเข้าช่อง
            updateSmoothReturn(dt);
            return;
        }

        // โหมด 3: ปกติ (อยู่ช่อง หรือ กำลังดิ่ง)
        divePhase += dt;
        updateAI(dt);        // ★★ นิสัยเฉพาะตัว (ลูกคลาสเขียน)
        applyVelocity(dt);   // เคลื่อนจริงตาม vx,vy
        updateShooting(dt);  // ลองยิง
    }

    // ── Follow-the-leader path traversal ────────────────────────────────────
    /** เดินตามเส้นเข้าฉาก: เลื่อน pathT ตามระยะพิกเซล แล้ว interpolate ตำแหน่ง */
    private void updateEntryFollow(double dt) {
        if (entryPath == null || entryPath.points.isEmpty()) { // ไม่มีเส้น → ข้ามไปช่องเลย
            inEntryPath = false; inFormation = true; return;
        }
        // Advance along the path by real pixel distance, then interpolate.
        // เลื่อนไปกี่ % = (ความเร็ว px/วิ × dt) ÷ ความยาวเส้น
        double len = entryPath.length();
        pathT += (len > 1e-6) ? (ENTRY_SPEED * speedMult * dt) / len : 1.0;
        pathT  = Math.min(pathT, 1.0); // ไม่ให้เกิน 100%

        EntryPath.Pt pt = entryPath.atFraction(pathT);
        x = pt.x();
        y = pt.y();
        vx = 0; vy = 0; // ★ คุมตำแหน่งตรงๆ ไม่ใช้ velocity ตอนเดินเส้น

        // Swoop trigger at 60% of the entry path (before reaching formation slot)
        if (willSwoop && pathT >= 0.6) {
            willSwoop   = false;
            inEntryPath = false;
            swoopPath = EntryPath.buildSwoop(x, y,
                    swoopTargetX, swoopTargetY,
                    formationX, formationY,
                    world.width, world.height);
            swoopActive = true;
            swoopT      = 0.0;
            return;
        }

        // Reached end of path — ถึงปลายเส้น (เข้าช่องแล้ว)
        if (pathT >= 1.0) {
            inEntryPath = false;
            inFormation = true;
            // No hard snap: the path already ends on the slot, and
            // moveTowardsFormation eases the enemy into its (swaying) slot —
            // teleporting by the current formation offset looked jarring.
        }
    }

    // ── Post-entry swoop traversal ───────────────────────────────────────────
    /** เดินตามเส้นพุ่งโจมตี (ลูกคลาสเรียกเองใน updateAI ผ่าน if (swoopActive)) */
    protected void runSwoop(double dt) {
        if (!swoopActive || swoopPath == null) return;
        double len = swoopPath.length();
        swoopT += (len > 1e-6) ? (SWOOP_SPEED * speedMult * dt) / len : 1.0;
        swoopT  = Math.min(swoopT, 1.0);
        EntryPath.Pt pt = swoopPath.atFraction(swoopT);
        x = pt.x(); y = pt.y(); vx = 0; vy = 0;
        if (swoopT >= 1.0) { // พุ่งจบ → เคลียร์สถานะ กลับเข้าช่อง
            swoopActive = false;
            swoopT      = 0.0;
            swoopPath   = null;
            inFormation = true;
        }
    }

    // ── Smooth return traversal ──────────────────────────────────────────────
    /** เดินตามเส้นกลับช่อง (สร้างไว้โดย startSmoothReturn) */
    private void updateSmoothReturn(double dt) {
        if (returnPath == null) { returningToFormation = false; inFormation = true; return; }
        double len = returnPath.length();
        returnT += (len > 1e-6) ? (RETURN_SPEED * speedMult * dt) / len : 1.0;
        returnT  = Math.min(returnT, 1.0);
        EntryPath.Pt pt = returnPath.atFraction(returnT);
        x = pt.x(); y = pt.y(); vx = 0; vy = 0;
        if (returnT >= 1.0) { // ถึงช่องแล้ว → เคลียร์สถานะกลับ
            returningToFormation = false;
            inFormation          = true;
            returnPath           = null;
            returnT              = 0.0;
            // Hand off to moveTowardsFormation (no teleport) for a smooth settle.
            // ส่งต่อให้ moveTowardsFormation พาเข้าช่องนุ่มๆ (ไม่วาร์ป)
        }
    }

    // ── Subclass hooks ───────────────────────────────────────────────────────
    protected abstract void updateAI(double dt); // ★ ลูกคลาส "ต้อง" เขียนเอง (นิสัย)

    /** การยิงเริ่มต้น: ยิงเมื่อถึงคูลดาวน์ และไม่ยิงถ้าอยู่ล่างจอ (ลูกคลาสอาจ override ปิด) */
    protected void updateShooting(double dt) {
        shootTimer += dt;
        Player player = findPlayer();
        // ไม่ยิงถ้าไม่มีผู้เล่น หรืออยู่ล่างจอเกิน 85% (ใกล้ผู้เล่นเกินไป = ไม่แฟร์)
        if (player == null || y >= world.height * 0.85) return;
        if (shootTimer >= shootCooldown) {
            shootTimer    = 0;
            // ด่านสูง ยิงถี่ขึ้น แต่ไม่ต่ำกว่า 0.5 วินาที
            shootCooldown = Math.max(0.5, shootCooldown - wave * 0.04);
            fireDefaultPattern();
        }
    }
    private void fireDefaultPattern() {
        firePattern(new StraightPattern(260 + wave * 8)); // กระสุนตรง เร็วขึ้นตามด่าน
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public Color getColorPrimary()   { return colorPrimary;   }
    public int   getHp()             { return hp;             }
    public int   getMaxHp()          { return maxHp;          }
}
