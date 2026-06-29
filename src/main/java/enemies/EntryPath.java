package enemies;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-computed entry path as a list of 2-D waypoints.
 *
 * Shape: vertical figure-8 (≈ 3/4 of a lemniscate).
 * The path is shared across all members of a convoy.
 * Each member starts at a different index (follow-the-leader).
 *
 * "เส้นทาง" สำเร็จรูปเก็บเป็นรายการจุด 2 มิติ. ไม่ใช่ศัตรู แต่เป็นเครื่องมือ
 * เรขาคณิตที่ศัตรูทุกตัวเรียกใช้เพื่อเคลื่อนที่ตามเส้นโค้ง. หัวใจคือ "ตาราง
 * ระยะสะสม (arc-length)" ที่ทำให้เดินด้วยความเร็วพิกเซลคงที่ ไม่ว่าจุดบนเส้น
 * จะถี่หรือห่าง และไม่ขึ้นกับ frame rate. ใช้ร่วมกันทั้งขบวน โดยแต่ละตัวเริ่ม
 * ที่ index ต่างกัน (วิ่งตามๆ กันแบบ follow-the-leader).
 */
public class EntryPath {

    /** จุดพิกัด 1 จุด (record = คลาสเก็บข้อมูลแบบสั้น มี x,y อ่านอย่างเดียว) */
    public record Pt(double x, double y) {}

    private static final int SAMPLES = 160; // จำนวนจุดตัวอย่างของเส้น swoop

    public final List<Pt> points; // เส้นทาง = ลำดับจุดต่อกัน

    // ── Arc-length table (for constant-speed, interpolated traversal) ─────────
    // ── ตารางระยะสะสม (เพื่อเดินด้วยความเร็วคงที่ พร้อม interpolate) ──────────
    /** cum[i] = total polyline distance from points[0] up to points[i] */
    /** cum[i] = ระยะทางรวมจากจุดแรกถึงจุดที่ i (เหมือนหลักกิโลเมตรริมทาง) */
    private final double[] cum;
    private final double   totalLen; // ความยาวเส้นทั้งหมด (px)

    EntryPath(List<Pt> points) {
        this.points = points;

        // สร้างตารางระยะสะสม: เดินไล่จุด บวกระยะระหว่างจุดที่ติดกันเข้าไปเรื่อยๆ
        int n = points.size();
        cum = new double[Math.max(1, n)];
        double acc = 0;
        for (int i = 1; i < n; i++) {
            Pt a = points.get(i - 1), b = points.get(i);
            acc += Math.hypot(b.x() - a.x(), b.y() - a.y()); // ระยะ a→b (พีทาโกรัส)
            cum[i] = acc;
        }
        totalLen = acc; // ค่าสุดท้าย = ความยาวทั้งเส้น
    }

    /** Total arc length of the path in pixels. */
    /** ความยาวเส้นทั้งหมด (px) */
    double length() { return totalLen; }

    /** Arc-length fraction [0,1] at which raw waypoint {@code i} sits. */
    /** จุดที่ i อยู่ที่ "กี่ %" ของเส้น (0=ต้น, 1=ปลาย) — ใช้จัดระยะ follow-the-leader */
    double fractionAtIndex(int i) {
        if (totalLen <= 1e-9 || points.isEmpty()) return 0;
        i = Math.max(0, Math.min(i, points.size() - 1)); // กัน index เกินขอบ
        return cum[i] / totalLen;
    }

    /**
     * Position at arc-length fraction {@code f} in [0,1], linearly interpolated
     * between the two bracketing waypoints. Because it is parameterised by
     * distance (not by raw index), advancing {@code f} at a constant rate yields
     * a constant pixel speed regardless of how densely waypoints are packed.
     *
     * คืนพิกัดที่เศษส่วน f (0..1) ของเส้น โดยผสมเชิงเส้นระหว่างจุด 2 จุดที่ขนาบ.
     * เพราะอ้างอิงด้วย "ระยะทาง" (ไม่ใช่ลำดับจุด) การเพิ่ม f อย่างสม่ำเสมอจึงได้
     * ความเร็วพิกเซลคงที่ ไม่ว่าจุดจะถี่หรือห่าง.
     */
    Pt atFraction(double f) {
        if (points.isEmpty()) return new Pt(0, 0);
        if (f <= 0 || totalLen <= 1e-9) return points.get(0);     // ต้นเส้น
        if (f >= 1) return points.get(points.size() - 1);         // ปลายเส้น

        double target = f * totalLen; // f% คิดเป็นระยะกี่ px
        // Binary search for the segment [lo, hi] containing `target`.
        // ค้นหาแบบ binary search ว่าระยะ target อยู่ในช่วงจุด [lo, hi] ใด
        // (เร็วเพราะ cum[] เรียงจากน้อยไปมากอยู่แล้ว)
        int lo = 0, hi = cum.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) >>> 1; // >>>1 คือหาร 2
            if (cum[mid] <= target) lo = mid; else hi = mid;
        }
        double segLen = cum[hi] - cum[lo];
        double t = segLen > 1e-9 ? (target - cum[lo]) / segLen : 0; // อยู่ระหว่าง lo-hi กี่ %
        Pt a = points.get(lo), b = points.get(hi);
        return new Pt(a.x() + (b.x() - a.x()) * t,  // ผสมตำแหน่ง a กับ b ตามสัดส่วน t
                      a.y() + (b.y() - a.y()) * t);
    }

    // ── Sweeping screen entrance with a loop (Galaga-style) ──────────────────
    /**
     * Enemies fly IN from off-screen (a screen corner chosen by the entry
     * flags), sweep across a large part of the playfield, perform a single
     * loop flourish, then settle into their formation slot — instead of the
     * old tight loop that sat right on top of the formation.
     *
     * Construction (3 segments, concatenated) — made C1-continuous so there
     * are NO sharp corners where the segments meet:
     *   1. lead-in   : cubic Bézier  off-screen start → loop junction
     *   2. loop      : a full circle  (the flourish)
     *   3. settle    : cubic Bézier  loop junction → formation slot
     *
     * The lead-in arrives, and the settle departs, *along the loop's tangent*
     * at the junction point, so the heading never jumps (curvature stays
     * continuous and the motion reads as one smooth flowing arc).
     *
     * สร้างเส้นเข้าฉากสไตล์ Galaga: บินจากมุมจอนอก → กวาดข้ามจอ → วนลูป 1 รอบ
     * → ร่อนเข้าช่อง. ประกอบจาก 3 ส่วนต่อกันแบบ "C1-continuous" (ไม่มีมุมหัก)
     * เพราะ lead-in เข้าหาวงลูป และ settle ออกจากวงลูป ตาม "เส้นสัมผัสวงกลม"
     * จุดเดียวกัน → ทิศทางต่อเนื่อง ดูลื่นไหลเป็นเส้นเดียว.
     *
     * @param fromLeft   entry from the left edge (false = right edge)
     * @param fromBottom entry from below the screen (false = from above)
     * @param formX      formation slot X
     * @param formY      formation slot Y
     * @param W          screen width
     * @param H          screen height
     * @param loopFar    place the loop on the side OPPOSITE the entry corner, so
     *                   the lead-in sweeps clear across the screen before looping
     *                   (a "crossover" entry that still performs the loop flourish)
     */
    public static EntryPath buildFigure8(boolean fromLeft, boolean fromBottom,
                                         double formX,   double formY,
                                         int W,          int H,
                                         boolean loopFar) {
        final int N = 220; // จำนวนจุดรวมทั้งเส้น
        List<Pt> pts = new ArrayList<>(N);

        double side   = fromLeft ? -1 : 1;       // entry corner side — เข้าจากซ้าย(-1)/ขวา(+1)
        double lside  = loopFar ? -side : side;  // side the loop flourish sits on — ฝั่งที่วางวงลูป

        // Off-screen start corner. — จุดเริ่มนอกจอ (เผื่อ 70px)
        double startX = fromLeft ? -70 : W + 70;
        double startY = fromBottom ? H + 70 : -70;

        // Loop flourish: a circle in the upper-mid playfield on the loop side.
        // วงลูป: วงกลมในครึ่งบนกลางจอ ฝั่ง lside
        double loopCx = W / 2.0 + lside * 75;
        double loopCy = H * 0.40;
        double loopR  = 86;
        double sweepDir = -lside;                // loop direction follows loop side — ทิศหมุนวง
        double sweep    = 2 * Math.PI * sweepDir; // หมุนเต็มวง (2π = 360°)

        // Exit the loop on the SIDE whose tangent points toward the formation
        // (vertically), so the settle RISES out of the loop and then fans gently
        // into the slot. Exiting at the top/bottom (a horizontal tangent) forced
        // slots on the far side to reverse direction — a visible crooked kink.
        // Direction (in y) from the loop toward the slot — exit the loop side
        // whose tangent heads that way so the settle never reverses.
        // เลือกออกจากวงลูปด้านที่เส้นสัมผัสชี้ไปทางช่อง (แนวตั้ง) เพื่อให้ settle
        // ไม่ต้องวกกลับทิศ (เคยเกิดเส้นหักคดถ้าออกผิดด้าน)
        double upY = (formY < loopCy) ? -1 : 1;
        double a0  = (Math.signum(sweepDir) == Math.signum(upY)) ? 0.0 : Math.PI;
        Pt junction = new Pt(loopCx + loopR * Math.cos(a0), // จุดต่อระหว่างวงลูปกับ Bézier
                             loopCy + loopR * Math.sin(a0));
        // Unit tangent of the circle at the junction (points toward the slots).
        // เวกเตอร์เส้นสัมผัสวงกลมที่จุด junction (ชี้ไปทางช่อง)
        double tanX = sweepDir * -Math.sin(a0);
        double tanY = sweepDir *  Math.cos(a0);

        // Lead-in: arrive at the junction ALONG the loop tangent (C1 join).
        // ส่วนนำเข้า: จุดควบคุม Bézier ให้เข้าหา junction ตามเส้นสัมผัส (ต่อเนียน)
        Pt li1 = new Pt(startX + side * 10, (startY + junction.y()) / 2);
        Pt li2 = new Pt(junction.x() - tanX * 280, junction.y() - tanY * 280);

        // Settle: depart the junction ALONG the same tangent (C1 join), fan in.
        // ส่วนร่อนเข้าช่อง: ออกจาก junction ตามเส้นสัมผัสเดียวกัน แล้วโค้งเข้าช่อง
        Pt ex1 = new Pt(junction.x() + tanX * 120, junction.y() + tanY * 120);
        Pt ex2 = new Pt(formX, formY - tanY * 60);
        Pt slot = new Pt(formX, formY);

        // แบ่งจำนวนจุด: ส่วนนำเข้า 34%, วงลูป 40%, ร่อนเข้าช่อง 26%
        int n1 = (int)(N * 0.34), n2 = (int)(N * 0.40), n3 = N - n1 - n2;
        for (int i = 0; i < n1; i++) // ส่วนที่ 1: lead-in (Bézier)
            pts.add(cubic(new Pt(startX, startY), li1, li2, junction, i / (double) n1));
        for (int i = 0; i < n2; i++) { // ส่วนที่ 2: วงกลมเต็มรอบ
            double a = a0 + sweep * (i / (double) n2);
            pts.add(new Pt(loopCx + loopR * Math.cos(a), loopCy + loopR * Math.sin(a)));
        }
        for (int i = 0; i < n3; i++) // ส่วนที่ 3: settle (Bézier)
            pts.add(cubic(junction, ex1, ex2, slot, i / (double) (n3 - 1)));

        // Force last waypoint exactly on the formation slot.
        // บังคับจุดสุดท้ายให้ตรงช่องเป๊ะ (กันคลาดเคลื่อนจากการ sample)
        pts.set(pts.size() - 1, slot);

        return new EntryPath(pts);
    }

    /** Cubic Bézier point at parameter t. */
    /** คำนวณจุดบนเส้นโค้ง Cubic Bézier ที่พารามิเตอร์ t (ผสม 4 จุดตามน้ำหนัก) */
    private static Pt cubic(Pt p0, Pt p1, Pt p2, Pt p3, double t) {
        double u = 1 - t;
        double x = u*u*u*p0.x() + 3*u*u*t*p1.x() + 3*u*t*t*p2.x() + t*t*t*p3.x();
        double y = u*u*u*p0.y() + 3*u*u*t*p1.y() + 3*u*t*t*p2.y() + t*t*t*p3.y();
        return new Pt(x, y);
    }

    // ── Swoop path ────────────────────────────────────────────────────────────
    /**
     * Straight dive at snapshotted player pos → exits bottom → Bézier arc
     * back up to formation.
     *
     * เส้นพุ่งโจมตี (swoop): ดิ่งตรงผ่านตำแหน่งผู้เล่นที่ถ่ายรูปไว้ → ทะลุลงใต้จอ
     * → โค้ง Bézier วกกลับขึ้นช่องฟอร์เมชัน.
     */
    static EntryPath buildSwoop(double startX, double startY,
                                       double playerX, double playerY,
                                       double formX,  double formY,
                                       int W, int H) {
        List<Pt> pts = new ArrayList<>(SAMPLES);

        // Phase 1 (45%): straight line through player, exiting bottom
        // เฟส 1 (45%): เส้นตรงพุ่งผ่านผู้เล่น ออกใต้จอ
        int ph1 = SAMPLES * 45 / 100;
        // Direction vector from start toward player — เวกเตอร์ทิศทางไปผู้เล่น
        double dx  = playerX - startX;
        double dy  = playerY - startY;
        double len = Math.sqrt(dx*dx + dy*dy) + 0.001; // +กันหารศูนย์
        double ux  = dx / len; // ทิศทางหน่วย
        double uy  = dy / len;
        // Exit point: extrapolate past player until y > H + 60
        // จุดออก: ยืดเส้นเลยผู้เล่นไปจน y > ใต้จอ
        double exitDist = (H + 80 - startY) / (uy + 0.001);
        double exitX = startX + ux * exitDist;
        double exitY = H + 80;

        for (int i = 0; i < ph1; i++) {
            double t  = i / (double)(ph1 - 1);
            pts.add(new Pt(startX + ux * exitDist * t,
                    startY + uy * exitDist * t));
        }

        // Phase 2 (55%): Bézier arc from exit → formation
        // เฟส 2 (55%): โค้ง Bézier จากจุดออก กลับขึ้นช่อง
        int ph2 = SAMPLES - ph1;
        // Control points: loop out to the side then sweep up
        // จุดควบคุม: วกออกข้างก่อนแล้วกวาดขึ้น
        double side  = exitX > W / 2.0 ? -1 : 1; // วกออกด้านที่ใกล้ขอบ
        double cp1x  = exitX + side * W * 0.25;
        double cp1y  = H + 60;
        double cp2x  = formX + side * 40;
        double cp2y  = formY + 80;

        for (int i = 0; i < ph2; i++) {
            double t  = i / (double)(ph2 - 1);
            double u  = 1 - t;
            double px = u*u*u*exitX + 3*u*u*t*cp1x + 3*u*t*t*cp2x + t*t*t*formX;
            double py = u*u*u*exitY + 3*u*u*t*cp1y + 3*u*t*t*cp2y + t*t*t*formY;
            pts.add(new Pt(px, py));
        }
        pts.set(pts.size() - 1, new Pt(formX, formY)); // จุดสุดท้ายให้ตรงช่องเป๊ะ

        return new EntryPath(pts);
    }
}
