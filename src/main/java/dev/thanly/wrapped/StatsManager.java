package dev.thanly.wrapped;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La temporada actual y las estadisticas de todos los jugadores.
 * Se guarda en plugins/HCFWrapped/seasons/&lt;temporada&gt;.yml
 */
public final class StatsManager {

    private final HCFWrapped plugin;
    private final Map<UUID, PlayerStats> stats = new HashMap<UUID, PlayerStats>();

    private String season;
    private boolean active;
    private boolean ended;

    public StatsManager(HCFWrapped plugin) {
        this.plugin = plugin;
    }

    public String season() {
        return season;
    }

    /** true mientras el mapa esta en curso (solo ahi se cuentan estadisticas). */
    public boolean active() {
        return active;
    }

    /** true cuando la temporada termino y ya se pueden ver los Wrapped. */
    public boolean ended() {
        return ended;
    }

    private File stateFile() {
        return new File(plugin.getDataFolder(), "state.yml");
    }

    private File seasonFile(String name) {
        return new File(plugin.getDataFolder(), "seasons/" + name.replaceAll("[^A-Za-z0-9_-]", "_") + ".yml");
    }

    // ---------- cargar / guardar ----------

    public void load() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
        season = state.getString("season");
        active = state.getBoolean("active", false);
        ended = state.getBoolean("ended", false);
        loadSeasonData();
    }

    /** Lee del disco los jugadores de la temporada actual. */
    private void loadSeasonData() {
        stats.clear();
        if (season == null) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(seasonFile(season));
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                stats.put(id, PlayerStats.read(id, players.getConfigurationSection(key)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping bad player entry " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration state = new YamlConfiguration();
        state.set("season", season);
        state.set("active", active);
        state.set("ended", ended);
        try {
            plugin.getDataFolder().mkdirs();
            state.save(stateFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save state.yml: " + e.getMessage());
        }
        if (season == null) {
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        for (PlayerStats p : stats.values()) {
            p.write(data.createSection("players." + p.id));
        }
        try {
            File f = seasonFile(season);
            f.getParentFile().mkdirs();
            data.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save season data: " + e.getMessage());
        }
    }

    // ---------- temporadas ----------

    /** Empieza una temporada nueva (las anteriores quedan guardadas en seasons/). */
    public void start(String name) {
        save();
        season = name;
        active = true;
        ended = false;
        loadSeasonData(); // si esa temporada ya existia, se continua desde donde quedo
        save();
    }

    public void end() {
        active = false;
        ended = true;
        save();
    }

    // ---------- estadisticas ----------

    /** Las estadisticas del jugador en la temporada actual (las crea si no existen). */
    public PlayerStats of(Player player) {
        PlayerStats p = stats.get(player.getUniqueId());
        if (p == null) {
            p = new PlayerStats(player.getUniqueId(), player.getName());
            stats.put(player.getUniqueId(), p);
        } else {
            p.name = player.getName();
        }
        return p;
    }

    public PlayerStats find(UUID id) {
        return stats.get(id);
    }

    /** Las stats de un jugador conectado: por su UUID y, si no aparece, por su nombre
     *  (pasa si el servidor cambio entre online-mode true/false). */
    public PlayerStats mine(org.bukkit.entity.Player player) {
        PlayerStats p = find(player.getUniqueId());
        return p != null ? p : findByName(player.getName());
    }

    public PlayerStats findByName(String name) {
        for (PlayerStats p : stats.values()) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public String nameOf(String uuid) {
        try {
            PlayerStats p = stats.get(UUID.fromString(uuid));
            return p == null ? "?" : p.name;
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }

    public Collection<PlayerStats> all() {
        return Collections.unmodifiableCollection(stats.values());
    }

    /** Los mejores de una estadistica, de mayor a menor. */
    public List<PlayerStats> top(final String stat, int limit) {
        List<PlayerStats> list = new ArrayList<PlayerStats>();
        for (PlayerStats p : stats.values()) {
            if (p.get(stat) > 0) {
                list.add(p);
            }
        }
        Collections.sort(list, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats a, PlayerStats b) {
                return Double.compare(b.get(stat), a.get(stat));
            }
        });
        return list.size() > limit ? new ArrayList<PlayerStats>(list.subList(0, limit)) : list;
    }

    /** Puesto del jugador en una estadistica (1 = el mejor); 0 si no tiene nada. */
    public int rank(PlayerStats who, String stat) {
        double mine = who.get(stat);
        if (mine <= 0) {
            return 0;
        }
        int better = 0;
        for (PlayerStats p : stats.values()) {
            if (p.get(stat) > mine) {
                better++;
            }
        }
        return better + 1;
    }

    /** Promedio de una estadistica entre quienes jugaron (sirve para elegir el titulo). */
    public double average(String stat) {
        double sum = 0;
        int n = 0;
        for (PlayerStats p : stats.values()) {
            if (p.playtimeSeconds > 0) {
                sum += p.get(stat);
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }
}
