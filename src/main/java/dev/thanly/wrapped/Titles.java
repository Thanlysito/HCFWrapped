package dev.thanly.wrapped;

/**
 * Elige el "titulo divertido" del jugador: la estadistica en la que mas destaca
 * comparado con el promedio del servidor (Adicto a las Pots, Maestro del Pearl...).
 */
public final class Titles {

    /** Estadisticas que dan titulo, en orden de prioridad si hay empate. */
    private static final String[] STATS = {"kills", "streak", "pots", "pearls", "gapples", "diamonds", "damage", "playtime"};

    private Titles() {
    }

    /** Devuelve la clave del titulo en el archivo de idioma (title.kills, title.pots...). */
    public static String of(HCFWrapped plugin, PlayerStats p) {
        StatsManager sm = plugin.stats();
        String best = null;
        double bestRatio = 1.25; // tiene que estar al menos un 25% por encima del promedio
        for (String stat : STATS) {
            double avg = sm.average(stat);
            if (avg <= 0) {
                continue;
            }
            double ratio = p.get(stat) / avg;
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = stat;
            }
        }
        if (best != null) {
            return "title." + best;
        }
        // Muere mucho y mata poco: "Carne de Cañon" (en vez de "El Sobreviviente", que no pegaba).
        if (p.deaths >= 5 && p.kills < p.deaths / 2.0) {
            return "title.fodder";
        }
        // Casi no muere comparado con lo que juega: "Inmortal".
        if (p.playtimeSeconds >= 3600 && p.deaths <= 1) {
            return "title.immortal";
        }
        return "title.default";
    }
}
