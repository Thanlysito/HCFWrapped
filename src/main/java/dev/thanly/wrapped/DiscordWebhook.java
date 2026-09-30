package dev.thanly.wrapped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Publica en un canal de Discord usando un webhook (Ajustes del canal -> Integraciones -> Webhooks).
 * Hecho a mano con HttpURLConnection para no depender de librerias extra.
 * Siempre se llama desde un hilo aparte, nunca desde el hilo principal del servidor.
 */
public final class DiscordWebhook {

    private final String url;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public boolean configured() {
        return url != null && url.startsWith("https://") && url.contains("/api/webhooks/");
    }

    /** Envia un mensaje con una imagen PNG adjunta (se muestra dentro de un embed). */
    public void sendImage(String content, String embedTitle, int color, String fileName, byte[] png) throws IOException {
        String json = "{\"content\":" + str(content)
                + ",\"embeds\":[{\"title\":" + str(embedTitle) + ",\"color\":" + color
                + ",\"image\":{\"url\":\"attachment://" + fileName + "\"}}]}";
        String boundary = "----HCFWrapped" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + json + "\r\n");
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n");
        body.write(png);
        write(body, "\r\n--" + boundary + "--\r\n");
        post("multipart/form-data; boundary=" + boundary, body.toByteArray());
    }

    /** Envia un embed de solo texto con campos (para el resumen del servidor). */
    public void sendEmbed(String title, String description, int color, String[][] fields) throws IOException {
        StringBuilder f = new StringBuilder();
        for (String[] field : fields) {
            if (f.length() > 0) {
                f.append(',');
            }
            f.append("{\"name\":").append(str(field[0])).append(",\"value\":").append(str(field[1]))
                    .append(",\"inline\":true}");
        }
        String json = "{\"embeds\":[{\"title\":" + str(title) + ",\"description\":" + str(description)
                + ",\"color\":" + color + ",\"fields\":[" + f + "]}]}";
        post("application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private void post(String contentType, byte[] data) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Content-Type", contentType);
        con.setRequestProperty("User-Agent", "HCFWrapped (https://github.com/Thanlysito)");
        OutputStream out = con.getOutputStream();
        try {
            out.write(data);
        } finally {
            out.close();
        }
        int code = con.getResponseCode();
        if (code >= 300) {
            InputStream err = con.getErrorStream();
            String msg = "";
            if (err != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = err.read(buf)) > 0) {
                    b.write(buf, 0, n);
                }
                err.close();
                msg = new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
            throw new IOException("Discord answered " + code + " " + msg);
        }
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Texto JSON con comillas y caracteres escapados. */
    static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
