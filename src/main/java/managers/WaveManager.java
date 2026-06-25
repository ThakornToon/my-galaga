package managers;

import core.World;
import core.Updatable;
import enemies.*;
import entities.Player;

import java.util.*;

/**
 * WaveManager — convoy entry system:
 *
 * Convoy formats (each can enter from the LEFT or RIGHT side and from the
 * TOP or BOTTOM edge, all chosen per group):
 *   A) 2-row parallel, one side
 *   B) 2-row parallel, both sides simultaneously
 *   C) 1-row single file, one side
 *   D) 1-row single file, crossover (loops on the far side)
 *
 * Busier waves (wave ≥ 4) spawn MIXED convoys: two enemy types sweep in
 * together as paired, laterally-offset streams that settle into two adjacent
 * rows — two colours flying as one convoy. Earlier waves lean toward two-row
 * formats as the wave count climbs.
 *
 * Enemy-count growth (wave ≥ 6): every type's count grows LINEARLY by 20% of
 * its wave-5 baseline each wave — factor = 1 + 0.2*(wave-5) (wave6 ×1.2,
 * wave10 ×2.0, wave15 ×3.0). The formation only holds one batch's worth at a
 * time, so the surplus is split into sequential BATCHES: the next batch sweeps
 * in (reusing the same formation slots) only once the previous one is cleared.
 * A wave therefore lasts longer as it scales, instead of cramming the screen.
 *
 * 20% swoop chance: snapshot player pos → straight dive through player →
 *   exit bottom → arc back to formation.
 *
 * Speed scaling:
 *   Drone + Boss : base × (1 + 0.05 * floor((wave-1)/5))
 *   Others       : base × 0.75 × (1 + 0.10 * floor((wave-1)/2))
 */
public class WaveManager implements Updatable {

    // ── Formation grid geometry ────────────────────────────────────────────────
    // The slot layout (slotBaseX/slotX/slotY) and its inverse in
    // summonReinforcements MUST share these, or reinforcements land in the wrong
    // slots. Keep them here, not duplicated as literals at each call site.
    private static final int    COLS        = 8;
    private static final double COL_SPACING = 55;
    private static final double ROW_TOP     = 70;
    private static final double ROW_SPACING = 52;

    // A queued spawn. `groupKey` ties members that share one convoy decision
    // (entry side / vertical edge / format). `lane` is 0 for a normal convoy, or
    // 0/1 for the two rows of a mixed convoy — lane 1 enters from the opposite
    // side so the two streams don't overlap on the way in.
    private record SpawnEntry(double time, String type, int row, int col,
                              String groupKey, int lane) {}

    // Convoy format enum
    private enum ConvoyFormat { TWO_ROW_ONE_SIDE, TWO_ROW_BOTH, ONE_ROW_ONE_SIDE, ONE_ROW_CROSSOVER }

    private final World world;
    private final List<SpawnEntry> queue = new ArrayList<>();
    // A wave is split into one or more batches (see buildWave). Each batch is a
    // full convoy set; the next one only enters once the previous is cleared.
    private final List<List<SpawnEntry>> batches = new ArrayList<>();
    private int    batchIndex  = 0;
    private int    spawnIndex  = 0;
    private double spawnTimer  = 0;
    private boolean waveCleared = false;
    private double clearTimer   = 0;
    // Boss-fight reinforcements: cooldown gate + unique group id per summon.
    private double reinforceCooldown = 0;
    private int    reinforceGroup    = 0;

    public WaveManager(World world) {
        this.world = world;
        buildWave(world.wave);
    }

    // ── Wave definition ───────────────────────────────────────────────────────
    private void buildWave(int wave) {
        queue.clear();
        batches.clear();
        batchIndex  = 0;
        spawnIndex  = 0;
        spawnTimer  = 0;
        waveCleared = false;
        // Re-roll convoy formats every wave. Without this the cache kept each
        // group's first-ever decision forever, so a type that happened to roll a
        // dull format on its debut wave was stuck with it for the whole game.
        convoyCache.clear();
        int cols = COLS;

        if (wave < 4) {
            // Early waves: introduce one type (one row) at a time — single batch.
            List<SpawnEntry> b = new ArrayList<>();
            double t = 0;
            addGroup(b, t, "DRONE", 0, cols, 0, "b0_g0");
            if (wave >= 2) { t += 2.2; addGroup(b, t, "ORBIT", 1, cols - 2, 1, "b0_g1"); }
            if (wave >= 3) { t += 2.2; addGroup(b, t, "WAVE",  2, cols - 2, 1, "b0_g2"); }
            batches.add(b);
        } else {
            // Busier waves: two colours sweep in TOGETHER as paired streams that
            // settle into two adjacent rows (Galaga-style). Count grows linearly
            // by 20% of the wave-5 baseline per wave; the surplus spills into
            // additional sequential batches, each a (possibly partial) copy of
            // the wave-5 layout. nBatches = ceil(factor); full batches use the
            // baseline counts, and the final batch uses the leftover fraction.
            double factor   = (wave <= 5) ? 1.0 : 1.0 + 0.20 * (wave - 5);
            int    nBatches = (int) Math.ceil(factor - 1e-9);
            // Split the total EVENLY across the batches (each gets factor/nBatches
            // ≤ 1.0 of the baseline) rather than front-loading full batches and
            // leaving a tiny remainder — that trailing 5-6 enemy batch read as an
            // anticlimax. Equal shares keep every batch a healthy size.
            double frac = factor / nBatches;
            for (int k = 0; k < nBatches; k++) {
                List<SpawnEntry> b = buildMixedBatch(wave, k, frac, cols);
                if (!b.isEmpty()) batches.add(b);
            }
        }

        // Boss rides in at the tail of the LAST batch, every 5th wave.
        if (wave % 5 == 0 && wave > 0 && !batches.isEmpty()) {
            List<SpawnEntry> last = batches.get(batches.size() - 1);
            double t = 0;
            for (SpawnEntry se : last) t = Math.max(t, se.time());
            last.add(new SpawnEntry(t + 2.5, "BOSS", -1, -1, "boss", 0));
        }

        loadBatch(0);
    }

    /**
     * Build one mixed-convoy batch. {@code frac} (0–1] scales every row's member
     * count down from the wave-5 baseline, so the final partial batch carries the
     * leftover < 1.0 share. {@code k} namespaces the convoy keys so each batch
     * re-rolls its own entry formats (variety between batches).
     */
    private List<SpawnEntry> buildMixedBatch(int wave, int k, double frac, int cols) {
        List<SpawnEntry> b = new ArrayList<>();
        String kp = "b" + k + "_";
        double t = 0;

        int aDrone = (int) Math.round(cols       * frac);   // mixA lane0 (row0)
        int aOrbit = (int) Math.round((cols - 2) * frac);   // mixA lane1 (row1)
        int aWave  = (int) Math.round((cols - 2) * frac);   // mixB lane0 (row2)
        int aDive  = (int) Math.round((cols - 3) * frac);   // mixB lane1 (row3)
        int aShoot = (int) Math.round(4          * frac);   // row4

        addMixedConvoy(b, t,        "DRONE", 0, aDrone, 0, "ORBIT", 1, aOrbit, 1, kp + "mixA");
        // 3.6s apart so each mixed pair finishes its symmetric entry before the
        // next colour pair sweeps in (otherwise all four colours overlap
        // mid-screen and the entry reads as clutter).
        t += 3.6;
        addMixedConvoy(b, t,        "WAVE",  2, aWave,  1, "DIVE",  3, aDive,  2, kp + "mixB");
        if (wave >= 5 && aShoot > 0) {
            t += 3.6;
            addGroup(b, t, "SHOOTER", 4, aShoot, 2, kp + "g4");
        }
        return b;
    }

    // Swap the active queue to batch i and reset the per-batch spawn cursor.
    private void loadBatch(int i) {
        queue.clear();
        if (i >= 0 && i < batches.size()) queue.addAll(batches.get(i));
        queue.sort(Comparator.comparingDouble(SpawnEntry::time));
        spawnIndex  = 0;
        spawnTimer  = 0;
        waveCleared = false;
    }

    private void addGroup(List<SpawnEntry> out, double baseTime, String type,
                          int row, int count, int colStart, String groupKey) {
        // 0.18s between members → a flowing follow-the-leader line that is
        // strung out along the path instead of bunched together.
        for (int c = 0; c < count; c++)
            out.add(new SpawnEntry(baseTime + c * 0.18, type, row, colStart + c, groupKey, 0));
    }

    /**
     * Schedule two types to enter together as one mixed convoy. Rather than
     * stacking a solid row of each colour (1-1-1 over 2-2-2), the two types are
     * INTERLEAVED by column within each row (1-2-1-2…), so the formation reads as
     * an alternating two-colour pattern. The two rows share the convoy decision
     * (edge / format) but enter from opposite sides (lanes 0 and 1), and because
     * members spawn in column order each entry stream itself alternates colour.
     */
    private void addMixedConvoy(List<SpawnEntry> out, double baseTime,
                                String typeA, int rowA, int countA, int colStartA,
                                String typeB, int rowB, int countB, int colStartB,
                                String groupKey) {
        for (int c = 0; c < countA; c++) {
            String type = (c % 2 == 0) ? typeA : typeB;          // 1-2-1-2…
            out.add(new SpawnEntry(baseTime + c * 0.18, type, rowA, colStartA + c, groupKey, 0));
        }
        for (int c = 0; c < countB; c++) {
            String type = (c % 2 == 0) ? typeB : typeA;          // 2-1-2-1… (offset → checkerboard)
            out.add(new SpawnEntry(baseTime + c * 0.18, type, rowB, colStartB + c, groupKey, 1));
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @Override
    public void update(double dt) {
        if (world.gameOver) return;
        spawnTimer += dt;

        maybeReinforceBoss(dt);

        while (spawnIndex < queue.size()
                && spawnTimer >= queue.get(spawnIndex).time())
            spawnOne(queue.get(spawnIndex++));

        if (!waveCleared && spawnIndex >= queue.size()
                && world.allOf(Enemy.class).isEmpty()) {
            waveCleared = true;
            // Short pause between batches of the same wave; longer pause before
            // the next wave proper.
            clearTimer  = (batchIndex < batches.size() - 1) ? 1.2 : 2.5;
        }
        if (waveCleared) {
            clearTimer -= dt;
            if (clearTimer <= 0) {
                if (batchIndex < batches.size() - 1) {
                    loadBatch(++batchIndex);     // next batch of THIS wave
                } else {
                    world.wave++;
                    buildWave(world.wave);       // advance to the next wave
                }
            }
        }
    }

    // ── Boss-fight reinforcements ──────────────────────────────────────────────
    // While a boss is on the field, keep the pressure up: if its escort thins to
    // fewer than 5 minions, the boss scrambles 8 fresh ones of random types into
    // the empty formation slots. The cooldown gate stops it from re-triggering
    // every frame while the new wing is still flying in.
    private void maybeReinforceBoss(double dt) {
        if (reinforceCooldown > 0) reinforceCooldown -= dt;
        List<Boss> bosses = world.allOf(Boss.class);
        if (bosses.isEmpty()) return;
        int minions = world.allOf(Enemy.class).size() - bosses.size();
        if (minions < 5 && reinforceCooldown <= 0) {
            summonReinforcements(8);
            reinforceCooldown = reinforceInterval(bosses.get(0));
        }
    }

    /**
     * Reinforcement cooldown: base 4s, minus 0.5s for every 5 waves beyond
     * wave 5, minus a further 1s while the boss is at ≤40% HP (a desperate,
     * faster scramble). Floored at 1s so it can never spam every frame.
     */
    private double reinforceInterval(Boss boss) {
        double cd = 4.0;
        if (world.wave > 5) cd -= 0.5 * ((world.wave - 5) / 5);   // integer step per 5 waves
        double hpFrac = (double) boss.getHp() / boss.getMaxHp();
        if (hpFrac <= 0.40) cd -= 1.0;
        return Math.max(1.0, cd);
    }

    private void summonReinforcements(int n) {
        double xs = slotBaseX();

        // Slots already taken by surviving minions (skip the boss).
        Set<Integer> occupied = new HashSet<>();
        for (Enemy e : world.allOf(Enemy.class)) {
            if (e instanceof Boss) continue;
            int col = (int) Math.round((e.getFormationX() - xs) / COL_SPACING);
            int row = (int) Math.round((e.getFormationY() - ROW_TOP) / ROW_SPACING);
            occupied.add(row * 100 + col);
        }
        // Free slots across the standard 5 formation rows, shuffled.
        List<int[]> slots = new ArrayList<>();
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < COLS; c++)
                if (!occupied.contains(r * 100 + c)) slots.add(new int[]{r, c});
        Collections.shuffle(slots, world.rng());

        String[] types = {"DRONE", "ORBIT", "WAVE", "DIVE", "SHOOTER"};
        String gk = "reinf" + (reinforceGroup++) + "_";
        int placed = Math.min(n, slots.size());
        // Append to the active queue with a follow-the-leader time stagger; the
        // boss keeps the wave from clearing, so these spawn as the timer advances.
        for (int i = 0; i < placed; i++) {
            int[] s = slots.get(i);
            String type = types[world.rng().nextInt(types.length)];
            queue.add(new SpawnEntry(spawnTimer + i * 0.18, type, s[0], s[1], gk, 0));
        }
    }

    // ── Spawn logic ───────────────────────────────────────────────────────────
    // We batch-spawn by row-group. Each call to spawnOne checks if the
    // full group for this (type, row) has been decided yet; if not, pick
    // a ConvoyFormat + entry side that the whole group shares. Each member
    // then builds its OWN path that ends on its OWN slot (see buildMemberPath).

    // Cache of convoy decisions (format + entry side + entry edge): key = groupKey
    private final Map<String, ConvoyDecision> convoyCache = new HashMap<>();

    private record ConvoyDecision(ConvoyFormat format, boolean fromLeft, boolean fromBottom) {}

    // ── Formation slot geometry ────────────────────────────────────────────────
    // Left edge of column 0. summonReinforcements inverts slotX/slotY against
    // this same base, so both live here to stay in sync.
    private double slotBaseX() { return world.width / 2.0 - (COLS - 1) * COL_SPACING / 2.0; }
    private double slotX(int col) { return col >= 0 ? slotBaseX() + col * COL_SPACING : world.width / 2.0; }
    private double slotY(int row) { return row >= 0 ? ROW_TOP + row * ROW_SPACING : 80; }

    private void spawnOne(SpawnEntry se) {
        double formX = slotX(se.col());
        double formY = slotY(se.row());

        // Boss: simple top-centre entry
        if ("BOSS".equals(se.type())) {
            Boss boss = new Boss(world);
            boss.setFormationTarget(formX, formY);
            boss.setWave(world.wave);
            applySpeedMult(boss);
            world.add(boss);
            return;
        }

        // ── Get or create convoy decision for this (type, row) group ─────────
        // The decision (format + entry side) is shared so the group enters as
        // one coherent stream, but every member builds its OWN path that ENDS
        // on its OWN formation slot. Previously the whole group shared a single
        // path ending on the first member's slot, so everyone funneled to that
        // one point and then fanned out — an ugly pile-up right as the convoy
        // reached formation. Per-member end slots let each enemy peel off the
        // shared stream straight into its place (Galaga-style).
        String key = se.groupKey();
        boolean mixed = key.contains("mix");
        ConvoyDecision cd = convoyCache.computeIfAbsent(key, k -> makeConvoyDecision(world.wave, mixed));

        EntryPath path = buildMemberPath(cd, se.col(), se.lane(), formX, formY);

        // ── Follow-the-leader ─────────────────────────────────────────────────
        // Every member flies the SAME path from the very start (off-screen);
        // the spacing comes purely from the staggered spawn TIME (see addGroup).
        // Previously we ALSO offset the start index, which compounded with the
        // time stagger and bunched the whole convoy into one clump.
        int startIndex = 0;

        // ── Swoop: 20% chance, snapshot player ───────────────────────────────
        boolean willSwoop = world.rng().nextDouble() < 0.20;
        double swoopX = world.width / 2.0, swoopY = world.height - 80;
        if (willSwoop) {
            List<Player> ps = world.allOf(Player.class);
            if (!ps.isEmpty()) { swoopX = ps.get(0).getX(); swoopY = ps.get(0).getY(); }
        }

        // ── Create enemy ──────────────────────────────────────────────────────
        // Start position = first point on path at startIndex
        EntryPath.Pt startPt = path.points.get(startIndex);
        Enemy enemy = makeEnemy(se, startPt.x(), startPt.y());
        enemy.setFormationTarget(formX, formY);
        enemy.setWave(world.wave);
        applySpeedMult(enemy);
        enemy.setupEntryPath(path, startIndex, willSwoop, swoopX, swoopY);
        world.add(enemy);
    }

    // ── Convoy decision factory ───────────────────────────────────────────────
    private ConvoyDecision makeConvoyDecision(int wave, boolean mixed) {
        ConvoyFormat fmt;
        if (mixed) {
            // Mixed convoys: each lane is a clean single-file stream, and the two
            // lanes enter from OPPOSITE sides (see buildMemberPath) so their loops
            // sit on opposite halves of the screen instead of piling up together.
            // Colour interleaving is done per column in addMixedConvoy.
            fmt = ConvoyFormat.ONE_ROW_ONE_SIDE;
        } else if (wave >= 2 && world.rng().nextDouble() < 0.5) {
            // Bias busier waves toward two-row formations.
            fmt = world.rng().nextBoolean() ? ConvoyFormat.TWO_ROW_ONE_SIDE
                                            : ConvoyFormat.TWO_ROW_BOTH;
        } else {
            ConvoyFormat[] all = ConvoyFormat.values();
            fmt = all[world.rng().nextInt(all.length)];
        }
        boolean fromLeft   = world.rng().nextBoolean();
        boolean fromBottom = world.rng().nextBoolean();  // enter from the top OR the bottom edge
        return new ConvoyDecision(fmt, fromLeft, fromBottom);
    }

    // ── Per-member entry path ──────────────────────────────────────────────────
    /**
     * Build THIS member's entry path. The lead-in + loop geometry in
     * {@link EntryPath#buildFigure8} depends only on the entry side / edge / row
     * offset (NOT on the slot), so every member of a group flies an identical
     * stream and only the final settle segment diverges — straight into its own
     * slot. {@code lane} laterally offsets the 2nd colour stream of a mixed convoy.
     */
    private EntryPath buildMemberPath(ConvoyDecision cd, int col, int lane,
                                      double formX, double formY) {
        int W = world.width, H = world.height;
        boolean fb = cd.fromBottom();
        // A mixed convoy's two rows ride OPPOSITE sides (lane 1 mirrors lane 0) so
        // their loops land on opposite halves of the screen instead of overlapping.
        // Every non-mixed group has lane 0, so this leaves them unchanged.
        boolean left = (lane == 0) ? cd.fromLeft() : !cd.fromLeft();
        return switch (cd.format()) {
            // Two parallel rows from one side: odd columns ride a laterally
            // offset copy of the same arc.
            case TWO_ROW_ONE_SIDE ->
                    EntryPath.buildFigure8(cd.fromLeft(), fb, formX, formY, W, H,
                                           (col % 2 == 0) ? 0 : 22, false);
            // Two rows fed by both sides: even columns from the left, odd from
            // the right.
            case TWO_ROW_BOTH ->
                    EntryPath.buildFigure8(col % 2 == 0, fb, formX, formY, W, H, 0, false);
            case ONE_ROW_ONE_SIDE ->
                    EntryPath.buildFigure8(left, fb, formX, formY, W, H, 0, false);
            // Crossover: sweep across from the entry corner, loop on the FAR
            // side, then settle — still a full loop flourish, just mirrored.
            case ONE_ROW_CROSSOVER ->
                    EntryPath.buildFigure8(left, fb, formX, formY, W, H, 0, true);
        };
    }

    // ── Enemy factory ─────────────────────────────────────────────────────────
    private Enemy makeEnemy(SpawnEntry se, double sx, double sy) {
        return switch (se.type()) {
            case "DRONE"   -> new Drone(world,         sx, sy, se.row(), se.col());
            case "ORBIT"   -> new OrbitBug(world,      sx, sy, se.row(), se.col());
            case "WAVE"    -> new WaveBug(world,        sx, sy, se.row(), se.col());
            case "DIVE"    -> new DiveBug(world,        sx, sy, se.row(), se.col());
            case "SHOOTER" -> new ShooterEnemy(world,  sx, sy, se.row(), se.col());
            default        -> throw new IllegalArgumentException("Unknown: " + se.type());
        };
    }

    // ── Speed multiplier ──────────────────────────────────────────────────────
    private void applySpeedMult(Enemy enemy) {
        int w = world.wave;
        double mult = (enemy instanceof Drone || enemy instanceof Boss)
                ? 1.0  + 0.05 * ((w - 1) / 5)
                : 0.75 + 0.10 * ((w - 1) / 2);
        enemy.setSpeedMult(mult);
    }
}