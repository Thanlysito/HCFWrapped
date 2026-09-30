package dev.thanly.wrapped;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /wrapped [jugador]          ver el resumen (menu de cofre)
 * /wrapped chat [jugador]     ver el resumen en el chat
 * /wrapped top &lt;stat&gt;          top 10
 * /wrapped share              tarjeta PNG a Discord
 * /wrapped season start|end   (admin) empezar / terminar la temporada
 * /wrapped add &lt;j&gt; &lt;stat&gt; &lt;n&gt;  (admin) sumar stats de HCF (KOTH, raids...)
 * /wrapped recap              (admin) resumen del servidor
 * /wrapped reload             (admin)
 */
public final class WrappedCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BASE_STATS = Arrays.asList(
            "kills", "deaths", "kdr", "streak", "damage", "pots", "gapples", "pearls", "diamonds", "playtime");

    private final HCFWrapped plugin;
    private final Map<UUID, Long> shareCooldown = new HashMap<UUID, Long>();

    public WrappedCommand(HCFWrapped plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private boolean admin(CommandSender s) {
        return s.hasPermission("hcfwrapped.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();

        if (sub.equals("season")) {
            season(sender, args);
            return true;
        }
        if (sub.equals("add")) {
            add(sender, args);
            return true;
        }
        if (sub.equals("reload")) {
            if (!admin(sender)) {
                msg().send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            msg().send(sender, "reloaded");
            return true;
        }
        if (sub.equals("recap")) {
            recap(sender);
            return true;
        }
        if (sub.equals("top")) {
            if (args.length == 1 && sender instanceof Player) {
                if (canView(sender)) {
                    plugin.menu().openTop((Player) sender, "kills");
                }
                return true;
            }
            top(sender, args.length > 1 ? args[1].toLowerCase() : "kills");
            return true;
        }
        boolean chat = false;
        if (sub.equals("chat")) {
            chat = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (sub.equals("share")) {
            share(sender);
            return true;
        }
        if (sub.equals("help")) {
            for (String line : msg().lines("help")) {
                sender.sendMessage(line);
            }
            if (admin(sender)) {
                for (String line : msg().lines("help-admin")) {
                    sender.sendMessage(line);
                }
            }
            return true;
        }

        // /wrapped  o  /wrapped <jugador>
        if (!canView(sender)) {
            return true;
        }
        PlayerStats target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                msg().send(sender, "players-only");
                return true;
            }
            target = plugin.stats().mine(((Player) sender));
        } else {
            if (!sender.hasPermission("hcfwrapped.others")) {
                msg().send(sender, "no-permission");
                return true;
            }
            target = plugin.stats().findByName(args[0]);
        }
        if (target == null) {
            msg().send(sender, "no-data");
            return true;
        }
        boolean menu = !chat && sender instanceof Player && plugin.settings().getBoolean("open-menu", true);
        if (menu) {
            plugin.menu().openRecap((Player) sender, target);
        } else {
            showRecap(sender, target);
        }
        return true;
    }

    /** Durante el mapa solo los admins ven los resumenes (salvo que la config lo permita). */
    private boolean canView(CommandSender sender) {
        StatsManager sm = plugin.stats();
        if (sm.season() == null) {
            msg().send(sender, "no-season");
            return false;
        }
        if (sm.active() && !plugin.settings().getBoolean("view-during-season", false) && !admin(sender)) {
            msg().send(sender, "wait-for-end");
            return false;
        }
        return true;
    }

    // ---------- resumen en el chat ----------

    private void showRecap(CommandSender to, PlayerStats p) {
        StatsManager sm = plugin.stats();
        Messages m = msg();
        boolean fancy = to instanceof Player;
        to.sendMessage(ChatFormat.bar("&8"));
        to.sendMessage(ChatFormat.center(m.get("chat.header", "player", p.name)));
        to.sendMessage(ChatFormat.center(m.get("chat.subtitle", "title", m.get(Titles.of(plugin, p)), "season", sm.season())));
        to.sendMessage("");

        // Dos columnas; cada estadistica muestra su puesto y el promedio al pasar el mouse.
        List<String> keys = StatView.keys(plugin);
        for (int i = 0; i < keys.size(); i += 2) {
            String left = keys.get(i);
            String right = i + 1 < keys.size() ? keys.get(i + 1) : null;
            String leftText = ChatFormat.padTo(cell(p, left), 158);
            if (!fancy) {
                to.sendMessage(leftText + (right == null ? "" : cell(p, right)));
                continue;
            }
            List<BaseComponent> line = new ArrayList<BaseComponent>();
            line.addAll(Arrays.asList(hover(leftText, StatView.details(plugin, p, left))));
            if (right != null) {
                line.addAll(Arrays.asList(hover(cell(p, right), StatView.details(plugin, p, right))));
            }
            ((Player) to).spigot().sendMessage(line.toArray(new BaseComponent[0]));
        }

        Map.Entry<String, Integer> vic = PlayerStats.top(p.victims);
        Map.Entry<String, Integer> nem = PlayerStats.top(p.killers);
        to.sendMessage("");
        to.sendMessage(m.get("chat.victim", "player", vic == null ? "-" : sm.nameOf(vic.getKey()),
                "count", vic == null ? 0 : vic.getValue()));
        to.sendMessage(m.get("chat.nemesis", "player", nem == null ? "-" : sm.nameOf(nem.getKey()),
                "count", nem == null ? 0 : nem.getValue()));
        to.sendMessage("");

        if (fancy) {
            boolean own = ((Player) to).getUniqueId().equals(p.id) || ((Player) to).getName().equalsIgnoreCase(p.name);
            List<BaseComponent> buttons = new ArrayList<BaseComponent>();
            String menuCmd = own ? "/wrapped" : "/wrapped " + p.name;
            String row = m.get("chat.btn-menu") + "   " + m.get("chat.btn-top") + (own ? "   " + m.get("chat.btn-share") : "");
            int pad = ChatFormat.center(row).length() - row.length();
            buttons.add(new TextComponent(new String(new char[Math.max(0, pad)]).replace('\0', ' ')));
            buttons.addAll(Arrays.asList(button(m.get("chat.btn-menu"), m.get("chat.btn-menu-hover"), menuCmd)));
            buttons.add(new TextComponent("   "));
            buttons.addAll(Arrays.asList(button(m.get("chat.btn-top"), m.get("chat.btn-top-hover"), "/wrapped top")));
            if (own) {
                buttons.add(new TextComponent("   "));
                buttons.addAll(Arrays.asList(button(m.get("chat.btn-share"), m.get("chat.btn-share-hover"), "/wrapped share")));
            }
            ((Player) to).spigot().sendMessage(buttons.toArray(new BaseComponent[0]));
            to.sendMessage(ChatFormat.center(m.get("chat.hint")));
        }
        to.sendMessage(ChatFormat.bar("&8"));
    }

    /** "⚔ Kills: 4" con los colores de la estadistica. */
    private String cell(PlayerStats p, String key) {
        StatView v = StatView.of(plugin, key);
        return Messages.color(" " + v.chat + v.symbol + " &7" + v.name + ": &f" + StatView.format(p, key));
    }

    private static BaseComponent[] hover(String text, List<String> lines) {
        BaseComponent[] parts = TextComponent.fromLegacyText(text);
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                h.append('\n');
            }
            h.append(lines.get(i));
        }
        HoverEvent ev = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(h.toString()));
        for (BaseComponent c : parts) {
            c.setHoverEvent(ev);
        }
        return parts;
    }

    private static BaseComponent[] button(String text, String hoverText, String command) {
        BaseComponent[] parts = TextComponent.fromLegacyText(text);
        HoverEvent hv = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText));
        ClickEvent ck = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        for (BaseComponent c : parts) {
            c.setHoverEvent(hv);
            c.setClickEvent(ck);
        }
        return parts;
    }

    // ---------- top ----------

    private void top(CommandSender sender, String stat) {
        if (!canView(sender)) {
            return;
        }
        if (!BASE_STATS.contains(stat) && !plugin.customStats().containsKey(stat)) {
            msg().send(sender, "unknown-stat", "stats", allStats());
            return;
        }
        sender.sendMessage(ChatFormat.bar("&8"));
        sender.sendMessage(ChatFormat.center(msg().get("top-header", "stat", statName(stat), "season", plugin.stats().season())));
        sender.sendMessage("");
        int i = 1;
        for (PlayerStats p : plugin.stats().top(stat, 10)) {
            String medal = i == 1 ? "&6" : i == 2 ? "&f" : i == 3 ? "&c" : "&e";
            sender.sendMessage(msg().get("top-line", "rank", Messages.color(medal + "#" + i), "player", p.name, "value", format(stat, p.get(stat))));
            i++;
        }
        if (i == 1) {
            sender.sendMessage(msg().get("top-empty"));
        }
        sender.sendMessage(ChatFormat.bar("&8"));
    }

    private String format(String stat, double v) {
        if ("playtime".equals(stat)) {
            return Messages.time((long) v);
        }
        if ("kdr".equals(stat)) {
            return String.format("%.2f", v);
        }
        if ("damage".equals(stat)) {
            return Messages.num(Math.round(v / 2.0)) + " ❤";
        }
        return Messages.num(v);
    }

    private String statName(String stat) {
        String custom = plugin.customStats().get(stat);
        return custom != null ? custom : msg().raw("card." + stat);
    }

    private String allStats() {
        List<String> all = new ArrayList<String>(BASE_STATS);
        all.addAll(plugin.customStats().keySet());
        return String.join(", ", all);
    }

    // ---------- compartir la tarjeta ----------

    private void share(CommandSender sender) {
        if (!(sender instanceof Player)) {
            msg().send(sender, "players-only");
            return;
        }
        if (!canView(sender)) {
            return;
        }
        final Player player = (Player) sender;
        final PlayerStats p = plugin.stats().mine(player);
        if (p == null) {
            msg().send(sender, "no-data");
            return;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.settings().getLong("share-cooldown-seconds", 300) * 1000L;
        Long last = shareCooldown.get(player.getUniqueId());
        if (last != null && now - last < cd && !admin(sender)) {
            msg().send(sender, "share-cooldown", "seconds", (cd - (now - last)) / 1000 + 1);
            return;
        }
        shareCooldown.put(player.getUniqueId(), now);

        // Los datos se copian aqui (hilo principal); el dibujo y Discord van en otro hilo.
        final CardData data = CardData.of(plugin, p);
        final CardRenderer renderer = plugin.renderer();
        final DiscordWebhook hook = plugin.webhook();
        final String content = msg().raw("discord-share").replace("{player}", p.name);
        final String embedTitle = msg().raw("card.header") + " - " + p.name;
        final int color = plugin.accentInt();
        final File folder = new File(plugin.getDataFolder(), "cards");
        msg().send(sender, "share-working");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String resultKey;
                try {
                    byte[] png = renderer.render(data);
                    folder.mkdirs();
                    FileOutputStream out = new FileOutputStream(new File(folder, p.name + ".png"));
                    try {
                        out.write(png);
                    } finally {
                        out.close();
                    }
                    if (hook.configured()) {
                        hook.sendImage(content, embedTitle, color, "wrapped.png", png);
                        resultKey = "share-done";
                    } else {
                        resultKey = "share-saved";
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not share card of " + p.name + ": " + e.getMessage());
                    resultKey = "share-failed";
                }
                final String key = resultKey;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            msg().send(player, key, "player", p.name);
                        }
                    }
                });
            }
        });
    }

    // ---------- admin ----------

    private void season(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            msg().send(sender, "no-permission");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        StatsManager sm = plugin.stats();
        if (action.equals("start") && args.length > 2) {
            sm.start(args[2]);
            Bukkit.broadcastMessage(msg().get("prefix") + msg().get("season-started", "season", args[2]));
        } else if (action.equals("end")) {
            if (!sm.active()) {
                msg().send(sender, "no-active-season");
                return;
            }
            sm.end();
            for (String line : msg().lines("season-ended", "season", sm.season())) {
                Bukkit.broadcastMessage(line);
            }
            if (plugin.settings().getBoolean("discord.recap-on-end", true)) {
                recap(sender);
            }
        } else {
            msg().send(sender, "season-status", "season", sm.season() == null ? "-" : sm.season(),
                    "state", msg().raw(sm.active() ? "state-active" : sm.ended() ? "state-ended" : "state-none"),
                    "players", sm.all().size());
            msg().send(sender, "season-usage");
        }
    }

    /** /wrapped add <jugador> <stat> <cantidad> - para que el core de HCF sume KOTH, raids, etc. */
    private void add(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            msg().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            msg().send(sender, "add-usage");
            return;
        }
        StatsManager sm = plugin.stats();
        if (!sm.active()) {
            return; // fuera de temporada no se cuenta nada (sin mensaje: lo suelen llamar otros plugins)
        }
        String stat = args[2].toLowerCase();
        if (!plugin.customStats().containsKey(stat)) {
            msg().send(sender, "unknown-custom", "stats", String.join(", ", plugin.customStats().keySet()));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            msg().send(sender, "add-usage");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        PlayerStats p = online != null ? sm.of(online) : sm.findByName(args[1]);
        if (p == null) {
            msg().send(sender, "no-data");
            return;
        }
        Long old = p.custom.get(stat);
        p.custom.put(stat, (old == null ? 0 : old) + amount);
        if (sender instanceof Player) {
            msg().send(sender, "added", "player", p.name, "stat", plugin.customStats().get(stat), "value", amount);
        }
    }

    /** Resumen del servidor: top 3 de cada categoria, en el chat y en Discord. */
    private void recap(CommandSender sender) {
        if (!admin(sender)) {
            msg().send(sender, "no-permission");
            return;
        }
        StatsManager sm = plugin.stats();
        if (sm.season() == null) {
            msg().send(sender, "no-season");
            return;
        }
        List<String> stats = new ArrayList<String>(Arrays.asList("kills", "streak", "pots", "pearls", "diamonds", "playtime"));
        stats.addAll(plugin.customStats().keySet());
        final List<String[]> fields = new ArrayList<String[]>();
        sender.sendMessage(msg().get("recap-server-header", "season", sm.season()));
        for (String stat : stats) {
            List<PlayerStats> top = sm.top(stat, 3);
            if (top.isEmpty()) {
                continue;
            }
            StringBuilder chat = new StringBuilder();
            StringBuilder disc = new StringBuilder();
            for (int i = 0; i < top.size(); i++) {
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String value = format(stat, top.get(i).get(stat));
                chat.append(i == 0 ? "" : "&7, ").append("&f").append(top.get(i).name).append(" &7(").append(value).append(")");
                disc.append(medal).append(' ').append(top.get(i).name).append(" - ").append(value).append('\n');
            }
            sender.sendMessage(msg().get("recap-server-line", "stat", statName(stat), "list", Messages.color(chat.toString())));
            fields.add(new String[]{statName(stat), disc.toString()});
        }
        final DiscordWebhook hook = plugin.webhook();
        if (!hook.configured()) {
            return;
        }
        final String title = msg().raw("card.header") + " - " + sm.season();
        final String desc = msg().raw("discord-recap").replace("{server}",
                plugin.settings().getString("server-name", "My HCF")).replace("{players}", String.valueOf(sm.all().size()));
        final int color = plugin.accentInt();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    hook.sendEmbed(title, desc, color, fields.toArray(new String[0][]));
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not post the recap to Discord: " + e.getMessage());
                }
            }
        });
    }

    // ---------- autocompletar ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        boolean admin = admin(sender);
        if (args.length == 1) {
            options.addAll(Arrays.asList("top", "share", "chat", "help"));
            if (admin) {
                options.addAll(Arrays.asList("season", "add", "recap", "reload"));
            }
            if (sender.hasPermission("hcfwrapped.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            options.addAll(BASE_STATS);
            options.addAll(plugin.customStats().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("season") && admin) {
            options.addAll(Arrays.asList("start", "end", "status"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add") && admin) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add") && admin) {
            options.addAll(plugin.customStats().keySet());
        }
        String typed = args[args.length - 1].toLowerCase();
        List<String> out = new ArrayList<String>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(typed)) {
                out.add(o);
            }
        }
        return out;
    }
}
