package dev.thanly.wrapped;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Textos del plugin, en plugins/HCFWrapped/lang/&lt;idioma&gt;.yml.
 * En 1.8 los colores se escriben con &amp; (por ejemplo &amp;a verde, &amp;l negrita).
 * Variables: {player}, {season}, {value}... se reemplazan con pairs "clave", "valor".
 */
public final class Messages {

    private static final String[] BUNDLED = {"en", "es"};

    private final HCFWrapped plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Messages(HCFWrapped plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        for (String code : BUNDLED) {
            File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        InputStream in = plugin.getResource("lang/en.yml");
        fallback = in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        lang = file.exists() ? HCFWrapped.loadUtf8(file) : fallback;
    }

    public String raw(String key) {
        String v = lang.getString(key);
        return v != null ? v : fallback.getString(key, key);
    }

    /** Texto con colores y variables reemplazadas. */
    public String get(String key, Object... pairs) {
        return color(fill(raw(key), pairs));
    }

    public List<String> lines(String key, Object... pairs) {
        List<String> src = lang.getStringList(key);
        if (src.isEmpty()) {
            src = fallback.getStringList(key);
        }
        List<String> out = new ArrayList<String>();
        for (String s : src) {
            out.add(color(fill(s, pairs)));
        }
        return out;
    }

    public void send(CommandSender to, String key, Object... pairs) {
        to.sendMessage(color(raw("prefix")) + get(key, pairs));
    }

    public static String fill(String text, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return text;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** "12h 05m", "45m", "0m". */
    public static String time(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h > 0 ? h + "h " + String.format("%02dm", m) : m + "m";
    }

    /** 1234 -> "1,234"; 12.5 -> "12.5". */
    public static String num(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e12) {
            return String.format("%,d", (long) v);
        }
        return String.format("%,.1f", v);
    }
}
