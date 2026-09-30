package dev.thanly.wrapped;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HCF Wrapped: cuenta las estadisticas de cada jugador durante el mapa y, al terminar,
 * le muestra su resumen (en el chat y como imagen para Discord), como un "Spotify Wrapped".
 * Hecho para Spigot/Paper 1.8.8 (Java 8).
 */
public final class HCFWrapped extends JavaPlugin {

    private YamlConfiguration config;
    private Messages messages;
    private WrappedMenu menu;
    private StatsManager stats;
    private final Map<String, String> customStats = new LinkedHashMap<String, String>();

    @Override
    public void onEnable() {
        // Para poder dibujar imagenes en un servidor sin pantalla.
        System.setProperty("java.awt.headless", "true");
        saveDefaultConfig();
        messages = new Messages(this);
        stats = new StatsManager(this);
        reloadAll();
        stats.load();

        PluginCommand cmd = getCommand("wrapped");
        if (cmd != null) {
            WrappedCommand executor = new WrappedCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        menu = new WrappedMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);
        StatTracker tracker = new StatTracker(this);
        getServer().getPluginManager().registerEvents(tracker, this);
        tracker.startPlaytimeTask();

        // Guardado automatico cada 5 minutos.
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                stats.save();
            }
        }, 6000L, 6000L);

        getLogger().info("HCFWrapped enabled. Season: " + (stats.season() == null ? "none" : stats.season())
                + (stats.active() ? " (active)" : stats.ended() ? " (ended)" : ""));
    }

    @Override
    public void onDisable() {
        if (stats != null) {
            stats.save();
        }
    }

    /** Lee otra vez config.yml y el idioma. */
    public void reloadAll() {
        reloadConfig();
        messages.load(getConfig().getString("language", "en"));
        customStats.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("custom-stats");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                customStats.put(key.toLowerCase(), cs.getString(key, key));
            }
        }
    }

    /**
     * Lee un .yml siempre en UTF-8. En Windows, Java 8 usa otra codificacion por defecto
     * y las tildes (a, e, n...) salen rotas si se deja que Bukkit lo lea solo.
     */
    public static YamlConfiguration loadUtf8(File file) {
        YamlConfiguration yml = new YamlConfiguration();
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            yml.load(reader);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HCFWrapped] Could not read " + file.getName() + ": " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        return yml;
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = loadUtf8(new File(getDataFolder(), "config.yml"));
        InputStream in = getResource("config.yml");
        if (in != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public FileConfiguration settings() {
        return getConfig();
    }

    public Messages messages() {
        return messages;
    }

    public WrappedMenu menu() {
        return menu;
    }

    public StatsManager stats() {
        return stats;
    }

    /** Estadisticas extra definidas en config.yml: id -> nombre para mostrar. */
    public Map<String, String> customStats() {
        return customStats;
    }

    public CardRenderer renderer() {
        FileConfiguration c = getConfig();
        return new CardRenderer(c.getString("card.accent-color", "#F5C542"),
                c.getString("card.background-top", "#0A0A0A"),
                c.getString("card.background-bottom", "#1E1E1E"),
                c.getString("card.avatar-url", "https://mc-heads.net/avatar/{player}/140"),
                c.getString("card.body-url", "https://mc-heads.net/body/{player}/right"));
    }

    public DiscordWebhook webhook() {
        return new DiscordWebhook(getConfig().getString("discord.webhook-url", ""));
    }

    /** El color de acento como numero, para los embeds de Discord. */
    public int accentInt() {
        try {
            String hex = getConfig().getString("card.accent-color", "#F5C542").replace("#", "");
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0xF5C542;
        }
    }
}
