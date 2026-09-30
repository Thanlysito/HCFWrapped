package dev.thanly.wrapped;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

/**
 * Dibuja la tarjeta PNG del Wrapped con Java AWT (viene incluido en Java, no hace falta nada extra).
 * Diseno: fondo oscuro, cuerpo del skin con un brillo del color del titulo, numeros grandes,
 * cada estadistica con su color, su puesto en el top y una barra contra el mejor del mapa.
 */
public final class CardRenderer {

    private static final int W = 1000;
    private static final int PAD = 50;

    private final Color accent;
    private final Color bgTop;
    private final Color bgBottom;
    private final String avatarUrl;
    private final String bodyUrl;

    public CardRenderer(String accentHex, String topHex, String bottomHex, String avatarUrl, String bodyUrl) {
        this.accent = parse(accentHex, new Color(0xF5C542));
        this.bgTop = parse(topHex, new Color(0x0A0A0A));
        this.bgBottom = parse(bottomHex, new Color(0x1E1E1E));
        this.avatarUrl = avatarUrl;
        this.bodyUrl = bodyUrl;
    }

    public byte[] render(CardData d) throws IOException {
        int rows = (d.grid.size() + 1) / 2;
        int gridTop = 480;
        int h = gridTop + rows * 118 + 270;
        BufferedImage img = new BufferedImage(W, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        Color titleColor = new Color(d.titleColor);

        // ---- Fondo con brillo del color del titulo ----
        g.setPaint(new GradientPaint(0, 0, bgTop, 0, h, bgBottom));
        g.fillRect(0, 0, W, h);
        glow(g, 170, 260, 330, titleColor, 90);
        glow(g, W - 80, h - 120, 360, accent, 35);
        g.setColor(accent);
        g.fillRect(0, 0, W, 8);

        // ---- Cabecera ----
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.drawString(d.header, PAD, 72);
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g.setColor(new Color(0xB0B0B0));
        String sub = d.serverName + "  •  " + d.season;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(sub, W - PAD - fm.stringWidth(sub), 72);

        // ---- Cuerpo del skin (o la cara si falla) ----
        BufferedImage body = load(bodyUrl, d.playerName);
        int bodyBoxX = PAD, bodyBoxY = 110, bodyBoxW = 240, bodyBoxH = 340;
        panel(g, bodyBoxX, bodyBoxY, bodyBoxW, bodyBoxH, 24);
        if (body != null) {
            drawFit(g, body, bodyBoxX + 20, bodyBoxY + 20, bodyBoxW - 40, bodyBoxH - 40);
        } else {
            BufferedImage face = load(avatarUrl, d.playerName);
            if (face != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(face, bodyBoxX + 50, bodyBoxY + 100, 140, 140, null);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
        }

        // ---- Nombre y titulo ----
        int rx = bodyBoxX + bodyBoxW + 34;
        int rw = W - PAD - rx;
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 58));
        g.drawString(fit(g, d.playerName, rw), rx, 180);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        fm = g.getFontMetrics();
        String t = "★  " + d.title;
        int pillW = fm.stringWidth(t) + 40;
        g.setColor(new Color(titleColor.getRed(), titleColor.getGreen(), titleColor.getBlue(), 45));
        g.fillRoundRect(rx, 205, pillW, 48, 48, 48);
        g.setColor(titleColor);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(rx, 205, pillW, 48, 48, 48);
        g.drawString(t, rx + 20, 238);

        // ---- Numeros grandes (kills, muertes, K/D, racha) ----
        int by = 290;
        int bw = (rw - 3 * 12) / 4;
        for (int i = 0; i < d.big.size() && i < 4; i++) {
            CardData.Cell c = d.big.get(i);
            int x = rx + i * (bw + 12);
            panel(g, x, by, bw, 160, 20);
            g.setColor(new Color(c.color));
            g.fillRoundRect(x + bw / 2 - 22, by + 16, 44, 5, 5, 5);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 46));
            centered(g, fit(g, c.value, bw - 16), x, bw, by + 90);
            g.setColor(new Color(0xA8A8A8));
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            centered(g, c.label.toUpperCase(), x, bw, by + 124);
            if (c.rank >= 1 && c.rank <= 10) {
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.setColor(accent);
                centered(g, "#" + c.rank, x, bw, by + 148);
            }
        }

        // ---- Cuadricula de estadisticas ----
        int cellW = (W - PAD * 2 - 16) / 2;
        for (int i = 0; i < d.grid.size(); i++) {
            CardData.Cell c = d.grid.get(i);
            int x = PAD + (i % 2) * (cellW + 16);
            int y = gridTop + (i / 2) * 118;
            statCell(g, c, x, y, cellW, 102);
        }

        // ---- Victima favorita y nemesis (con sus caras) ----
        int y = gridTop + rows * 118 + 6;
        duo(g, PAD, y, cellW, d.victimLabel, d.victim, d.victimName, new Color(0x5CD65C));
        duo(g, PAD + cellW + 16, y, cellW, d.nemesisLabel, d.nemesis, d.nemesisName, new Color(0xFF5555));

        // ---- Puestos en el top ----
        y += 140;
        g.setColor(accent);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(d.ranksLabel, PAD, y);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g.drawString(fit(g, d.ranks, W - PAD * 2), PAD, y + 34);

        // ---- Pie ----
        g.setColor(new Color(0x6A6A6A));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        centered(g, d.footer, 0, W, h - 26);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void statCell(Graphics2D g, CardData.Cell c, int x, int y, int w, int h) {
        Color col = new Color(c.color);
        panel(g, x, y, w, h, 18);
        // Franja de color a la izquierda
        g.setColor(col);
        g.fillRoundRect(x, y + 14, 6, h - 28, 6, 6);
        // Nombre
        g.setColor(new Color(0xA8A8A8));
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.drawString(c.label.toUpperCase(), x + 26, y + 32);
        // Valor
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        g.drawString(fit(g, c.value, w - 130), x + 26, y + 72);
        // Puesto
        if (c.rank >= 1 && c.rank <= 10) {
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            String r = "#" + c.rank;
            int bw = fm.stringWidth(r) + 22;
            g.setColor(c.rank == 1 ? accent : new Color(255, 255, 255, 30));
            g.fillRoundRect(x + w - bw - 18, y + 16, bw, 30, 30, 30);
            g.setColor(c.rank == 1 ? Color.BLACK : Color.WHITE);
            g.drawString(r, x + w - bw - 18 + 11, y + 37);
        }
        // Barra contra el mejor del mapa
        if (c.fill >= 0) {
            int bx = x + 26, bw = w - 52, byy = y + h - 16;
            g.setColor(new Color(255, 255, 255, 22));
            g.fillRoundRect(bx, byy, bw, 6, 6, 6);
            if (c.fill > 0) {
                g.setColor(col);
                g.fillRoundRect(bx, byy, (int) Math.max(6, bw * c.fill), 6, 6, 6);
            }
        }
    }

    private void duo(Graphics2D g, int x, int y, int w, String label, String value, String who, Color col) {
        panel(g, x, y, w, 110, 18);
        int tx = x + 26;
        if (who != null) {
            BufferedImage face = load(avatarUrl, who);
            if (face != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(face, x + 22, y + 23, 64, 64, null);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                tx = x + 106;
            }
        }
        g.setColor(col);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(label, tx, y + 44);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(fit(g, value, x + w - tx - 20), tx, y + 82);
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, int r) {
        g.setColor(new Color(255, 255, 255, 16));
        g.fillRoundRect(x, y, w, h, r, r);
        g.setColor(new Color(255, 255, 255, 34));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, r, r);
    }

    private static void glow(Graphics2D g, int cx, int cy, int radius, Color c, int alpha) {
        Color in = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        Color out = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[]{0f, 1f}, new Color[]{in, out}));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    /** Dibuja la imagen lo mas grande posible dentro del recuadro, sin deformarla. */
    private static void drawFit(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        double s = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
        int dw = (int) (img.getWidth() * s);
        int dh = (int) (img.getHeight() * s);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
    }

    private static void centered(Graphics2D g, String text, int x, int w, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2, baseline);
    }

    /** Recorta el texto con "..." si no cabe. */
    private static String fit(Graphics2D g, String text, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "...") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    /** Descarga una imagen del skin (cara o cuerpo). Si falla devuelve null y se dibuja sin ella. */
    private static BufferedImage load(String template, String name) {
        if (template == null || template.isEmpty() || name == null) {
            return null;
        }
        try {
            URL url = new URL(template.replace("{player}", URLEncoder.encode(name, "UTF-8")));
            URLConnection con = url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "HCFWrapped");
            InputStream in = con.getInputStream();
            try {
                return ImageIO.read(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Color parse(String hex, Color def) {
        try {
            return hex == null ? def : Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
