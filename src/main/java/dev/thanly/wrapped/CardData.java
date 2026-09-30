package dev.thanly.wrapped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Una "foto" de los datos del jugador para dibujar la tarjeta.
 * Se arma en el hilo principal del servidor y luego se dibuja en otro hilo,
 * asi el dibujo y la subida a Discord no congelan el servidor.
 */
public final class CardData {

    /** Una casilla de la tarjeta: nombre, valor, color, puesto en el top y barra (0..1). */
    public static final class Cell {
        public final String label;
        public final String value;
        public final int color;
        public final int rank;
        public final double fill;

        public Cell(String label, String value, int color, int rank, double fill) {
            this.label = label;
            this.value = value;
            this.color = color;
            this.rank = rank;
            this.fill = fill;
        }
    }

    public String playerName;
    public String season;
    public String serverName;
    public String header;
    public String title;
    public int titleColor;
    public List<Cell> big = new ArrayList<Cell>();
    public List<Cell> grid = new ArrayList<Cell>();
    public String victimLabel;
    public String victim;
    public String victimName;
    public String nemesisLabel;
    public String nemesis;
    public String nemesisName;
    public String ranksLabel;
    public String ranks;
    public String footer;

    /** Color del titulo segun la estadistica en la que destaca. */
    private static int titleColor(String key, int accent) {
        if (key.endsWith(".kills")) return 0xFF5555;
        if (key.endsWith(".streak")) return 0xFFA02E;
        if (key.endsWith(".pots")) return 0xFF6BD6;
        if (key.endsWith(".pearls")) return 0x9C5CFF;
        if (key.endsWith(".gapples")) return 0xF5C542;
        if (key.endsWith(".diamonds")) return 0x4DE1FF;
        if (key.endsWith(".damage")) return 0xE53935;
        if (key.endsWith(".playtime")) return 0x5CD65C;
        if (key.endsWith(".immortal")) return 0xFFFFFF;
        if (key.endsWith(".fodder")) return 0x9E9E9E;
        return accent;
    }

    /** Arma la foto de un jugador con los textos del idioma elegido. */
    public static CardData of(HCFWrapped plugin, PlayerStats p) {
        Messages m = plugin.messages();
        StatsManager sm = plugin.stats();
        CardData c = new CardData();
        c.playerName = p.name;
        c.season = sm.season();
        c.serverName = plugin.settings().getString("server-name", "My HCF");
        c.header = m.raw("card.header");
        String titleKey = Titles.of(plugin, p);
        c.title = Messages.color(m.raw(titleKey)).replaceAll("§.", "");
        c.titleColor = titleColor(titleKey, plugin.accentInt());

        for (String key : new String[]{"kills", "deaths", "kdr", "streak"}) {
            c.big.add(cell(plugin, p, key));
        }
        for (String key : StatView.keys(plugin)) {
            if (key.equals("kills") || key.equals("deaths") || key.equals("kdr") || key.equals("streak")) {
                continue;
            }
            c.grid.add(cell(plugin, p, key));
        }

        Map.Entry<String, Integer> vic = PlayerStats.top(p.victims);
        Map.Entry<String, Integer> nem = PlayerStats.top(p.killers);
        c.victimLabel = m.raw("card.victim");
        c.victimName = vic == null ? null : sm.nameOf(vic.getKey());
        c.victim = vic == null ? "-" : c.victimName + "  x" + vic.getValue();
        c.nemesisLabel = m.raw("card.nemesis");
        c.nemesisName = nem == null ? null : sm.nameOf(nem.getKey());
        c.nemesis = nem == null ? "-" : c.nemesisName + "  x" + nem.getValue();

        StringBuilder r = new StringBuilder();
        String[] rankStats = {"kills", "streak", "pots", "pearls", "diamonds", "playtime"};
        for (String stat : rankStats) {
            int rank = sm.rank(p, stat);
            if (rank >= 1 && rank <= 10) {
                if (r.length() > 0) {
                    r.append("   ");
                }
                r.append('#').append(rank).append(' ').append(m.raw("card." + stat));
            }
        }
        c.ranksLabel = m.raw("card.ranks");
        c.ranks = r.length() == 0 ? "-" : r.toString();
        c.footer = m.raw("card.footer");
        return c;
    }

    private static Cell cell(HCFWrapped plugin, PlayerStats p, String key) {
        StatView v = StatView.of(plugin, key);
        int rank = v.higherIsBetter ? plugin.stats().rank(p, key) : 0;
        double fill = v.higherIsBetter ? StatView.fill(plugin, p, key) : -1;
        return new Cell(v.name, StatView.format(p, key), v.awt.getRGB() & 0xFFFFFF, rank, fill);
    }
}
