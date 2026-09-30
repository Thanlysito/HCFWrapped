package dev.thanly.wrapped;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Todo lo que se cuenta de un jugador durante una temporada. */
public final class PlayerStats {

    public final UUID id;
    public String name;

    public int kills;
    public int deaths;
    public int streak;
    public int bestStreak;
    public double damageDealt;
    public double damageTaken;
    public int pots;
    public int gapples;
    public int godApples;
    public int pearls;
    public int diamonds;
    public long playtimeSeconds;

    /** uuid de la victima -> cuantas veces la mato este jugador */
    public final Map<String, Integer> victims = new HashMap<String, Integer>();
    /** uuid del asesino -> cuantas veces mato a este jugador */
    public final Map<String, Integer> killers = new HashMap<String, Integer>();
    /** estadisticas extra que suma el core de HCF: koth, raids... */
    public final Map<String, Long> custom = new HashMap<String, Long>();

    public PlayerStats(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Valor de una estadistica por su nombre (sirve para /wrapped top y los titulos). */
    public double get(String stat) {
        if ("kills".equals(stat)) return kills;
        if ("deaths".equals(stat)) return deaths;
        if ("kdr".equals(stat)) return deaths == 0 ? kills : (double) kills / deaths;
        if ("streak".equals(stat)) return bestStreak;
        if ("damage".equals(stat)) return damageDealt;
        if ("pots".equals(stat)) return pots;
        if ("gapples".equals(stat)) return gapples + godApples;
        if ("pearls".equals(stat)) return pearls;
        if ("diamonds".equals(stat)) return diamonds;
        if ("playtime".equals(stat)) return playtimeSeconds;
        Long c = custom.get(stat);
        return c == null ? 0 : c;
    }

    /** El que mas se repite en el mapa (victima favorita o nemesis); null si esta vacio. */
    public static Map.Entry<String, Integer> top(Map<String, Integer> map) {
        Map.Entry<String, Integer> best = null;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) {
                best = e;
            }
        }
        return best;
    }

    public static void add(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        map.put(key, v == null ? 1 : v + 1);
    }

    // ---------- guardar / leer ----------

    public void write(ConfigurationSection s) {
        s.set("name", name);
        s.set("kills", kills);
        s.set("deaths", deaths);
        s.set("streak", streak);
        s.set("best-streak", bestStreak);
        s.set("damage-dealt", round(damageDealt));
        s.set("damage-taken", round(damageTaken));
        s.set("pots", pots);
        s.set("gapples", gapples);
        s.set("god-apples", godApples);
        s.set("pearls", pearls);
        s.set("diamonds", diamonds);
        s.set("playtime", playtimeSeconds);
        s.set("victims", victims.isEmpty() ? null : victims);
        s.set("killers", killers.isEmpty() ? null : killers);
        s.set("custom", custom.isEmpty() ? null : custom);
    }

    public static PlayerStats read(UUID id, ConfigurationSection s) {
        PlayerStats p = new PlayerStats(id, s.getString("name", "?"));
        p.kills = s.getInt("kills");
        p.deaths = s.getInt("deaths");
        p.streak = s.getInt("streak");
        p.bestStreak = s.getInt("best-streak");
        p.damageDealt = s.getDouble("damage-dealt");
        p.damageTaken = s.getDouble("damage-taken");
        p.pots = s.getInt("pots");
        p.gapples = s.getInt("gapples");
        p.godApples = s.getInt("god-apples");
        p.pearls = s.getInt("pearls");
        p.diamonds = s.getInt("diamonds");
        p.playtimeSeconds = s.getLong("playtime");
        ConfigurationSection v = s.getConfigurationSection("victims");
        if (v != null) {
            for (String k : v.getKeys(false)) {
                p.victims.put(k, v.getInt(k));
            }
        }
        ConfigurationSection k2 = s.getConfigurationSection("killers");
        if (k2 != null) {
            for (String k : k2.getKeys(false)) {
                p.killers.put(k, k2.getInt(k));
            }
        }
        ConfigurationSection c = s.getConfigurationSection("custom");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                p.custom.put(k, c.getLong(k));
            }
        }
        return p;
    }

    private static double round(double d) {
        return Math.round(d * 10.0) / 10.0;
    }
}
