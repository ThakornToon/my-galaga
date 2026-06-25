package core;

import entities.Player;
import entities.DualFighter;
import entities.EnergyWave;
import entities.GhostShip;
import enemies.Enemy;
import enemies.Boss;
import entities.Bullet;
import entities.PowerUp;
import entities.Particle;
import entities.BlastRing;
import entities.GameObject;
import managers.*;
import effects.StarField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.util.concurrent.locks.LockSupport;

public class GamePanel extends Canvas {
    public static final int W = 600, H = 760;

    private volatile World world;
    private volatile Player player;          // cached so key input never walks world lists
    private StarField stars;
    private FormationManager formation;
    private WaveManager waves;
    private CollisionManager collisions;
    private HudRenderer hud;

    // ── Game loop: dedicated thread + active rendering via BufferStrategy ────
    // A real double-buffered, vsync-friendly flip removes the frame-pacing
    // judder of the old Swing passive-repaint loop (which couldn't hold the
    // refresh rate and presented frames unevenly).
    private Thread loopThread;
    private volatile boolean running;
    private int targetFps = 120;
    private long lastTime;
    private int fps, fpsCounter;
    private double fpsTimer;
    private double menuAnim, gameOverTimer;
    private double muteToastTimer = 0;

    private enum Screen { MENU, PLAYING, PAUSED, GAME_OVER }
    private volatile Screen screen = Screen.MENU;

    public GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e)  { handleKey(e, true);  }
            public void keyReleased(KeyEvent e) { handleKey(e, false); }
        });
    }

    private void handleKey(KeyEvent e, boolean pressed) {
        if (!pressed) { relayToPlayer(e, false); return; }
        // M toggles mute from any screen
        if (e.getKeyCode() == KeyEvent.VK_M) { SoundManager.toggleMute(); muteToastTimer = 2.5; return; }
        switch (screen) {
            case MENU     -> { if (e.getKeyCode() == KeyEvent.VK_ENTER)  startPlaying(); }
            case GAME_OVER-> { if (e.getKeyCode() == KeyEvent.VK_ENTER)  { screen = Screen.MENU; SoundManager.playBgm(SoundManager.BGM_MENU); } }
            case PAUSED   -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) screen = Screen.PLAYING;
                if (e.getKeyCode() == KeyEvent.VK_R) { screen = Screen.MENU; SoundManager.playBgm(SoundManager.BGM_MENU); }
            }
            case PLAYING  -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { screen = Screen.PAUSED; return; }
                relayToPlayer(e, true);
            }
        }
    }

    private boolean shiftHeld;   // edge-detect Shift → one special shot per press

    private void relayToPlayer(KeyEvent e, boolean pressed) {
        // Key events arrive on the EDT; use the cached reference so we never
        // iterate world's lists while the game thread is mutating them.
        Player p = player;
        if (p == null) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> p.setMoveLeft(pressed);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> p.setMoveRight(pressed);
            case KeyEvent.VK_SPACE, KeyEvent.VK_Z -> p.setShooting(pressed);
            case KeyEvent.VK_SHIFT -> {
                if (pressed) { if (!shiftHeld) { shiftHeld = true; p.requestSpecialFire(); } }
                else shiftHeld = false;
            }
        }
    }

    public void startGame() {
        stars = new StarField(W, H);
        // Run comfortably ABOVE the refresh rate so the macOS window compositor
        // (which samples at vblank) always has a fresh frame to show — capping
        // at the refresh rate left us overhead-bound at ~56fps, which beats
        // against the 60Hz display and reads as periodic judder.
        try {
            int rr = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDisplayMode().getRefreshRate();
            if (rr > 0 && rr != DisplayMode.REFRESH_RATE_UNKNOWN) targetFps = rr * 2;
        } catch (Exception ignored) {}

        SoundManager.preload();
        SoundManager.playBgm(SoundManager.BGM_MENU);   // MENU is the opening screen
        requestFocusInWindow();
        lastTime   = System.nanoTime();
        running    = true;
        loopThread = new Thread(this::gameLoop, "game-loop");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /**
     * Active-rendering loop on its own thread. All game state is created and
     * mutated only here (see the volatile hand-off from {@link #startPlaying});
     * key events on the EDT only flip cached input booleans.
     */
    private void gameLoop() {
        final double targetNs = 1_000_000_000.0 / targetFps;
        while (running) {
            long frameStart = System.nanoTime();
            double dt = Math.min((frameStart - lastTime) / 1e9, 0.05);
            lastTime  = frameStart;
            fpsTimer += dt;
            fpsCounter++;
            if (fpsTimer >= 1.0) { fps = fpsCounter; fpsCounter = 0; fpsTimer = 0; }

            // One stray exception in a draw/update must not kill the loop and
            // freeze the game (Swing's EDT used to swallow these and continue).
            try {
                tick(dt);
                render();
            } catch (Throwable t) {
                System.err.println("frame error: " + t);
            }

            long sleep = (long)(targetNs - (System.nanoTime() - frameStart));
            if (sleep > 0) LockSupport.parkNanos(sleep);
        }
    }

    private java.awt.image.BufferedImage frame;

    /**
     * Render the scene into a software back-buffer, then blit that single image
     * to the screen. Drawing hundreds of translucent anti-aliased particles
     * straight onto the hardware BufferStrategy surface was pathologically slow
     * (~34ms/frame); the same draws into a BufferedImage take <1ms, and a single
     * image blit is cheap.
     */
    private void render() {
        if (frame == null)
            frame = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D fg = frame.createGraphics();
        renderScene(fg);
        fg.dispose();

        BufferStrategy bs = getBufferStrategy();
        if (bs == null) {
            if (isDisplayable()) createBufferStrategy(2);
            return;
        }
        do {
            do {
                Graphics g = bs.getDrawGraphics();
                g.drawImage(frame, 0, 0, null);
                g.dispose();
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
    }

    private void startPlaying() {
        Boss.resetSpawnCount();
        World    w  = new World(W, H);
        Player   p  = new Player(w);
        w.add(p);
        formation   = new FormationManager(w);
        waves       = new WaveManager(w);
        collisions  = new CollisionManager(w);
        hud         = new HudRenderer(W, H);
        player      = p;
        world       = w;
        SoundManager.playBgm(SoundManager.BGM_PLAY);
        screen      = Screen.PLAYING;   // volatile write last → publishes the rest
    }

    private void tick(double dt) {
        menuAnim += dt;
        muteToastTimer = Math.max(0, muteToastTimer - dt);
        stars.update(dt);

        if (screen == Screen.PLAYING) {
            world.totalTime += dt;
            for (GameObject go : world.all()) if (go.isAlive()) go.update(dt);
            world.particles().removeIf(p -> { p.update(dt); return !p.alive; });
            formation.update(dt);
            waves.update(dt);
            collisions.update(dt);
            world.flush();
            if (world.gameOver) { screen = Screen.GAME_OVER; gameOverTimer = 0; SoundManager.playBgm(SoundManager.BGM_GAME_OVER); }
        }
        if (screen == Screen.GAME_OVER) gameOverTimer += dt;
    }

    // AWT may call these on expose/resize; the loop thread owns rendering, so
    // make them no-ops to avoid touching game state off the loop thread.
    @Override public void update(Graphics g) { /* active rendering only */ }
    @Override public void paint(Graphics g)  { /* active rendering only */ }

    private void renderScene(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Sub-pixel accurate shape/stroke placement so motion reads smoothly
        // instead of snapping to the integer pixel grid each frame.
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        switch (screen) {
            case MENU     -> drawMenu(g2);
            case PLAYING  -> drawGame(g2);
            case PAUSED   -> { drawGame(g2); drawOverlay(g2, "PAUSED", "ESC to resume  |  R to menu", new Color(0,200,255)); }
            case GAME_OVER-> { drawGame(g2); drawGameOver(g2); }
        }
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(60, 60, 80));
        g2.drawString("FPS:" + fps, W - 52, H - 6);
        if (muteToastTimer > 0) drawMuteToast(g2);
    }

    private void drawGame(Graphics2D g2) {
        g2.setColor(new Color(2, 4, 18));
        g2.fillRect(0, 0, W, H);
        stars.draw(g2);
        for (Particle     p  : world.particles())             p.draw(g2);
        for (BlastRing    br : world.allOf(BlastRing.class))  drawSub(g2, br);
        for (EnergyWave   ew : world.allOf(EnergyWave.class)) drawSub(g2, ew);
        for (GhostShip    gs : world.allOf(GhostShip.class))  drawSub(g2, gs);
        for (PowerUp      pu : world.allOf(PowerUp.class))    drawSub(g2, pu);
        for (Enemy        en : world.allOf(Enemy.class))       drawSub(g2, en);
        for (DualFighter  df : world.allOf(DualFighter.class)) drawSub(g2, df);
        for (Bullet        b : world.allOf(Bullet.class))       drawSub(g2, b);
        for (Player        p : world.allOf(Player.class))       drawSub(g2, p);
        hud.draw(g2, world);
    }

    /**
     * Draw one object with a sub-pixel translation equal to the fractional part
     * of its position. The sprite still builds its shapes from integer pixel
     * coordinates, but this shift places them at the true sub-pixel position so
     * motion no longer snaps to the pixel grid each frame (anti-aliasing renders
     * the fraction). The shift is always < 1px, so it is imperceptible at rest.
     */
    private void drawSub(Graphics2D g2, GameObject o) {
        Graphics2D gg = (Graphics2D) g2.create();
        gg.translate(o.getX() - (int) o.getX(), o.getY() - (int) o.getY());
        o.draw(gg);
        gg.dispose();
    }

    private void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(2, 4, 18));
        g2.fillRect(0, 0, W, H);
        stars.draw(g2);
        double t = menuAnim;
        FontMetrics fm;

        // ── Title ─────────────────────────────────────────────────────────────
        for (int i = 5; i > 0; i--) {
            g2.setColor(new Color(0, 150, 255, i * 9));
            g2.setFont(new Font("Monospaced", Font.BOLD, 72 + i * 2));
            fm = g2.getFontMetrics();
            g2.drawString("GALAGA", (W - fm.stringWidth("GALAGA")) / 2, (int)(120 + Math.sin(t)*5));
        }
        g2.setFont(new Font("Monospaced", Font.BOLD, 72));
        g2.setColor(new Color(0, 220, 255));
        fm = g2.getFontMetrics();
        g2.drawString("GALAGA", (W - fm.stringWidth("GALAGA")) / 2, (int)(120 + Math.sin(t)*5));

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.setColor(new Color(180, 180, 255, (int)(160 + Math.sin(t*2)*55)));
        fm = g2.getFontMetrics();
        String sub = "OOP ARCADE EDITION";
        g2.drawString(sub, (W - fm.stringWidth(sub)) / 2, 148);

        g2.setFont(new Font("Monospaced", Font.BOLD, 17));
        g2.setColor(new Color(100, 200, 255, (int)(185 + Math.sin(t*3)*60)));
        fm = g2.getFontMetrics();
        String press = "PRESS ENTER TO START";
        g2.drawString(press, (W - fm.stringWidth(press)) / 2, 174);

        // ── Enemy Roster ──────────────────────────────────────────────────────
        drawMenuSep(g2, 187);
        drawMenuHeader(g2, "ENEMY ROSTER", 203);

        int[] rowX = {100, 300, 500};
        // row 1: Drone, OrbitBug, WaveBug
        String[][] row1 = {{"DRONE","100 pts"},{"ORBIT BUG","150 pts"},{"WAVE BUG","160 pts"}};
        for (int i = 0; i < 3; i++) {
            int ey = (int)(257 + Math.sin(t * 1.4 + i * 1.1) * 7);
            drawPreviewEnemy(g2, rowX[i], ey, i, t);
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(enemyPreviewColor(i));
            fm = g2.getFontMetrics();
            g2.drawString(row1[i][0], rowX[i] - fm.stringWidth(row1[i][0])/2, 286);
            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g2.setColor(new Color(140, 140, 190));
            fm = g2.getFontMetrics();
            g2.drawString(row1[i][1], rowX[i] - fm.stringWidth(row1[i][1])/2, 299);
        }
        // row 2: DiveBug, ShooterEnemy, Boss
        String[][] row2 = {{"DIVE BUG","200 pts"},{"SHOOTER","250 pts"},{"BOSS","5000 pts"}};
        for (int i = 0; i < 3; i++) {
            int ey = (int)(350 + Math.sin(t * 1.4 + i * 1.1 + 1.0) * 7);
            drawPreviewEnemy(g2, rowX[i], ey, i + 3, t);
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(enemyPreviewColor(i + 3));
            fm = g2.getFontMetrics();
            g2.drawString(row2[i][0], rowX[i] - fm.stringWidth(row2[i][0])/2, 379);
            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g2.setColor(new Color(140, 140, 190));
            fm = g2.getFontMetrics();
            g2.drawString(row2[i][1], rowX[i] - fm.stringWidth(row2[i][1])/2, 392);
        }

        // ── Controls ──────────────────────────────────────────────────────────
        drawMenuSep(g2, 407);
        drawMenuHeader(g2, "CONTROLS", 423);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.setColor(new Color(150, 150, 205));
        String[][] cols = {
            {"← / A  :  MOVE LEFT",  "SPACE / Z  :  SHOOT",  "ESC  :  PAUSE"},
            {"→ / D  :  MOVE RIGHT", "SHIFT  :  SPECIAL",    "M  :  MUTE / UNMUTE"}
        };
        int[] colX = {50, 316};
        for (int c = 0; c < 2; c++)
            for (int r = 0; r < 3; r++)
                g2.drawString(cols[c][r], colX[c], 442 + r * 20);

        // ── Power-ups ─────────────────────────────────────────────────────────
        drawMenuSep(g2, 493);
        drawMenuHeader(g2, "POWER-UPS", 509);

        String[] pups = {"2X=Double Shot","SP=Spread Shot","RF=Rapid Fire","SH=Shield","SC=Score ×2"};
        Color[] pc = {new Color(100,255,100), new Color(100,200,255),
                      new Color(255,150,50),  new Color(255,220,0), new Color(255,100,255)};
        int[] pupX = {32, 142, 272, 390, 500};
        for (int i = 0; i < 5; i++) {
            int px = pupX[i], py = 530;
            g2.setColor(pc[i]);
            g2.fillRoundRect(px, py - 13, 36, 17, 5, 5);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            fm = g2.getFontMetrics();
            String key = pups[i].split("=")[0];
            g2.drawString(key, px + 18 - fm.stringWidth(key)/2, py);
            g2.setColor(new Color(150, 150, 200));
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            fm = g2.getFontMetrics();
            String lbl = pups[i].split("=")[1];
            g2.drawString(lbl, px + 18 - fm.stringWidth(lbl)/2, py + 14);
        }

        // ── Footer ────────────────────────────────────────────────────────────
        drawMenuSep(g2, 556);
        g2.setColor(new Color(55, 55, 95));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        fm = g2.getFontMetrics();
        String copy = "© GALAGA OOP — JAVA ARCADE";
        g2.drawString(copy, (W - fm.stringWidth(copy)) / 2, H - 14);
    }

    private void drawMenuSep(Graphics2D g2, int y) {
        g2.setColor(new Color(40, 60, 110, 170));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(18, y, W - 18, y);
    }

    private void drawMenuHeader(Graphics2D g2, String text, int y) {
        String s = "— " + text + " —";
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(new Color(90, 150, 255, 200));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    private Color enemyPreviewColor(int type) {
        return switch (type) {
            case 0 -> new Color(200, 80,  255);
            case 1 -> new Color(50,  200, 255);
            case 2 -> new Color(255, 180, 0);
            case 3 -> new Color(255, 100, 50);
            case 4 -> new Color(255, 50,  100);
            case 5 -> new Color(255, 140, 40);
            default -> Color.WHITE;
        };
    }

    // ── Per-type enemy preview draws (match actual game sprites) ───────────────

    private void drawPreviewEnemy(Graphics2D g2, int x, int y, int type, double t) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (type) {
            case 0 -> drawPrevDrone(g, x, y);
            case 1 -> drawPrevOrbit(g, x, y, t);
            case 2 -> drawPrevWave(g, x, y);
            case 3 -> drawPrevDive(g, x, y);
            case 4 -> drawPrevShooter(g, x, y);
            case 5 -> drawPrevBoss(g, x, y, t);
        }
        g.dispose();
    }

    private void drawPrevDrone(Graphics2D g, int x, int y) {
        Color pri = new Color(200,80,255), sec = new Color(140,40,200), acc = new Color(255,150,255);
        int w = 28, h = 24;
        g.setColor(new Color(200,80,255,28)); g.fillOval(x-w/2-5,y-h/2-5,w+10,h+10);
        int[] px={x,x-w/2,x-w/4,x+w/4,x+w/2}, py={y-h/2,y+h/4,y+h/2,y+h/2,y+h/4};
        g.setColor(sec); g.fillPolygon(px,py,5);
        g.setColor(pri); g.setStroke(new BasicStroke(1f)); g.drawPolygon(px,py,5);
        g.setColor(acc); g.fillOval(x-6,y-2,5,5); g.fillOval(x+2,y-2,5,5);
    }

    private void drawPrevOrbit(Graphics2D g, int x, int y, double t) {
        Color pri = new Color(50,200,255), sec = new Color(0,120,200), acc = new Color(150,255,255);
        int w = 30, h = 26;
        g.setColor(new Color(50,200,255,28)); g.fillOval(x-w/2-5,y-h/2-5,w+10,h+10);
        g.setColor(sec); g.fillOval(x-w/2+4,y-h/2+4,w-8,h-8);
        g.setColor(pri); g.setStroke(new BasicStroke(2f)); g.drawOval(x-w/2,y-h/2,w,h);
        for (int i = 0; i < 4; i++) {
            double a = t*2 + i*Math.PI/2;
            g.setColor(acc); g.fillOval((int)(x+Math.cos(a)*w/2)-3,(int)(y+Math.sin(a)*h/2)-3,6,6);
        }
    }

    private void drawPrevWave(Graphics2D g, int x, int y) {
        Color pri = new Color(255,180,0), sec = new Color(200,120,0), acc = new Color(255,240,100);
        int w = 30, h = 26;
        g.setColor(new Color(255,180,0,28)); g.fillOval(x-w/2-5,y-h/2-5,w+10,h+10);
        int[] lx={x,x-w/2,x-w/3,x-2}, ly={y-h/4,y-h/2,y+h/2,y+2};
        int[] rx={x,x+w/2,x+w/3,x+2}, ry={y-h/4,y-h/2,y+h/2,y+2};
        g.setColor(pri); g.fillPolygon(lx,ly,4); g.fillPolygon(rx,ry,4);
        g.setColor(sec); g.fillOval(x-5,y-h/2,10,h);
        g.setColor(acc); g.fillOval(x-3,y-4,6,8);
    }

    private void drawPrevDive(Graphics2D g, int x, int y) {
        Color pri = new Color(255,100,50), sec = new Color(200,50,0), acc = new Color(255,200,100);
        int w = 30, h = 28;
        g.setColor(new Color(255,100,50,28)); g.fillOval(x-w/2-5,y-h/2-5,w+10,h+10);
        int[] px={x,x-w/2,x,x+w/2}, py={y-h/2,y,y+h/2,y};
        g.setColor(sec); g.fillPolygon(px,py,4);
        g.setColor(pri); g.setStroke(new BasicStroke(1.5f)); g.drawPolygon(px,py,4);
        g.setColor(acc); g.fillOval(x-4,y-4,8,8);
    }

    private void drawPrevShooter(Graphics2D g, int x, int y) {
        Color pri = new Color(255,50,100), sec = new Color(180,0,60), acc = new Color(255,150,180);
        int w = 34, h = 30;
        g.setColor(new Color(255,50,100,28)); g.fillOval(x-w/2-5,y-h/2-5,w+10,h+10);
        g.setColor(sec);
        g.fillRect(x-w/2, y-h/6, w, h/3);
        g.fillRect(x-w/6, y-h/2, w/3, h);
        g.setColor(pri); g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x-w/2, y-h/6, w, h/3);
        g.drawRect(x-w/6, y-h/2, w/3, h);
        g.setColor(acc); g.fillOval(x-6,y-6,12,12);
    }

    private void drawPrevBoss(Graphics2D g, int x, int y, double t) {
        Color pri = new Color(255,80,0), sec = new Color(180,30,0), acc = new Color(255,220,0);
        int bw = 42, bh = 36;
        g.setColor(new Color(255,80,0,35)); g.fillOval(x-bw/2-8,y-bh/2-8,bw+16,bh+16);
        double rot = Math.toRadians(t*30);
        int[] bx = new int[6], by = new int[6];
        for (int i = 0; i < 6; i++) {
            double a = rot + i*Math.PI*2/6;
            bx[i] = (int)(x + Math.cos(a)*bw/2);
            by[i] = (int)(y + Math.sin(a)*bh/2);
        }
        g.setColor(sec); g.fillPolygon(bx,by,6);
        g.setColor(pri); g.setStroke(new BasicStroke(2.5f)); g.drawPolygon(bx,by,6);
        g.setColor(acc); g.setStroke(new BasicStroke(1.5f)); g.drawOval(x-bw/4,y-bh/4,bw/2,bh/2);
        g.setColor(new Color(255,200,50)); g.fillOval(x-7,y-7,14,14);
    }

    // ── Mute toggle toast ─────────────────────────────────────────────────────

    private void drawMuteToast(Graphics2D g2) {
        double alpha;
        if (muteToastTimer > 2.2)       alpha = (2.5 - muteToastTimer) / 0.3;
        else if (muteToastTimer < 0.45) alpha = muteToastTimer / 0.45;
        else                            alpha = 1.0;
        alpha = Math.max(0, Math.min(1, alpha));

        boolean muted = SoundManager.isMuted();
        String  label = muted ? "✕  SOUND OFF" : "♪  SOUND ON";
        Color   bg    = muted ? new Color(110, 18, 18) : new Color(8, 55, 100);
        Color   fg    = muted ? new Color(255, 90,  90) : new Color(80, 215, 255);
        Color   bord  = muted ? new Color(210, 55,  55) : new Color(55, 180, 255);

        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label) + 30, th = 38;
        int tx = (W - tw) / 2, ty = H - 74;

        Graphics2D gt = (Graphics2D) g2.create();
        gt.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        gt.setColor(bg);           gt.fillRoundRect(tx, ty, tw, th, 12, 12);
        gt.setColor(bord);         gt.setStroke(new BasicStroke(2f)); gt.drawRoundRect(tx, ty, tw, th, 12, 12);
        gt.setColor(fg);           gt.drawString(label, tx + 15, ty + th/2 + fm.getAscent()/2 - 2);
        gt.dispose();
    }

    private void drawOverlay(Graphics2D g2, String title, String sub, Color titleColor) {
        g2.setColor(new Color(0,0,0,150)); g2.fillRect(0,0,W,H);
        g2.setFont(new Font("Monospaced", Font.BOLD, 48)); g2.setColor(titleColor);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (W - fm.stringWidth(title))/2, H/2-20);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 16)); g2.setColor(new Color(180,180,255));
        fm = g2.getFontMetrics();
        g2.drawString(sub, (W - fm.stringWidth(sub))/2, H/2+20);
    }

    private void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(0,0,W,H);
        double pulse = Math.sin(gameOverTimer*3)*0.3+0.7;
        g2.setColor(new Color(255,(int)(50*pulse),50));
        g2.setFont(new Font("Monospaced", Font.BOLD, 52));
        FontMetrics fm = g2.getFontMetrics();
        String go = "GAME OVER";
        g2.drawString(go, (W-fm.stringWidth(go))/2, H/2-60);
        g2.setColor(new Color(0,200,255)); g2.setFont(new Font("Monospaced",Font.BOLD,22));
        fm = g2.getFontMetrics(); String sc = "FINAL SCORE: "+world.score;
        g2.drawString(sc, (W-fm.stringWidth(sc))/2, H/2);
        g2.setColor(new Color(180,180,255)); g2.setFont(new Font("Monospaced",Font.PLAIN,16));
        fm = g2.getFontMetrics(); String wv = "REACHED WAVE "+world.wave;
        g2.drawString(wv, (W-fm.stringWidth(wv))/2, H/2+30);
        g2.setColor(new Color(255,180,120));
        fm = g2.getFontMetrics(); String ek = "ENEMIES DESTROYED: "+world.kills;
        g2.drawString(ek, (W-fm.stringWidth(ek))/2, H/2+56);
        if (gameOverTimer > 1.5) {
            g2.setColor(new Color(100,255,180,(int)(180+Math.sin(gameOverTimer*4)*60)));
            g2.setFont(new Font("Monospaced",Font.BOLD,16)); fm = g2.getFontMetrics();
            String ret = "PRESS ENTER TO RETURN";
            g2.drawString(ret, (W-fm.stringWidth(ret))/2, H/2+96);
        }
    }
}