# GALAGA — OOP Edition
> Java arcade shooter ที่ได้แรงบันดาลใจจากเกม Galaga / Galaga '88 สร้างด้วย Java Swing + AWT

---

## สารบัญ
1. [ภาพรวมโปรเจกต์](#1-ภาพรวมโปรเจกต์)
2. [โครงสร้างไฟล์](#2-โครงสร้างไฟล์)
3. [การ Build & Run](#3-การ-build--run)
4. [การควบคุม](#4-การควบคุม)
5. [สถาปัตยกรรม (Architecture)](#5-สถาปัตยกรรม-architecture)
6. [ระบบ Rendering](#6-ระบบ-rendering)
7. [ระบบ World & Object Management](#7-ระบบ-world--object-management)
8. [ระบบ Entity](#8-ระบบ-entity)
9. [ศัตรูทุกประเภท](#9-ศัตรูทุกประเภท)
10. [ระบบ Entry Path (Follow-the-Leader)](#10-ระบบ-entry-path-follow-the-leader)
11. [ระบบ Bullet Pattern](#11-ระบบ-bullet-pattern)
12. [ระบบ Power-Up](#12-ระบบ-power-up)
13. [ระบบ Special Ammo](#13-ระบบ-special-ammo)
14. [ระบบ Wave & Difficulty Scaling](#14-ระบบ-wave--difficulty-scaling)
15. [Mechanic พิเศษ](#15-mechanic-พิเศษ)
16. [ระบบ Effects & Particles](#16-ระบบ-effects--particles)
17. [ระบบ Managers](#17-ระบบ-managers)
18. [Flow ของเกม](#18-flow-ของเกม)
19. [ค่า Constants สำคัญ](#19-ค่า-constants-สำคัญ)
20. [ความน่าจะเป็น & ตัวคูณทั้งหมด](#20-ความน่าจะเป็น--ตัวคูณทั้งหมด)
21. [Design Patterns ที่ใช้](#21-design-patterns-ที่ใช้)
22. [Changelog](#22-changelog)

---

## 1. ภาพรวมโปรเจกต์

| รายการ | รายละเอียด |
|---|---|
| ภาษา | Java 21+ |
| Build Tool | Gradle 8.14 |
| UI / Rendering | Java AWT (`Canvas` + `BufferStrategy`) |
| Game Loop | Dedicated Thread (ไม่ใช่ Swing Timer) |
| Target FPS | refresh rate × 2 (auto-detect จอ) |
| ขนาดหน้าจอ | 600 × 760 px (fixed, non-resizable) |
| จำนวนไฟล์ Java | 25 ไฟล์ (~3,359 บรรทัด) |
| Sound Assets | 4 ไฟล์ `.wav` ใน resources |
| Design Patterns | Strategy (BulletPattern), Template Method (Enemy AI), Observer-lite (World flush) |

---

## 2. โครงสร้างไฟล์

```
Galaga/
├── build.gradle                          # Gradle build config (Java plugin)
├── settings.gradle
├── src/
│   └── main/
│       ├── java/
│       │   ├── Main.java                 # entry point — สร้าง JFrame + GamePanel
│       │   ├── core/
│       │   │   ├── Collidable.java       # interface: getBounds(), isAlive()
│       │   │   ├── Drawable.java         # interface: draw(Graphics2D)
│       │   │   ├── Updatable.java        # interface: update(double dt)
│       │   │   ├── GamePanel.java        # Canvas หลัก: game loop thread, render, input
│       │   │   └── World.java            # container + factory: objects, particles, score
│       │   ├── effects/
│       │   │   └── StarField.java        # 120 ดาวเคลื่อนที่ parallax พื้นหลัง
│       │   ├── entities/
│       │   │   ├── GameObject.java       # abstract base: x,y,w,h,vx,vy,rotation,alive
│       │   │   ├── Player.java           # ยานผู้เล่น + power-up state + special ammo
│       │   │   ├── Bullet.java           # กระสุนทั้งหมด (player/enemy/special AoE)
│       │   │   ├── Particle.java         # อนุภาค explosion/spark แบบ fire-and-forget
│       │   │   ├── PowerUp.java          # ไอเทม power-up ตกจากศัตรู (5 ประเภท)
│       │   │   ├── EnergyWave.java       # คลื่นพลัง cone ของ DiveBug
│       │   │   ├── BlastRing.java        # วงแสงแสดง AoE radius ของ special bullet
│       │   │   ├── GhostShip.java        # ยานผู้เล่นที่ถูก ShooterEnemy จับ
│       │   │   └── DualFighter.java      # ยานเสริมสีเขียวหลัง rescue GhostShip
│       │   ├── enemies/
│       │   │   ├── Enemy.java            # abstract base: HP, entry path, dive, return
│       │   │   ├── EntryPath.java        # pre-computed arc-length waypoint paths
│       │   │   ├── Drone.java            # ม่วง — ยิง straight, dive ลงตรง
│       │   │   ├── OrbitBug.java         # ฟ้า — orbit dive, ไม่ยิง
│       │   │   ├── WaveBug.java          # เหลือง — wave convoy, ยิงตอนตาย
│       │   │   ├── DiveBug.java          # ส้ม — advance→pause→EnergyWave→return
│       │   │   ├── ShooterEnemy.java     # แดง — Capture หรือ Bouncer mode
│       │   │   └── Boss.java             # hexagon boss, 3 phases, compounding HP
│       │   ├── managers/
│       │   │   ├── WaveManager.java      # spawn queue, convoy, batch, reinforcements
│       │   │   ├── FormationManager.java # oscillate offset, dive trigger interval
│       │   │   ├── CollisionManager.java # AABB: 7 collision categories
│       │   │   └── HudRenderer.java      # score, lives, wave, power-up, special ammo
│       │   └── patterns/
│       │       ├── BulletPattern.java    # interface: createBullets(...)
│       │       ├── StraightPattern.java  # 1 กระสุนตรง
│       │       ├── AimedPattern.java     # 1 กระสุน aimed ที่ player
│       │       ├── SpreadPattern.java    # N กระสุนกระจาย
│       │       ├── CircularBurstPattern.java # N กระสุนวงกลม 360°
│       │       └── DoubleStraightPattern.java # 2 กระสุนตรง parallel
│       └── resources/
│           ├── laser.wav
│           ├── End_Fallen_Down_(Reprise).wav
│           ├── Menu_Once_Upon_a_Time.wav
│           └── Play_Battle_Against_a_True_Hero.wav
└── build/
    └── libs/
        └── Galaga-1.0-SNAPSHOT.jar
```

---

## 3. การ Build & Run

### ความต้องการ
- **JDK 21** ขึ้นไป (ใช้ record, switch expression, sealed/pattern matching)
- **Gradle 8.14** (หรือใช้ `./gradlew` ที่แนบมาแทนได้)

### Build ด้วย Gradle (แนะนำ)

```bash
# คอมไพล์
./gradlew build

# รัน
./gradlew run

# หรือรัน JAR โดยตรง
java -jar build/libs/Galaga-1.0-SNAPSHOT.jar
```

### Build ด้วย javac (manual)

```bash
# จาก root ของโปรเจกต์
javac -d out -sourcepath src/main/java \
  src/main/java/core/*.java \
  src/main/java/entities/*.java \
  src/main/java/enemies/*.java \
  src/main/java/patterns/*.java \
  src/main/java/managers/*.java \
  src/main/java/effects/*.java \
  src/main/java/Main.java

# รัน (ต้องอยู่ที่ root เพื่อ classpath ถูกต้อง)
java -cp out Main
```

### โครงสร้าง package

| Package | Directory |
|---|---|
| (default) | `src/main/java/` — ไฟล์ `Main.java` |
| `core` | `src/main/java/core/` |
| `entities` | `src/main/java/entities/` |
| `enemies` | `src/main/java/enemies/` |
| `patterns` | `src/main/java/patterns/` |
| `managers` | `src/main/java/managers/` |
| `effects` | `src/main/java/effects/` |

---

## 4. การควบคุม

| ปุ่ม | หน้าจอ | การทำงาน |
|---|---|---|
| `←` / `A` | PLAYING | เคลื่อนยานซ้าย (280 px/sec) |
| `→` / `D` | PLAYING | เคลื่อนยานขวา (280 px/sec) |
| `SPACE` / `Z` | PLAYING | ยิงกระสุนปกติ (hold to fire) |
| `SHIFT` | PLAYING | ยิงกระสุนพิเศษ AoE (ต้องมี Special Ammo จาก Boss) |
| `ESC` | PLAYING | Pause |
| `ESC` | PAUSED | Resume |
| `R` | PAUSED | กลับหน้าเมนู (รีเซ็ตเกม) |
| `ENTER` | MENU | เริ่มเกม |
| `ENTER` | GAME OVER | กลับหน้าเมนู (หลัง 1.5s) |
| `M` | ทุกหน้า | เปิด/ปิดเสียง (แสดง toast 2.5s) |

> **หมายเหตุ:** SHIFT ยิงได้ 1 นัดต่อการกด 1 ครั้ง (edge-triggered) ไม่สามารถ hold ค้างได้

---

## 5. สถาปัตยกรรม (Architecture)

### Class Hierarchy (Interfaces → Abstract → Concrete)

```
Updatable ──┐
Drawable  ──┼── GameObject (abstract)
Collidable──┘       ├── Player
                    ├── Bullet
                    ├── PowerUp
                    ├── EnergyWave
                    ├── BlastRing
                    ├── GhostShip
                    ├── DualFighter
                    └── Enemy (abstract)
                          ├── Drone
                          ├── OrbitBug
                          ├── WaveBug
                          ├── DiveBug
                          ├── ShooterEnemy
                          └── Boss

Particle: Updatable + Drawable (ไม่ extends GameObject — managed แยก)
StarField: Updatable + Drawable (ไม่อยู่ใน World)
```

### Game Loop Flow

```
Main.java
  └── JFrame + GamePanel.startGame()
        └── Thread "game-loop" → gameLoop()
              ├── tick(dt)
              │     ├── stars.update(dt)
              │     ├── [PLAYING only]
              │     │     ├── world.all() → each go.update(dt)
              │     │     ├── world.particles() → each p.update(dt) + removeIf(!alive)
              │     │     ├── formation.update(dt)
              │     │     ├── waves.update(dt)
              │     │     ├── collisions.update(dt)
              │     │     └── world.flush()
              │     └── gameOverTimer += dt (if GAME_OVER)
              └── render()
                    ├── renderScene() → BufferedImage (software back-buffer)
                    │     ├── drawMenu() / drawGame() / drawPaused() / drawGameOver()
                    │     └── "FPS:xx" counter
                    └── blit BufferedImage → BufferStrategy (hardware display)
```

### Render Draw Order (drawGame)
```
1. Background fill (dark blue)
2. StarField
3. Particles (explosion, sparks)
4. BlastRing (AoE visual)
5. EnergyWave (DiveBug cone)
6. GhostShip
7. PowerUp (falling items)
8. Enemy (all types including Boss)
9. DualFighter
10. Bullet
11. Player
12. HudRenderer (HUD overlay)
```

---

## 6. ระบบ Rendering

### Active Rendering (ไม่ใช้ Swing repaint())

`GamePanel` extends `Canvas` (AWT) ไม่ใช่ `JPanel` เพื่อให้ควบคุม rendering ได้โดยตรง

```
Canvas → createBufferStrategy(2)  ← double-buffered hardware flip
       → BufferedImage (TYPE_INT_RGB)  ← software back-buffer

ทุก frame:
  1. วาดทุก object ลง BufferedImage (g2D)
  2. blit BufferedImage → BufferStrategy surface (g.drawImage)
  3. bs.show() → present to display
```

**เหตุผล:** การวาด particle โปร่งแสงจำนวนมากตรงลง hardware surface ช้ามาก (~34ms/frame) แต่วาดลง BufferedImage ก่อนใช้เวลา <1ms แล้วค่อย blit ครั้งเดียว

### Sub-Pixel Positioning

แต่ละ object ถูก render ด้วย fractional translate เพื่อ motion ที่ smooth:

```java
gg.translate(o.getX() - (int)o.getX(), o.getY() - (int)o.getY());
o.draw(gg);  // sprite วาดจาก int coordinates ปกติ
// แต่ shift ไม่ถึง 1px ทำให้ anti-aliasing render ส่วน fraction ได้
```

### Target FPS

```java
// Auto-detect display refresh rate
int rr = GraphicsEnvironment...getDefaultScreenDevice()
         .getDisplayMode().getRefreshRate();
targetFps = (rr > 0) ? rr * 2 : 120;  // 2× refresh ทำให้ compositor มี frame ใหม่เสมอ
```

ถ้าจอ 60Hz → targetFps = 120, ถ้า 144Hz → targetFps = 288

### FPS Limiter

```java
long sleep = (long)(targetNs - (System.nanoTime() - frameStart));
if (sleep > 0) LockSupport.parkNanos(sleep);  // ชัดเจนกว่า Thread.sleep()
```

---

## 7. ระบบ World & Object Management

### World Fields

```java
int    score           = 0       // คะแนนสะสม (× scoreMultiplier เมื่อฆ่าศัตรู)
int    wave            = 1       // wave ปัจจุบัน
int    kills           = 0       // จำนวนศัตรูที่กำจัดทั้งหมด (ยิงตาย + พุ่งชนตาย) แสดงผลตอนจบเกม
int    scoreMultiplier = 1       // ปกติ 1, เพิ่มเป็น 2 เมื่อได้ SCORE_MULT power-up
boolean gameOver       = false   // true → เปลี่ยน screen เป็น GAME_OVER
boolean bossDefeated   = false   // ใช้ track ว่า Boss ตายในชน/AoE
double  totalTime      = 0       // เวลาทั้งหมดที่เล่น (seconds)
double  formationOffset = 0      // offset ซ้าย-ขวาของ formation ทั้งหมด
```

### Object Lifecycle

```
world.add(obj)        → pendingAdd list (thread-safe defer)
world.flush()         → pendingAdd → objects + removeIf(!isAlive())
world.allOf(T.class)  → filter list by type + isAlive()
world.all()           → raw list (อย่า mutate ขณะ iterate)
```

**ทำไมถึงใช้ ArrayList แทน CopyOnWriteArrayList:**  
`COWAL` copy backing array ทุกครั้งที่ add → O(n) per add → O(n²) per frame เมื่อ particle เยอะ ปัจจุบันใช้ single game-loop thread + `pendingAdd` deferred add แทน

### Particle System

`Particle` ไม่อยู่ใน `World.objects` แต่อยู่ใน `World.particles` แยกต่างหาก:
- update และ remove ใน `tick()` ตาม `alive` flag
- ไม่มี collision detection (cosmetic only)

### World Particle Factories

```java
spawnHitSparks(x, y, color)          // 4 อนุภาค, speed 40-120, life 0.2-0.4s
spawnExplosion(x, y, color)          // 14+5 อนุภาค, speed 60-260, life 0.2-1.0s
spawnBigExplosion(x, y, radius, col) // 60+24+30 อนุภาค, speed ∝ radius, special blast
```

---

## 8. ระบบ Entity

### GameObject (abstract base)

```java
double x, y       // ตำแหน่งศูนย์กลาง (pixels)
double w, h       // ขนาด hitbox (AABB)
double vx, vy     // velocity (px/sec)
double rotation   // หน่วย degrees (ใช้ใน PowerUp spin, Boss rotate)
boolean alive     // false = ถูก flush ออกจาก World ใน frame ถัดไป
World world       // reference กลับไปหา World
```

`getBounds()` → `Rectangle2D.Double(x-w/2, y-h/2, w, h)` (AABB)  
`applyVelocity(dt)` → `x += vx*dt; y += vy*dt`

### Player

| Field | ค่า |
|---|---|
| spawn position | (300, 680) — ล่างกลางจอ |
| speed | 280 px/sec |
| lives | เริ่ม 3 |
| invulnerable duration | 2.0s หลังโดนกระสุน (กะพริบทุก 0.1s) |
| normal shoot cooldown | 0.22s |
| rapid fire cooldown | 0.10s |
| shield visual | aura สีเหลืองรอบยาน (fillOval +8px, strokeOval +8px) |

**Shot-type enum:** `NONE | DOUBLE | SPREAD`  
**Rapid fire:** tracked independently (`rapidTimer`) — stacks with any shot type

**DualFighter management:**
- Player ถือ `List<DualFighter>` และ sync ตำแหน่งทุก frame
- ยิงพร้อมกันเมื่อ Player กด shoot
- `removeIf(!df.isAlive())` ทุก frame ทำความสะอาดอัตโนมัติ

### Bullet

| ประเภท | ขนาด hitbox | สี |
|---|---|---|
| Player bullet | 4 × 10 px | สีเขียวมิ้นท์ |
| Enemy bullet | 5 × 12 px | สีแดง |
| Special (AoE) | 16 × 16 px | สีฟ้าอมขาว (pulsing) |

**Special Bullet:**
- `aoeRadius` = `world.width / 4.0` = 150 px
- `aoeDamage` = 20 (เท่ากับยิงปกติ 20 นัด)
- หมดอายุเมื่อออกนอกจอ (margin 20px ทุกด้าน)

---

## 9. ศัตรูทุกประเภท

### Enemy (abstract base)

**Path Travel Speeds (px/sec, frame-rate independent):**
| Phase | Speed |
|---|---|
| Entry path | 400 |
| Swoop (post-entry) | 460 |
| Smooth return | 380 |

**HP Scaling:** `setWave(w)` → `bonus = w / 10` → เพิ่ม HP ทุก 10 wave (`+1` ต่อ 10 wave)

**ลำดับ update ใน Enemy:**
1. `patternTimer += dt` — ใช้เป็น timer สำหรับ sin wave / shooting pattern
2. `tickInvulnerability(dt)` — ลด invulnerable timer
3. ถ้า `inEntryPath` → `updateEntryFollow(dt)` แล้ว return
4. ถ้า `returningToFormation` → `updateSmoothReturn(dt)` แล้ว return
5. `updateAI(dt)` + `applyVelocity(dt)` + `updateShooting(dt)`

---

### Drone (สีม่วง) 🟣

```
HP: 1  |  Score: 100  |  Wave: 1+  |  Shoot cooldown: dynamic
```

- ยิง `StraightPattern(260 + wave*8)` — bullet speed เพิ่มตาม wave
- Dive: บินตรงผ่านจอล่าง แล้ว `startSmoothReturn()`
- **ไม่ถูกลด speed** (exempt จาก 75% base reduction)
- `speedMult = 1.0 + 0.05 × floor((wave-1)/5)` → Wave 1: ×1.0, Wave 6: ×1.05, Wave 11: ×1.10

---

### OrbitBug (สีฟ้า) 🔵

```
HP: 2  |  Score: 150  |  Wave: 2+
```

- **ไม่ยิงกระสุน** (shootCooldown = 99)
- Formation oscillation: `cos/sin` รอบ formation slot
- Dive:
  1. Snapshot ตำแหน่ง Player (ไม่ track หลังนี้)
  2. บินโค้งผ่านจุด snapshot
  3. วนออก loop ด้านข้าง radius 180px
  4. `startSmoothReturn()`
- `speedMult = 0.75 + 0.10 × floor((wave-1)/2)` → Wave 1: ×0.75, Wave 3: ×0.85, Wave 5: ×0.95

---

### WaveBug (สีเหลือง) 🟡

```
HP: 2  |  Score: 160  |  Wave: 3+
```

- **ไม่ยิงขณะมีชีวิต** (ยิงเฉพาะตอนตาย)
- Formation: oscillation แนวตั้ง (wave convoy) sine wave, amplitude 55px, period 3.5s
- Dive: บินลงพร้อม sinusoidal weave แล้ว `startSmoothReturn()`
- **เมื่อตาย (onDeath):** ยิง `AimedPattern` 2 นัด ออฟเซ็ต ±15° ไปที่ Player

---

### DiveBug (สีส้ม/เพชร) 🔶

```
HP: 2  |  Score: 200  |  Wave: 4+
```

- **ไม่ยิงกระสุน**
- Dive 4 ขั้นตอน:

| Phase | รายละเอียด |
|---|---|
| `ADVANCE` | Snapshot player pos → บินไปหยุดที่ y = H/4 |
| `PAUSE` | หยุดนิ่ง 0.55s (กะพริบ warning) |
| `FIRE` | ปล่อย `EnergyWave` cone ชี้ลง |
| `RETURN` | `startSmoothReturn()` |

#### EnergyWave — รายละเอียดทางเทคนิค

```
จำนวน arc : 14 วง
ระยะห่างต่อ arc : 18 px
ความกว้าง cone : ±35° จากแกนกลาง
ขยาย : 200 px/sec
ระยะสูงสุด : 14×18 + 60 = 312 px
```

- รูป fan แผ่ลงล่าง วาดด้วย `Arc2D` (180° → 360°) แต่ละวง
- Gradient HSB: hue 0.58 (ฟ้า) → 0.66 (น้ำเงิน) ตามระยะ
- Alpha fade ตามทั้งระยะและ lifetime
- Outer glow stroke 5px + main stroke 2.2px
- Centre beam: เส้นบางแนวตั้ง จาก origin ลงมา 60% ของ reach
- Hit: Player อยู่ใต้ origin AND `relX ≤ tan(35°)×relY + 20px`
- ทำ hit ได้ครั้งเดียว (`hasHit = true` หลังจากชน)
- จัดการ collision ตัวเองใน `update()` (ไม่ผ่าน CollisionManager)

---

### ShooterEnemy (สีแดง/บวก) 🔴

```
HP: 1  |  Score: 250  |  Wave: 5+
```

**สุ่ม 2 Mode เมื่อ triggerDive:**

#### CAPTURE mode (50% — เฉพาะเมื่อ Player lives > 0)

ใช้ **tractor beam** (ลำแสงดูด) ไม่ใช่การพุ่งชน — ShooterEnemy ลอยอยู่กับที่แล้วยิงลำแสงลงไปหา Player:

| ขั้น | Mode | รายละเอียด |
|---|---|---|
| 1 | `CAPTURE_BEAM` | hover อยู่กับที่ (vx=vy=0) ยิงลำแสงโตเข้าหา Player ด้วย `BEAM_GROW_SPEED` 320 px/sec |
| 2 | timeout | ถ้าเล็งไม่โดนภายใน `BEAM_TIMEOUT` 4.0s → `startSmoothReturn()` กลับ formation |
| 3 | capture event | เมื่อ `beamLength ≥ ระยะถึง Player` **และ Player lives > 1** → spawn particle + `player.absorbHit()` → สร้าง `GhostShip` ห่าง 36px ใต้ |
| 4 | `CAPTURE_BOB` | `moveTowardsFormation` + sin wave `1.4Hz × 45px` ลาก Ghost ตามหลัง |

- ลำแสงวาดเป็นจุดประกะพริบ + nozzle เรืองแสง + charging ring รอบตัว
- ยิงโดน GhostShip → Ghost หาย (`ghostHit()`) **ไม่คืน life**
- ยิงโดน ShooterEnemy ขณะ Ghost ยังอยู่ → Ghost หาย + `player.gainLife()` + `player.awardDualFighter()`

> **กฎกันตาย (life-steal guard):** ลำแสงดูดจะ "ขโมยเลือด" ได้ก็ต่อเมื่อ Player มี `lives > 1` เท่านั้น — ถ้า Player เหลือชีวิตสุดท้าย (`lives ≤ 1`) ลำแสงจะ `startSmoothReturn()` ยกเลิก capture ทันที ไม่หักเลือด เพื่อไม่ให้การโดนดูดเป็นการฆ่าผู้เล่นทันที

#### BOUNCER mode (50%)

```
vx = ±140 px/sec × speedMult (สุ่มทิศ)
vy = sin(patternTimer × 0.8) × 40 px/sec (bob ขึ้น-ลง)
Y bound: 50 → world.height × 0.55
ยิง: AimedPattern(280 + wave×8) ทุก 5.0s
```

---

### Boss (hexagon สีส้มแดง) 🔥

```
HP: 60 (wave 5, scaling) | Score: 5000 | Wave: 5, 10, 15, ...
ขนาด: 70 × 60 px
```

**HP Scaling — Compounding growth (+15% per 10-wave tier):**

```
tier = wave / 10
maxHp = round(60 × 1.15^tier)

Wave 5  (tier 0): 60 HP
Wave 10 (tier 1): 69 HP
Wave 15 (tier 2): 69 HP  (same tier)
Wave 20 (tier 2): 69 HP
Wave 25 (tier 2): 69 HP
Wave 30 (tier 3): ~79 HP
```

> Note: Boss override `setWave()` โดยเฉพาะ ไม่ใช้ generic HP bonus (+1/10 waves)

**3 Phase (เปลี่ยนตาม HP fraction):**

| Phase | เงื่อนไข | Movement | Bullet Pattern |
|---|---|---|---|
| 0 | HP > 2/3 maxHp | sin wave เบา (vx ±120, vy ±40) | CircularBurst 6 ทิศ |
| 1 | HP 1/3–2/3 maxHp | sin wave เร็วขึ้น (vx ±150, vy ±60) | AimedPattern + CircularBurst 4 ทิศ |
| 2 | HP < 1/3 maxHp | sin wave เร็วมาก (vx ±180, vy ±80) | CircularBurst 8 ทิศ + 50% AimedPattern |

**Phase Transition:** เมื่อเปลี่ยน phase → `startInvulnerability(1.2s)` (กะพริบ 1.2 วิ)

**Boss Core Color:** Phase 0 = สีเหลือง, Phase 1 = สีส้ม, Phase 2 = สีแดง

**HP Bar:** แสดงใต้ตัว (y + h/2 + 8), สีเขียว → เหลือง → แดง ตาม HP fraction

**Firing Cooldown:**

```
Base cooldowns ต่อ spawn (ลด 1s ต่อ respawn, floor 2s):
  spawn 1: Phase0=8s,  Phase1=7s,  Phase2=6s
  spawn 2: Phase0=7s,  Phase1=6s,  Phase2=5s
  spawn 3: Phase0=6s,  Phase1=5s,  Phase2=4s

HP < 50%: cooldown effective ลด 1s เพิ่มอีก (floor 2s)
```

**On Death:** `player.addSpecialAmmo(1)` (90%) หรือ `addSpecialAmmo(2)` (10%)

**Boss Rotation:** `rotation += dt × 30` (degrees/sec) — hexagon หมุนตลอดเวลา

#### Boss Reinforcements (WaveManager)

เมื่อ Boss อยู่บนจอและ minions < 5:
- Summon 8 ศัตรูใหม่แบบสุ่มจาก 5 ประเภท
- เข้า formation slots ที่ว่างอยู่ (shuffle แบบ random)
- Cooldown: `4.0s - 0.5s × floor((wave-5)/5)` → ลด 1s เพิ่มเมื่อ Boss HP ≤ 40%, floor 1s

---

## 10. ระบบ Entry Path (Follow-the-Leader)

### EntryPath Architecture

`EntryPath` เก็บ list ของ waypoints (x,y) และตาราง cumulative arc-length:

```java
public record Pt(double x, double y) {}
List<Pt> points;   // 160–220 จุด
double[] cum;      // cum[i] = total distance จาก point[0] ถึง point[i]
double totalLen;   // total arc length
```

**Arc-Length Parameterization:**
```java
// t = arc-length fraction [0,1] → ความเร็ว pixel สม่ำเสมอ
Pt atFraction(double t)  // binary search + linear interp ใน cum[]
```

ทำให้ object เดิน path ด้วย speed เป็น px/sec ที่แน่นอน ไม่ขึ้นกับความหนาแน่นของ waypoints

### Figure-8 Entry Path (3 Segments, C1-Continuous)

```
Segment 1 — Lead-in (34% of 220 pts):
  Cubic Bézier: off-screen corner → loop junction
  เส้นโค้งให้มาถึง junction ALONG the loop tangent

Segment 2 — Loop flourish (40% of 220 pts):
  Full circle: center = (W/2 ± 75, H×0.40), radius = 86px
  ทิศหมุน: ขึ้นกับ lside และ sweepDir

Segment 3 — Settle (26% of 220 pts):
  Cubic Bézier: loop junction → formation slot
  ออกจาก junction ALONG the same tangent (C1 join)
```

**Parameters:**
- `fromLeft` / `fromBottom`: มุมเข้าของ convoy
- `loopFar`: loop ด้านตรงข้าม (crossover format)

### 2 Convoy Formats

| Format | รายละเอียด |
|---|---|
| `ONE_ROW_ONE_SIDE` | 1 แถว single file จากฝั่งเดียว |
| `ONE_ROW_CROSSOVER` | 1 แถว, loop บนฝั่งตรงข้ามกับ entry (sweep across) |

### Mixed Convoy (Wave ≥ 4)

2 ประเภทปนกันในกลุ่มเดียว สลับสีแบบ interleaved:
```
Lane 0: typeA-typeB-typeA-typeB... (column order)
Lane 1: typeB-typeA-typeB-typeA... (offset → checkerboard)
```
Lane 1 เข้าจากฝั่งตรงข้าม Lane 0 เพื่อไม่ให้ overlap กลางจอ

### Entry from Bottom Edge

ConvoyDecision สุ่ม `fromBottom` แยกอิสระ → convoy เข้าจาก bottom ขึ้นมาหรือ top ลงมาได้ทั้งคู่

### Swoop (20% chance)

```
Trigger: เมื่อ enemy จบ entry path → snapshot player X,Y ณ เวลานั้น
Path: EntryPath.buildSwoop()
  Phase 1 (45%): เส้นตรงผ่าน player snapshot → ออกจอล่าง (y > H+80)
  Phase 2 (55%): Cubic Bézier: exit bottom → loop side → formation slot
Speed: 460 px/sec (SWOOP_SPEED)
```

### Smooth Return (ทุก dive)

```
50 waypoints Bézier curve:
  Start: ตำแหน่งปัจจุบัน
  CP1: (x + (tx-x)*0.3,  y - 80)        ← ดึงขึ้น
  CP2: (tx,               ty - 60)       ← ดึงเข้า slot
  End: formation slot (รวม formationOffset)
Speed: 380 px/sec (RETURN_SPEED)
```

---

## 11. ระบบ Bullet Pattern

**Strategy Pattern** — interface `BulletPattern`:
```java
List<Bullet> createBullets(World world, double srcX, double srcY, boolean fromPlayer)
```

| Class | รายละเอียด |
|---|---|
| `StraightPattern(speed)` | 1 กระสุน vy = ±speed (ขึ้นถ้า player, ลงถ้า enemy) |
| `AimedPattern(speed)` | 1 กระสุน aimed ไปตำแหน่ง Player (atan2 direction) |
| `SpreadPattern(speed, n, deg)` | n กระสุน กระจาย deg° รวม (เท่ากัน) |
| `CircularBurstPattern(speed, n, offset)` | n กระสุน วงกลม 360°, phase offset = `patternTimer` |
| `DoubleStraightPattern(speed, gap)` | 2 กระสุน ตรง, offset ±gap px แนวนอน |

**Bullet speeds ที่ใช้จริง:**
- Player normal: 520 px/sec
- Player spread: 520 px/sec (3 ทิศ, ±30°)
- Enemy straight: `260 + wave*8` px/sec
- Boss CircularBurst: 180–220 px/sec
- Boss Aimed: 300–320 px/sec
- ShooterEnemy Bouncer Aimed: `280 + wave*8` px/sec

---

## 12. ระบบ Power-Up

ตก 20% เมื่อศัตรูตาย (`world.maybeDropPowerUp()`), fall speed: 90 px/sec, lifetime: 12s

| สี | Label | Type | Effect | Duration |
|---|---|---|---|---|
| 🟢 เขียว | `2X` | `DOUBLE_SHOT` | `DoubleStraightPattern(520, 10)` | 12s |
| 🔵 น้ำเงิน | `SP` | `SPREAD_SHOT` | `SpreadPattern(520, 3, 30)` | 10s |
| 🟠 ส้ม | `RF` | `RAPID_FIRE` | cooldown 0.22s → 0.10s — **stack กับ DOUBLE/SPREAD ได้** | 8s |
| 🟡 เหลือง | `SH` | `SHIELD` | กัน 1 hit ต่อชั้น — **stack ได้หลายชั้น** — แชร์ให้ DualFighter ด้วย | 15s ต่อชั้น |
| 🟣 ม่วง | `SC` | `SCORE_MULT` | `world.scoreMultiplier = 2` | 10s |

> **Shield Stacking:** แต่ละ SHIELD power-up เพิ่มโล่ชั้นใหม่เข้า `List<Double> shieldTimers` แยกกัน  
> `absorbHit()` ดึงโล่ที่เวลาน้อยที่สุด (จะหมดก่อน) ออกก่อนเสมอ — `hasShield() = !shieldTimers.isEmpty()`  
> HUD แสดง `⬡ SHIELD x2` พร้อม bar ของโล่ที่จะหมดก่อน  
> เมื่อ Player ได้ SHIELD จะเรียก `df.applyShield(15)` ให้ DualFighter ทุกลำพร้อมกัน (DualFighter ไม่ stack)  
> **RAPID_FIRE Stacking:** `rapidTimer` เป็น field แยกต่างหากจาก shot-type — การได้ RAPID_FIRE ขณะมี DOUBLE/SPREAD ทำให้ยิงแบบนั้นเร็วขึ้นทันที

**Pickup hitbox:** มี margin เพิ่ม ±7px เพื่อให้เก็บง่ายขึ้นเมื่อ Power-up ผ่านมาเร็ว

**Power-up visual:** กล่องสี่เหลี่ยมหมุน (`rotation += dt*120°/sec`) + glow halo รอบ + label ตรงกลาง

---

## 13. ระบบ Special Ammo

**ได้มาจาก:** ฆ่า Boss (90% = 1 round, 10% = 2 rounds)

> **โบนัสฆ่า Boss:** นอกจาก Special Ammo แล้ว การฆ่า Boss ยัง `player.gainLife()` คืนเลือด **+1** ให้ผู้เล่นด้วย (อยู่ใน `Boss.onDeath()` — ทำงานทั้งตอนโดนกระสุนปกติและโดน AoE)

**ยิงด้วย:** `SHIFT` key (edge-triggered, 1 นัดต่อกด)

**สถิติ:**
```
Bullet speed: -400 px/sec (ขึ้นตรง)
AoE radius:   150 px (= world.width / 4)
AoE damage:   20 ต่อ enemy ใน radius
```

**Collision:** เมื่อ special bullet โดน enemy ตัวแรก → `detonateSpecial()`:
1. `world.spawnBigExplosion(x, y, radius, cyan)` — 114 particles
2. `world.add(new BlastRing(...))` — visual ring
3. ตรวจ `dx²+dy² ≤ r²` กับ **ทุก enemy** → `e.absorbHit(20)`

**BlastRing visual:**
```
ระยะเวลา: 0.45s
ขยายไปถึง: 100% ของ radius อย่างรวดเร็ว แล้วค่อย fade
องค์ประกอบ:
  - faint disc (alpha 0→45 ตาม lifetime)
  - bright rim stroke (2–5px)
  - inner white flash (หายเร็ว, alpha ∝ f²)
```

**HUD:** แสดง `◎ SPECIAL x{n}  [SHIFT]` เมื่อมี ammo

---

## 14. ระบบ Wave & Difficulty Scaling

### Wave Structure

| Wave | Enemy types ที่เพิ่มขึ้น |
|---|---|
| 1 | Drone ×8 |
| 2+ | + OrbitBug ×6 |
| 3+ | + WaveBug ×6 |
| 4+ | + DiveBug ×5 |
| 5+ | + ShooterEnemy ×4 + Boss ทุก wave ×5 |

### Batch System (Wave ≥ 6)

Enemy count เพิ่ม 20% ต่อ wave เหนือ wave 5:
```
factor = 1.0 + 0.20 × (wave - 5)
Wave 6:  ×1.2,  Wave 10: ×2.0,  Wave 15: ×3.0

nBatches = ceil(factor)
fraction_per_batch = factor / nBatches

Wave 10: factor=2.0, 2 batches × 1.0 each (full)
Wave 11: factor=2.2, 3 batches × 0.73 each (~even split)
```

แต่ละ batch มี **pause 1.2s** ระหว่าง batch, **2.5s** ระหว่าง wave

### Speed Scaling

```
Drone + Boss:
  speedMult = 1.0 + 0.05 × floor((wave-1) / 5)

OrbitBug, WaveBug, DiveBug, ShooterEnemy:
  speedMult = 0.75 + 0.10 × floor((wave-1) / 2)
```

| Wave | Drone/Boss | Others |
|---|---|---|
| 1 | ×1.00 | ×0.75 |
| 3 | ×1.00 | ×0.85 |
| 5 | ×1.00 | ×0.95 |
| 6 | ×1.05 | ×1.05 |
| 11 | ×1.10 | ×1.25 |

### Formation Movement

```
speed = 40 + wave × 5  (px/sec)
range = ±90 px จาก centre (oscillate ซ้าย-ขวา)
```

### Dive Trigger Interval

```
interval = max(1.0, 2.5 - wave × 0.2)
Wave 1: 2.3s,  Wave 5: 1.5s,  Wave 7: 1.1s,  Wave 8+: 1.0s
จำนวน: 1–2 ตัวต่อ trigger (สุ่ม จาก in-formation non-Boss)
```

### Enemy Bullet Speed Scaling

```
BasePattern speed: 260 + wave × 8 (StraightPattern default)
Shoot cooldown: max(0.5, initial - wave × 0.04) — ลดต่อ wave
```

---

## 15. Mechanic พิเศษ

### DualFighter (ยานเสริมสีเขียว)

- **รับ:** ยิงโดน ShooterEnemy ขณะลาก GhostShip อยู่
- ขนาด: 32 × 28 px, สีเขียวสด (`#64FF78`)
- offset: -50px (ซ้าย) หรือ +50px (ขวา) จาก Player
- สูงสุด 2 ลำ (1 ซ้าย + 1 ขวา)
- **HP = 2** — ต้องโดนตี 2 ครั้งถึงจะถูกทำลาย (มี HP pips แสดงใต้ cockpit)
- **Damage color:** HP เต็ม = สีเขียว (`#64FF78`); HP เหลือ 1 = สีแดง (`#FF5050`) — body, wing, cockpit, engine glow และ HP pip เปลี่ยนสีพร้อมกัน; explosion ตอนตายก็ใช้สีตาม HP ขณะนั้น
- ยิงพร้อม Player ทุกนัด: pattern เดียวกับ power-up ปัจจุบันของ Player (`tryShoot` ถูกเรียกตอน Player ยิง ไม่มี cooldown แยกของตัวเอง — Player คุม rate อยู่แล้ว)
- **Shield:** เมื่อ Player เก็บ SHIELD power-up จะแชร์โล่ให้ DualFighter ทุกลำ (`applyShield(15s)`) โล่กัน 1 hit ก่อนค่อยลด HP (DualFighter ไม่ stack)
- ถูกกระสุน/ชนศัตรู → `absorbHit()`: เช็คโล่ → ลด HP → ถ้า HP ≤ 0 จึงหาย + `spawnExplosion(สีตาม HP)` (Player ยังอยู่)
- **ไม่** เคลื่อนที่อิสระ — ตำแหน่งถูก sync จาก Player ทุก frame; `update()` แค่ลด shield timer
- HUD: `✦ WINGMAN x{n}`

### GhostShip

- `GhostShip(world, x, y+36)` — ห้อยห่าง 36px ใต้ ShooterEnemy
- กะพริบสีฟ้า (visual: ยานขนาดเล็ก)
- ยิงโดน Ghost → `se.ghostHit()` nulls reference + `gs.destroy()`
- Ghost ตาย: **ไม่คืน life**

### Shield

- รับจาก SHIELD power-up (ได้หลายชั้น — แต่ละชั้น = timer แยก 15s)
- `absorbHit()` เช็ค `shieldTimers` ก่อน lives — ถ้ามีโล่: ดึงโล่ที่เวลาน้อยสุดออก 1 ชั้น, ยกเลิก hit
- ป้องกันได้ทั้งกระสุน, ชน enemy body, ชน DiveBug EnergyWave
- HUD: `⬡ SHIELD` (ชั้นเดียว) หรือ `⬡ SHIELD x2` (หลายชั้น) พร้อม countdown bar ของโล่ที่จะหมดก่อน

### Kill Progress (Bonus Life)

- `world.killProgress` นับศัตรูที่กำจัดได้ (ยิงตาย + ชนตาย) เทียบกับ `KILLS_PER_LIFE = 100`
- ทุก 100 ตัว: `killProgress` รีเซ็ตเป็น 0 → `player.gainLife()` (+1 ชีวิต)
- HUD: `♥ +1  xx/100` พร้อม bar สีชมพู แสดงอยู่ระหว่าง LIVES กับ buff rows เสมอ
- `world.kills` (ตัวนับรวม Game Over) ยังคงนับต่อเนื่องแยกกัน

### Score Multiplier

- Power-up `SCORE_MULT` → `world.scoreMultiplier = 2`
- ทุกครั้งที่ kill → `world.score += scoreValue × world.scoreMultiplier`
- ถ้าชน enemy body → score `scoreValue / 2` (ไม่คูณ multiplier)

---

## 16. ระบบ Effects & Particles

### StarField

```
จำนวน: 120 ดาว
Speed: 20–80 px/sec (สุ่มต่อดาว)
Brightness: 0.3–1.0 (สุ่ม)
Size: 0.5–2.0 px (วาดด้วย Ellipse2D)
สี: R=G=brightness×255, B=min(255, brightness×255+30) — ฟ้าอมขาว
Reset: เมื่อ y > H → ขึ้น y=-2 ตำแหน่ง x ใหม่สุ่ม
```

### Particle

```java
// สร้างจาก World.spawnParticles()
double x, y, vx, vy   // ตำแหน่ง + velocity
Color color            // สีที่ให้มา
double life            // เวลาที่เหลือ (วินาที)
double size            // ขนาด (px)
// alpha fade: (life/maxLife) × 255
// alive = false เมื่อ life ≤ 0
```

### Explosion Types

| Function | Particles | Speed (px/s) | Life (s) | Size (px) |
|---|---|---|---|---|
| `spawnHitSparks` | 4 | 40–120 | 0.2–0.4 | 2–4 |
| `spawnExplosion` | 14 + 5 white | 60–260 | 0.2–1.0 | 3–7 |
| `spawnBigExplosion` | 60 + 24 + 30 | ∝ radius | 0.25–1.1 | 3–9 |

---

## 17. ระบบ Managers

### WaveManager

**SpawnEntry record:**
```java
record SpawnEntry(double time, String type, int row, int col, String groupKey, int lane)
```

**ConvoyDecision record:**
```java
record ConvoyDecision(ConvoyFormat format, boolean fromLeft, boolean fromBottom)
```

**Spawn timing:** 0.18s ระหว่างสมาชิกในกลุ่ม (follow-the-leader spacing)

**Group key ตัวอย่าง:** `"b0_g0"`, `"b1_mixA"`, `"reinf3_"`

**Boss spawn:** tail of last batch, +2.5s หลัง enemy สุดท้ายของ batch

### FormationManager

```java
speed = 40 + world.wave × 5  (px/sec)
range: offsetX ±90 px
diveInterval = max(1.0, 2.5 - world.wave × 0.2)
count per dive = 1 + rng.nextInt(min(2, candidates.size()))
```

### CollisionManager

ลำดับ collision check (7 categories):

| # | Source | Target | Result |
|---|---|---|---|
| 1 | Player bullet (normal) | Enemy | `e.absorbHit(1)`, `b.destroy()` |
| 2 | Player bullet (special) | Enemy (first touch) | `detonateSpecial()` — AoE |
| 3 | Player bullet | GhostShip | Ghost destroyed, `se.ghostHit()` |
| 4 | Enemy bullet | Player | `p.absorbHit()` |
| 5 | Enemy bullet | DualFighter | `df.absorbHit()` (โล่/HP 2 → ตายเมื่อ HP หมด) |
| 6 | Enemy body | Player | half score + explosion + `p.absorbHit()` |
| 7 | Enemy body | DualFighter | explosion + `e.destroy()` + `df.absorbHit()` |
| 8 | PowerUp | Player | `p.applyPowerUp(type)`, +7px margin |

> **EnergyWave** handles collision ตัวเองใน `update()` — ไม่อยู่ใน list นี้

**AABB:** ใช้ `Rectangle2D.intersects()` ทุก pair (O(n×m) per category)

### HudRenderer

| Element | ตำแหน่ง | Font |
|---|---|---|
| SCORE: xxx | (12, 28) | Monospaced Bold 20 |
| WAVE n | กลางจอ x, y=24 | Monospaced Bold 16 |
| LIVES: ♥♥♥ | (12, 52) | Monospaced Bold 14 |
| ♥ +1 xx/100 + bar | (12, 66/71) | Monospaced Bold 12 |
| Buff label + bar (dynamic) | (12, 92+) | Monospaced Bold 14 |
| ✦ WINGMAN x{n} | dynamic (ต่อจาก buffs) | Monospaced Bold 13 |
| ◎ SPECIAL x{n} [SHIFT] | dynamic (ต่อจาก wingman) | Monospaced Bold 14 |
| ENEMIES: n | (W-120, 28) | Monospaced Plain 12 |
| FPS:xx | (W-52, H-6) | Monospaced Plain 10 |

**Buff bar colors (ตรงกับสี item ที่หยิบ):**

| Buff | สี HUD |
|---|---|
| DOUBLE | เขียว `(100,255,100)` |
| SPREAD | ฟ้า `(100,200,255)` |
| RAPID | ส้ม `(255,150,50)` |
| SHIELD | เหลือง `(255,220,0)` |
| SCORE_MULT | ม่วง `(255,100,255)` |
| Kill progress | ชมพู `(255,120,180)` |

---

## 18. Flow ของเกม

```
MENU  ──── ENTER ──────────────────────────────────┐
  ↑                                                 ↓
  │  R                                          PLAYING
  │  ┌──────────────────────────────────────  ↙        ↘ ESC
  │  ↓                                   (game)        PAUSED
GAME_OVER ←── lives≤0                                ↙     ↘
              gameOver=true                        ESC       R
                                                 PLAYING    MENU
```

**Screen transitions:**

| From | Trigger | To |
|---|---|---|
| MENU | ENTER | PLAYING (Boss.resetSpawnCount, new World, new Player) |
| PLAYING | ESC | PAUSED |
| PAUSED | ESC | PLAYING |
| PAUSED | R | MENU (playBgm BGM_MENU) |
| PLAYING | `world.gameOver=true` | GAME_OVER (gameOverTimer เริ่ม) |
| GAME_OVER | ENTER (หลัง 1.5s) | MENU |

**Menu screen:** title "GALAGA" (sin bob) + **6 enemy sprites** 2 แถว พร้อมชื่อ/คะแนน + controls 2 คอลัมน์ + power-up legend ครบ 5 ชนิด  
**Pause screen:** overlay + hint `ESC to resume  |  R to menu`  
**Game Over screen:** "GAME OVER" text pulsing + FINAL SCORE + REACHED WAVE + **ENEMIES DESTROYED: {world.kills}** (สถิติจำนวนศัตรูที่กำจัดได้ตลอดเกม) + "PRESS ENTER TO RETURN" (fade in หลัง 1.5s)  
**Mute toast:** กด M ทุกหน้า → แสดง `♪ SOUND ON` / `✕ SOUND OFF` fade in/out นาน 2.5s

---

## 19. ค่า Constants สำคัญ

### GamePanel

| Constant | ค่า |
|---|---|
| W | 600 px |
| H | 760 px |
| targetFps | refresh×2 หรือ 120 |

### World

| Constant | ค่า |
|---|---|
| `KILLS_PER_LIFE` | 100 (ฆ่าครบ 100 ตัว → +1 ชีวิต) |

### Enemy (abstract)

| Constant | ค่า |
|---|---|
| `ENTRY_SPEED` | 400 px/sec |
| `SWOOP_SPEED` | 460 px/sec |
| `RETURN_SPEED` | 380 px/sec |

### EntryPath

| Constant | ค่า |
|---|---|
| `SAMPLES` (swoop) | 160 pts |
| Figure-8 pts (N) | 220 pts |
| Loop radius | 86 px |
| Loop center Y | H × 0.40 = 304 px |

### EnergyWave

| Constant | ค่า |
|---|---|
| `RING_COUNT` | 14 |
| `ARC_SPACING` | 18 px |
| `CONE_HALF_ANGLE` | 35° |
| `EXPAND_SPEED` | 200 px/sec |
| `MAX_REACH` | 312 px |

### Boss

| Constant | ค่า |
|---|---|
| `BASE_HP` | 60 (wave 5) |
| `HP_GROWTH_TIER` | 0.15 (15% compounding per 10-wave tier) |
| Base cooldowns | Phase0=8s, Phase1=7s, Phase2=6s |

### ShooterEnemy

| Constant | ค่า |
|---|---|
| `GHOST_TRAIL_DIST` | 36 px |
| `BEAM_GROW_SPEED` | 320 px/sec |
| `BEAM_TIMEOUT` | 4.0s |
| `BOUNCE_SPEED_X` | 140 px/sec |
| `BOUNCE_SHOT_INTERVAL` | 5.0s |

---

## 20. ความน่าจะเป็น & ตัวคูณทั้งหมด

รวมค่าความน่าจะเป็น (random) และตัวคูณ/สเกล (scaling) ทุกตัวที่ตั้งไว้ในเกม พร้อมตำแหน่งในซอร์สโค้ด

### 20.1 ความน่าจะเป็น (Random Chances)

| เหตุการณ์ | ความน่าจะเป็น | เงื่อนไข / รายละเอียด | แหล่ง |
|---|---|---|---|
| **Power-up drop** เมื่อศัตรูตาย | **20%** | `rng.nextDouble() > 0.20` → return | `World.maybeDropPowerUp` |
| **ชนิด Power-up** ที่สุ่มได้ | **20% ต่อชนิด** | uniform จาก 5 ชนิด (`nextInt(5)`) | `World.maybeDropPowerUp` |
| **Swoop** หลังจบ entry path | **20%** | `nextDouble() < 0.20` ต่อศัตรู 1 ตัว | `WaveManager.spawnOne` |
| **ShooterEnemy → CAPTURE** | **50%** | ต้อง Player lives > 0 ด้วย ไม่งั้นเป็น BOUNCER | `ShooterEnemy.triggerDive` |
| ShooterEnemy → BOUNCER | 50% (หรือ 100% ถ้า lives = 0) | ส่วนที่เหลือ | `ShooterEnemy.triggerDive` |
| ทิศ Bouncer (ซ้าย/ขวา) | **50% / 50%** | `nextBoolean()` | `ShooterEnemy.startBouncer` |
| **Boss drop Special Ammo = 1** | **90%** | `nextDouble() < 0.90` | `Boss.onDeath` |
| Boss drop Special Ammo = 2 | **10%** | ส่วนที่เหลือ | `Boss.onDeath` |
| Boss Phase 2 ยิง Aimed เพิ่ม | **50%** | `nextBoolean()` (ควบคู่ CircularBurst 8 ทิศ) | `Boss.updateShooting` |
| Convoy ใช้ format 2 แถว (wave ≥ 2) | **50%** | `nextDouble() < 0.5` แล้ว 50/50 ระหว่าง ONE_SIDE/BOTH | `WaveManager.makeConvoyDecision` |
| Convoy format อื่น (กรณีที่เหลือ) | **25% ต่อ format** | uniform จาก 4 format | `WaveManager.makeConvoyDecision` |
| Convoy เข้าจากซ้าย / ขวา | **50% / 50%** | `nextBoolean()` | `WaveManager.makeConvoyDecision` |
| Convoy เข้าจากบน / ล่าง | **50% / 50%** | `nextBoolean()` | `WaveManager.makeConvoyDecision` |
| ชนิดศัตรู reinforcement | **20% ต่อชนิด** | uniform จาก 5 ชนิด | `WaveManager.summonReinforcements` |
| จำนวนศัตรู dive ต่อครั้ง | **1–2 ตัว** | `1 + nextInt(min(2, candidates))` | `FormationManager.triggerDive` |
| Swoop path split (Phase1/Phase2) | **45% / 55%** | straight dive 45% + Bézier arc 55% | `EntryPath.buildSwoop` |

### 20.2 ตัวคูณ & สเกลตาม Wave (Scaling Multipliers)

| ระบบ | สูตร | ผลลัพธ์ตัวอย่าง | แหล่ง |
|---|---|---|---|
| **Score Multiplier** (SCORE_MULT) | `scoreMultiplier = 2` | คะแนน **×2** (+100%) นาน 10s | `Player.applyPowerUp` |
| คะแนนเมื่อชน body (ไม่ยิง) | `scoreValue / 2` | ได้ **50%** ของคะแนนปกติ ไม่คูณ multiplier | `CollisionManager` |
| **Speed: Drone + Boss** | `1.0 + 0.05·⌊(wave-1)/5⌋` | **+5% ทุก 5 wave** (W1 ×1.0, W6 ×1.05, W11 ×1.10) | `WaveManager.applySpeedMult` |
| **Speed: ศัตรูอื่น** | `0.75 + 0.10·⌊(wave-1)/2⌋` | ฐาน **75%**, **+10% ทุก 2 wave** (W1 ×0.75, W5 ×0.95, W11 ×1.25) | `WaveManager.applySpeedMult` |
| **HP ศัตรูทั่วไป** | `+1 ต่อ ⌊wave/10⌋` | **+1 HP ทุก 10 wave** | `Enemy.setWave` |
| **HP Boss** | `60 · 1.15^⌊wave/10⌋` | **+15% ทบต้น ต่อ tier 10 wave** (W5=60, W10=69, W30≈79) | `Boss.setWave` |
| **จำนวนศัตรูต่อ wave** (wave ≥ 6) | `1.0 + 0.20·(wave-5)` | **+20% ต่อ wave** (W6 ×1.2, W10 ×2.0, W15 ×3.0) | `WaveManager.buildWave` |
| **Rapid Fire cooldown** | `0.22s → 0.10s` | ยิงเร็วขึ้น ~**2.2 เท่า** | `Player.update` |
| Boss cooldown เมื่อ HP < 50% | `−1.0s` (floor 2s) | ยิงถี่ขึ้น | `Boss.updateShooting` |
| Boss cooldown ต่อ respawn | `−1.0s ต่อครั้ง` (floor 2s) | บอสตัวหลังยิงถี่ขึ้น | `Boss` constructor |
| **Reinforce cooldown** | `4.0 − 0.5·⌊(wave-5)/5⌋` | **−1s เพิ่ม** ถ้า Boss HP ≤ 40%, floor 1s | `WaveManager.reinforceInterval` |
| Enemy shoot cooldown decay | `max(0.5, cd − wave·0.04)` | ลดลงต่อ wave ถึงเพดาน 0.5s | `Enemy.updateShooting` |
| Enemy bullet speed | `260 + wave·8` px/sec | เร็วขึ้นตาม wave | `Enemy.fireDefaultPattern` |
| **Formation speed** | `40 + wave·5` px/sec | แกว่งซ้าย-ขวาเร็วขึ้น (range ±90px) | `FormationManager` |
| **Dive interval** | `max(1.0, 2.5 − wave·0.2)` | ดำดิ่งถี่ขึ้น (W1=2.3s → W8+ =1.0s) | `FormationManager` |
| Pickup hitbox margin | `+7px` | เก็บ power-up ง่ายขึ้น | `CollisionManager` |
| Boss phase threshold | `2/3` และ `1/3` ของ maxHp | เปลี่ยน phase ตามสัดส่วน HP | `Boss.updateAI` |

### 20.3 ค่า/ระยะเวลาคงที่ (Fixed Durations)

| ค่า | ระยะเวลา / จำนวน |
|---|---|
| DOUBLE_SHOT | 12s |
| SPREAD_SHOT | 10s |
| RAPID_FIRE | 8s (stack กับ DOUBLE/SPREAD) |
| SHIELD | 15s ต่อชั้น (stack ได้, DualFighter ไม่ stack) |
| SCORE_MULT | 10s |
| Kill progress → bonus life | 100 kills |
| Player invulnerable หลังโดน | 2.0s |
| Boss phase transition invuln | 1.2s |
| Power-up lifetime (ก่อนหายถ้าไม่เก็บ) | 12s |
| BlastRing visual | 0.45s |
| ShooterEnemy beam timeout | 4.0s |

---

## 21. Design Patterns ที่ใช้

| Pattern | ใช้ที่ไหน | รายละเอียด |
|---|---|---|
| **Strategy** | `BulletPattern` | แต่ละ `BulletPattern` subclass ใช้งานได้แทนกันโดยไม่ต้องเปลี่ยน caller |
| **Template Method** | `Enemy.update()` | base class กำหนด flow (`updateEntryFollow` → `updateAI` → `updateShooting`), subclass override เฉพาะ `updateAI()` |
| **Observer-lite** | `World.flush()` | ไม่ add/remove ขณะ iterate — defer ผ่าน `pendingAdd`, ล้างทีเดียวตอน flush |
| **Factory Method** | `WaveManager.makeEnemy()` | สร้าง Enemy subclass ตาม string type |
| **Command** | `Player.requestSpecialFire()` | EDT post volatile flag, game thread consume ถัดไป (thread-safe) |
| **Record (Java 16+)** | `EntryPath.Pt`, `SpawnEntry`, `ConvoyDecision` | immutable value objects สำหรับ data-carrying |
| **Double Buffer** | `GamePanel.render()` | `BufferedImage` (software) → blit → `BufferStrategy` (hardware) |

---

## 22. Changelog

### v3.4 — Kill Progress Gauge, DualFighter Damage Color & HUD Polish
- **Kill progress gauge:** `world.killProgress` นับฆ่า 0→100 → `player.gainLife()` แล้วรีเซ็ต; HUD แสดง `♥ +1 xx/100` + bar สีชมพูระหว่าง LIVES และ buff rows; `world.onEnemyKilled()` consolidate kill logic ทุกจุด (bullet hit, body collision)
- **DualFighter damage color:** HP=2 → สีเขียว, HP=1 → สีแดงทั้ง body/wing/cockpit/glow/pips; explosion ก็ใช้สีตาม HP ขณะตาย
- **HUD buff colors:** แก้ให้ตรงกับสี item ที่หยิบ
- **HUD buff spacing:** ขยาย row height 20→26px และเพิ่ม gap ระหว่าง kill gauge กับ buff rows อ่านง่ายขึ้น
- **Menu power-up redesign:** กล่องใหญ่ขึ้น (96×24px จาก 36×17px), font 13/11px (จาก 10/9px), style ตรง PowerUp.draw() จริง (glow + darker fill + border), กระจายเต็มหน้าจอ 5 ช่อง
- **Power-up drop rate:** 12% → 20%

### v3.3 — Shield Stacking, RAPID+Shot Stacking & HUD Countdown Bars
- **Shield stacking:** โล่ทุกชั้นมี timer แยก 15s — `shieldTimers: List<Double>` แทน boolean + single timer; โล่ที่เวลาน้อยสุดรับดาเมจก่อนเสมอ; HUD แสดง `⬡ SHIELD x{n}` เมื่อมีหลายชั้น
- **RAPID_FIRE stacking:** แยก `rapidTimer` ออกจาก shot-type enum (`PowerUpState` เหลือ `NONE|DOUBLE|SPREAD`) — สามารถ stack RAPID กับ DOUBLE หรือ SPREAD ได้พร้อมกัน ยิงกระจาย/คู่ด้วยความเร็ว rapid
- **HUD countdown bars:** แต่ละ buff แสดง bar นับถอยหลัง (DOUBLE, SPREAD, RAPID, SHIELD, SCORE_MULT) — y-position dynamic ไม่ hardcode
- **SCORE_MULT bug fix:** เปลี่ยนจาก frame counter (`int, --`) เป็น `double -= dt` — ระยะเวลา 10s แน่นอนไม่ขึ้นกับ FPS
- **Shield duration:** 10s → 15s

### v3.2 — Menu Redesign, Mute Toast & Pause Navigation (Initial commit)
- **Menu enemy roster:** วาดศัตรูครบ 6 ตัว (Drone, OrbitBug, WaveBug, DiveBug, ShooterEnemy, Boss) จาก sprite จริง จัด 2 แถว × 3 พร้อมชื่อและคะแนน; Boss หมุนตาม animation
- **Menu layout:** จัดเป็น section ชัดเจน (ENEMY ROSTER / CONTROLS / POWER-UPS) คั่น separator, controls 2 คอลัมน์, power-up ครบ 5 ชนิด
- **Mute toast:** กด M → ป้าย `♪ SOUND ON` / `✕ SOUND OFF` โผล่พร้อม fade in/out นาน 2.5s (ลบ permanent "MUTED [M]" เดิม)
- **Pause → R:** กด R บนหน้า PAUSED กลับหน้าเมนูได้; overlay แสดง hint `ESC to resume  |  R to menu`
- **Laser volume:** ลด −15 dB ผ่าน `FloatControl.MASTER_GAIN`

### v3.1 — DualFighter Survivability & Doc Sync
- **DualFighter HP 2:** ยานเสริมรับได้ 2 hit (มี HP pips) แทนตายทันที 1 hit
- **Shared Shield:** SHIELD power-up แชร์โล่ให้ DualFighter ทุกลำ (`applyShield`)
- **DualFighter fire fix:** ยิงพร้อม Player ทุกนัด (เดิม cooldown แยกทำให้แทบไม่ยิงตาม)
- **Cleanup:** ลบ unused imports 6 จุด, รวม formation-geometry constants ใน WaveManager, แก้ dead dedup check ใน `SoundManager.playBgm`
- **Docs:** sync ShooterEnemy เป็น tractor-beam capture, เพิ่มหมวดรวมความน่าจะเป็น & ตัวคูณ

### v3.0 — Special Ammo, Rendering & Scaling Overhaul
- **Special (explosive) ammo:** ได้จาก Boss, ยิงด้วย SHIFT → AoE radius 150px damage 20
- **BlastRing:** วงแสง visual แสดง blast radius 0.45s
- **Active rendering rewrite:** Canvas + BufferStrategy + BufferedImage back-buffer → เร็วขึ้น 30×
- **Sub-pixel rendering:** fractional translate ทำให้ motion smooth ไม่กระตุก
- **Boss HP compounding:** +15% per 10-wave tier (แทน flat +1 ต่อ 10 wave)
- **Boss reinforcements:** boss summon 8 minions เมื่อ escort < 5
- **Wave batch system:** wave ≥ 6 เพิ่ม count 20%/wave, split เป็น equal batches
- **Mixed convoy interleaving:** 2 สีสลับ 1-2-1-2 ใน busier waves
- **Entry from bottom edge:** convoy enter จาก top หรือ bottom สุ่มได้

### v2.0 — Entry Path & Polish
- ระบบ **follow-the-leader** entry path ด้วย arc-length parameterized waypoints
- **Figure-8 path** 3 segments C1-continuous (lead-in + loop flourish + settle)
- **4 convoy format** สุ่มต่อกลุ่ม
- **Crossover** path — sweep across จอก่อน loop
- **Smooth return** Bézier curve แทนการ teleport
- **Swoop 20%** — snapshot player, dive ทะลุจอ, arc กลับขึ้น
- **EnergyWave** cone/fan shape พร้อม arc-length gradient

### v1.0 — Core Systems
- 5 enemy types + Boss ครบ
- OrbitBug orbit dive + snapshot
- WaveBug wave convoy formation + death shot
- DiveBug 4-phase + EnergyWave
- ShooterEnemy Capture mechanic (GhostShip + DualFighter rescue) + Bouncer
- Boss 3 phases, firing interval scaling per HP + respawn count
- Speed scaling: 75% base + ramp ทุก 2 wave (Drone/Boss exempt)
- DualFighter ยานเสริม sync + shoot
- Power-up system 5 ประเภท drop 12%

---

*GALAGA OOP Edition — Java Arcade Project (~3,359 lines across 25 source files)*
