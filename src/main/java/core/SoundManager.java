package core;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

public final class SoundManager {
    private SoundManager() {}

    public static final String BGM_MENU      = "Menu_Once_Upon_a_Time.wav";
    public static final String BGM_PLAY      = "Play_Battle_Against_a_True_Hero.wav";
    public static final String BGM_GAME_OVER = "End_Fallen_Down_(Reprise).wav";

    private static Clip   bgmClip;
    private static String currentBgm;          // track that should be playing
    private static volatile boolean muted = false;

    private static byte[]      laserData;
    private static AudioFormat laserFormat;

    public static void preload() {
        try (AudioInputStream in = open("laser.wav")) {
            laserFormat = in.getFormat();
            laserData   = in.readAllBytes();
        } catch (Exception e) {
            System.err.println("SFX preload failed (laser.wav): " + e);
        }
    }

    public static synchronized void playBgm(String resource) {
        // Compare against the PREVIOUS track before overwriting it — otherwise the
        // "already playing this track" check is always true and never fires.
        boolean alreadyPlaying = resource.equals(currentBgm)
                && bgmClip != null && bgmClip.isRunning();
        currentBgm = resource;          // always remember, even while muted
        if (muted || alreadyPlaying) return;
        startBgmClip(resource);
    }

    public static synchronized void toggleMute() {
        muted = !muted;
        if (muted) {
            if (bgmClip != null) { bgmClip.stop(); bgmClip.close(); bgmClip = null; }
        } else {
            if (currentBgm != null) startBgmClip(currentBgm);
        }
    }

    public static boolean isMuted() { return muted; }

    private static void startBgmClip(String resource) {
        if (bgmClip != null) { bgmClip.stop(); bgmClip.close(); bgmClip = null; }
        try {
            AudioInputStream in = open(resource);
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            bgmClip = clip;
        } catch (Exception e) {
            System.err.println("BGM load failed (" + resource + "): " + e);
        }
    }

    private static final float LASER_GAIN_DB = -15f;   // ลดเสียง laser ลง 15 dB

    public static void playLaser() {
        if (laserData == null || muted) return;
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(laserFormat, laserData, 0, laserData.length);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), LASER_GAIN_DB));
            }
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) ev.getLine().close();
            });
            clip.start();
        } catch (Exception ignored) {}
    }

    private static AudioInputStream open(String resource) throws Exception {
        InputStream raw = SoundManager.class.getResourceAsStream("/" + resource);
        if (raw == null) throw new java.io.IOException("resource not found: " + resource);
        return AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
    }
}
