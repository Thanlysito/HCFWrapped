package dev.thanly.wrapped;

import org.bukkit.Material;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Como se muestra cada estadistica: icono del menu, color, simbolo del chat,
 * y los datos de comparacion (puesto, promedio, barra) que usan el menu, el chat y la tarjeta.
 */
public final class StatView {

    /** Estadisticas base, en el orden en que se muestran. */
    public static final List<String> BASE = Arrays.asList(
            "kills", "deaths", "kdr", "streak", "damage", "pots", "pearls", "gapples", "diamonds", "playtime");

    public final String key;
    public final String name;
    public final Material icon;
    public final short data;
    /** Color de chat (&c, &a...). */
    public final String chat;
    /** Simbolo que se ve bien en la fuente de Minecraft 1.8. */
    public final String symbol;
    /** Color para la tarjeta. */
    public final Color awt;
    /** "Mas es mejor"; para las muertes es al reves. */
    public final boolean higherIsBetter;

    private StatView(String key, String name, Material icon, int data, String chat, String symbol, int rgb, boolean higher) {
        this.key = key;
        this.name = name;
        this.icon = icon;
        this.data = (short) data;
        this.chat = chat;
        this.symbol = symbol;
        this.awt = new Color(rgb);
        this.higherIsBetter = higher;
    }

    public static StatView of(HCFWrapped plugin, String key) {
        Messages m = plugin.messages();
        String custom = plugin.customStats().get(key);
        if ("kills".equals(key)) return new StatView(key, m.raw("card.kills"), Material.DIAMOND_SWORD, 0, "&c", "⚔", 0xFF5555, true);
        if ("deaths".equals(key)) return new StatView(key, m.raw("card.deaths"), Material.SKULL_ITEM, 0, "&7", "☠", 0xAAAAAA, false);
        if ("kdr".equals(key)) return new StatView(key, m.raw("card.kdr"), Material.IRON_SWORD, 0, "&e", "✦", 0xFFD84D, true);
        if ("streak".equals(key)) return new StatView(key, m.raw("card.streak"), Material.BLAZE_POWDER, 0, "&6", "✹", 0xFFA02E, true);
        if ("damage".equals(key)) return new StatView(key, m.raw("card.damage"), Material.REDSTONE, 0, "&4", "❤", 0xE53935, true);
        if ("pots".equals(key)) return new StatView(key, m.raw("card.pots"), Material.POTION, 16421, "&d", "✚", 0xFF6BD6, true);
        if ("pearls".equals(key)) return new StatView(key, m.raw("card.pearls"), Material.ENDER_PEARL, 0, "&5", "●", 0x9C5CFF, true);
        if ("gapples".equals(key)) return new StatView(key, m.raw("card.gapples"), Material.GOLDEN_APPLE, 1, "&6", "✪", 0xF5C542, true);
        if ("diamonds".equals(key)) return new StatView(key, m.raw("card.diamonds"), Material.DIAMOND_ORE, 0, "&b", "♦", 0x4DE1FF, true);
        if ("playtime".equals(key)) return new StatView(key, m.raw("card.playtime"), Material.WATCH, 0, "&a", "⌚", 0x5CD65C, true);
        // Estadisticas extra de config.yml (koth, raids...)
        Material icon = Material.PAPER;
        int rgb = 0xFFFFFF;
        if ("koth".equals(key)) {
            icon = Material.BEACON;
            rgb = 0x6FE3FF;
        } else if ("raids".equals(key)) {
            icon = Material.TNT;
            rgb = 0xFF7043;
        }
        return new StatView(key, custom != null ? custom : key, icon, 0, "&f", "◆", rgb, true);
    }

    /** Todas: las base + las extra de config.yml. */
    public static List<String> keys(HCFWrapped plugin) {
        List<String> all = new ArrayList<String>(BASE);
        for (Map.Entry<String, String> e : plugin.customStats().entrySet()) {
            all.add(e.getKey());
        }
        return all;
    }

    /** El valor listo para mostrar (1,234 / 0.29 / 3h 05m / 46 ❤). */
    public static String format(PlayerStats p, String key) {
        double v = p.get(key);
        if ("playtime".equals(key)) {
            return Messages.time((long) v);
        }
        if ("kdr".equals(key)) {
            return String.format("%.2f", v);
        }
        if ("damage".equals(key)) {
            return Messages.num(Math.round(v / 2.0)) + " ❤";
        }
        if ("gapples".equals(key)) {
            return Messages.num(p.gapples) + " (+" + p.godApples + " god)";
        }
        return Messages.num(v);
    }

    public static String formatValue(String key, double v) {
        if ("playtime".equals(key)) {
            return Messages.time((long) v);
        }
        if ("kdr".equals(key)) {
            return String.format("%.2f", v);
        }
        if ("damage".equals(key)) {
            return Messages.num(Math.round(v / 2.0)) + " ❤";
        }
        return Messages.num(v);
    }

    /** Que tan lleno va el jugador comparado con el mejor del mapa (0..1). */
    public static double fill(HCFWrapped plugin, PlayerStats p, String key) {
        List<PlayerStats> top = plugin.stats().top(key, 1);
        if (top.isEmpty()) {
            return 0;
        }
        double best = top.get(0).get(key);
        return best <= 0 ? 0 : Math.max(0, Math.min(1, p.get(key) / best));
    }

    /** Barra de texto para el chat y los menus: &a||||||&8|||||||| */
    public static String bar(double fill, String color) {
        int total = 20;
        int on = (int) Math.round(fill * total);
        StringBuilder b = new StringBuilder(color);
        for (int i = 0; i < total; i++) {
            if (i == on) {
                b.append("&8");
            }
            b.append('|');
        }
        return b.toString();
    }

    /** Lineas de detalle (hover del chat y lore del menu). */
    public static List<String> details(HCFWrapped plugin, PlayerStats p, String key) {
        Messages m = plugin.messages();
        StatsManager sm = plugin.stats();
        StatView v = of(plugin, key);
        int rank = sm.rank(p, key);
        int total = sm.all().size();
        double avg = sm.average(key);
        double mine = p.get(key);
        List<String> out = new ArrayList<String>();
        out.add(m.get("detail.value", "value", format(p, key)));
        if (rank > 0) {
            out.add(m.get("detail.rank", "rank", rank, "total", total));
        } else {
            out.add(m.get("detail.no-rank"));
        }
        if (avg > 0) {
            double ratio = mine / avg;
            String cmp;
            if (Math.abs(ratio - 1) < 0.05) {
                cmp = m.get("detail.like-avg");
            } else if (ratio > 1) {
                cmp = m.get(v.higherIsBetter ? "detail.above" : "detail.above-bad", "x", String.format("%.1f", ratio));
            } else {
                cmp = m.get(v.higherIsBetter ? "detail.below" : "detail.below-good", "x", String.format("%.1f", ratio));
            }
            out.add(m.get("detail.avg", "avg", formatValue(key, avg), "cmp", cmp));
        }
        out.add(Messages.color(bar(fill(plugin, p, key), v.chat)));
        return out;
    }
}
