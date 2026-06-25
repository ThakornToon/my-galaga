package managers;

import core.World;
import entities.Player;
import entities.DualFighter;
import enemies.Enemy;

import java.awt.*;
import java.util.List;

public class HudRenderer {
    private final int W, H;

    public HudRenderer(int W, int H) {
        this.W = W;
        this.H = H;
    }

    public void draw(Graphics2D g, World world) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Score
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        g2.setColor(new Color(0, 200, 255));
        g2.drawString("SCORE: " + world.score, 12, 28);

        // Wave
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(new Color(200, 200, 255));
        String waveStr = "WAVE " + world.wave;
        g2.drawString(waveStr, W/2 - g2.getFontMetrics().stringWidth(waveStr)/2, 24);

        // Player lives / power-up / shield
        List<Player> players = world.allOf(Player.class);
        if (!players.isEmpty()) {
            Player p = players.get(0);

            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(new Color(100, 255, 180));
            String hearts = "♥ ".repeat(Math.max(0, p.getLives()));
            if (hearts.isEmpty()) hearts = "♥";
            g2.drawString("LIVES: " + hearts.trim(), 12, 52);

            // Active buff rows — each takes 20px (label + bar); y advances per active buff
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            int buffY = 72;

            if (p.getPowerUp() != Player.PowerUpState.NONE) {
                Color col = switch (p.getPowerUp()) {
                    case DOUBLE -> new Color(100, 255, 100);
                    case SPREAD -> new Color(100, 200, 255);
                    default     -> Color.WHITE;
                };
                String label = switch (p.getPowerUp()) {
                    case DOUBLE -> "⬡ DOUBLE";
                    case SPREAD -> "⬡ SPREAD";
                    default     -> "";
                };
                double frac = switch (p.getPowerUp()) {
                    case DOUBLE -> p.getPowerUpTimer() / Player.DOUBLE_DURATION;
                    case SPREAD -> p.getPowerUpTimer() / Player.SPREAD_DURATION;
                    default     -> 0;
                };
                g2.setColor(col);
                g2.drawString(label, 12, buffY);
                drawBuffBar(g2, Math.max(0, frac), col, 12, buffY + 4, 120);
                buffY += 20;
            }

            if (p.isRapid()) {
                Color col = new Color(255, 150, 50);
                g2.setColor(col);
                g2.drawString("⬡ RAPID", 12, buffY);
                drawBuffBar(g2, Math.max(0, p.getRapidTimer() / Player.RAPID_DURATION), col, 12, buffY + 4, 120);
                buffY += 20;
            }

            if (p.hasShield()) {
                Color col = new Color(255, 220, 0);
                String label = p.getShieldCount() > 1 ? "⬡ SHIELD x" + p.getShieldCount() : "⬡ SHIELD";
                g2.setColor(col);
                g2.drawString(label, 12, buffY);
                drawBuffBar(g2, Math.max(0, p.getShieldTimer() / Player.SHIELD_DURATION), col, 12, buffY + 4, 120);
                buffY += 20;
            }

            if (world.scoreMultiplier > 1) {
                Color col = new Color(255, 100, 255);
                g2.setColor(col);
                g2.drawString("⬡ x" + world.scoreMultiplier + " SCORE", 12, buffY);
                drawBuffBar(g2, Math.max(0, p.getScoreMultTimer() / Player.SCORE_MULT_DURATION), col, 12, buffY + 4, 120);
                buffY += 20;
            }

            buffY += 4;

            // Dual fighter indicator
            int dfCount = world.allOf(DualFighter.class).size();
            if (dfCount > 0) {
                g2.setFont(new Font("Monospaced", Font.BOLD, 13));
                g2.setColor(new Color(100, 255, 120));
                g2.drawString("✦ WINGMAN x" + dfCount, 12, buffY);
                buffY += 18;
            }

            // Special (explosive) ammo — earned from bosses, fired with Shift
            if (p.getSpecialAmmo() > 0) {
                g2.setFont(new Font("Monospaced", Font.BOLD, 14));
                g2.setColor(new Color(150, 240, 255));
                g2.drawString("◎ SPECIAL x" + p.getSpecialAmmo() + "  [SHIFT]", 12, buffY);
            }
        }

        // Enemy count
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.setColor(new Color(150, 150, 200));
        g2.drawString("ENEMIES: " + world.allOf(Enemy.class).size(), W - 120, 28);

        g2.dispose();
    }

    private void drawBuffBar(Graphics2D g2, double fraction, Color col, int x, int y, int barW) {
        g2.setColor(new Color(50, 50, 50, 180));
        g2.fillRoundRect(x, y, barW, 5, 3, 3);
        g2.setColor(col);
        g2.fillRoundRect(x, y, (int)(barW * fraction), 5, 3, 3);
    }
}