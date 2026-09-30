package dev.thanly.wrapped;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Menus de cofre:
 *  - Resumen: tu cabeza con el titulo, cada estadistica como un item (hover = puesto, promedio, barra),
 *    victima favorita y nemesis con sus cabezas, y botones Top / Compartir / Cerrar.
 *  - Top: las 10 mejores cabezas de una estadistica y una fila para cambiar de estadistica.
 */
public final class WrappedMenu implements Listener {

    /** Guarda que menu es y de quien, para saber que hacer al hacer clic. */
    public static final class Holder implements InventoryHolder {
        final String type;
        final PlayerStats target;
        final String stat;
        final String[] slotAction = new String[54];
        Inventory inv;

        Holder(String type, PlayerStats target, String stat) {
            this.type = type;
            this.target = target;
            this.stat = stat;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Donde va cada estadistica base en el menu de resumen. */
    private static final int[] STAT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] TOP_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private final HCFWrapped plugin;

    public WrappedMenu(HCFWrapped plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    // ---------- resumen ----------

    public void openRecap(Player viewer, PlayerStats p) {
        StatsManager sm = plugin.stats();
        Holder h = new Holder("recap", p, null);
        Inventory inv = Bukkit.createInventory(h, 54, title(m().get("menu.title", "player", p.name)));
        h.inv = inv;
        frame(inv);

        // Cabeza del jugador con su titulo
        String titleKey = Titles.of(plugin, p);
        double kdr = p.deaths == 0 ? p.kills : (double) p.kills / p.deaths;
        ItemStack head = head(p.name, m().get("menu.head-name", "player", p.name), Arrays.asList(
                m().get("menu.head-title", "title", m().get(titleKey)),
                m().get("menu.head-season", "season", sm.season()),
                "",
                m().get("menu.head-kd", "kills", Messages.num(p.kills), "deaths", Messages.num(p.deaths),
                        "kdr", String.format("%.2f", kdr))));
        inv.setItem(4, head);

        // Estadisticas
        List<String> keys = StatView.keys(plugin);
        for (int i = 0; i < keys.size() && i < STAT_SLOTS.length; i++) {
            String key = keys.get(i);
            StatView v = StatView.of(plugin, key);
            List<String> lore = new ArrayList<String>();
            lore.add("");
            lore.addAll(StatView.details(plugin, p, key));
            ItemStack it = item(v.icon, v.data, Messages.color(v.chat + "&l" + v.name), lore);
            int rank = sm.rank(p, key);
            if (rank >= 1 && rank <= 3 && v.higherIsBetter) {
                glow(it);
            }
            inv.setItem(STAT_SLOTS[i], it);
        }

        // Victima favorita y nemesis
        Map.Entry<String, Integer> vic = PlayerStats.top(p.victims);
        Map.Entry<String, Integer> nem = PlayerStats.top(p.killers);
        inv.setItem(39, duo("menu.victim", vic == null ? null : sm.nameOf(vic.getKey()), vic == null ? 0 : vic.getValue()));
        inv.setItem(41, duo("menu.nemesis", nem == null ? null : sm.nameOf(nem.getKey()), nem == null ? 0 : nem.getValue()));

        // Botones
        button(h, 48, Material.GOLD_INGOT, 0, "menu.btn-top", "menu.btn-top-lore", "top");
        button(h, 49, Material.BARRIER, 0, "menu.btn-close", null, "close");
        if (viewer.getUniqueId().equals(p.id) || viewer.getName().equalsIgnoreCase(p.name)) {
            button(h, 50, Material.EMPTY_MAP, 0, "menu.btn-share", "menu.btn-share-lore", "share");
        }
        viewer.openInventory(inv);
    }

    private ItemStack duo(String key, String name, int count) {
        if (name == null) {
            return item(Material.SKULL_ITEM, 0, m().get(key), Arrays.asList(m().get("menu.nobody")));
        }
        return head(name, m().get(key), Arrays.asList(m().get("menu.duo-line", "player", name, "count", count)));
    }

    // ---------- top ----------

    public void openTop(Player viewer, String stat) {
        if (!StatView.keys(plugin).contains(stat)) {
            stat = "kills";
        }
        StatsManager sm = plugin.stats();
        StatView view = StatView.of(plugin, stat);
        Holder h = new Holder("top", null, stat);
        Inventory inv = Bukkit.createInventory(h, 54, title(m().get("menu.top-title", "stat", view.name)));
        h.inv = inv;
        frame(inv);

        List<PlayerStats> top = sm.top(stat, 10);
        for (int i = 0; i < TOP_SLOTS.length; i++) {
            if (i >= top.size()) {
                inv.setItem(TOP_SLOTS[i], item(Material.STAINED_GLASS_PANE, 7, m().get("menu.top-empty", "rank", i + 1), null));
                continue;
            }
            PlayerStats p = top.get(i);
            String medal = i == 0 ? "&6" : i == 1 ? "&f" : i == 2 ? "&c" : "&e";
            ItemStack it = head(p.name, Messages.color(medal + "&l#" + (i + 1) + " &f" + p.name),
                    Arrays.asList(m().get("menu.top-value", "stat", view.name, "value", StatView.format(p, stat))));
            it.setAmount(i + 1);
            inv.setItem(TOP_SLOTS[i], it);
            h.slotAction[TOP_SLOTS[i]] = "player:" + p.name;
        }

        // Tu puesto, abajo en el centro
        PlayerStats me = sm.mine(viewer);
        if (me != null) {
            int rank = sm.rank(me, stat);
            inv.setItem(31, head(me.name, m().get("menu.your-rank-name"), Arrays.asList(
                    rank > 0 ? m().get("menu.your-rank", "rank", rank, "total", sm.all().size())
                            : m().get("detail.no-rank"),
                    m().get("menu.top-value", "stat", view.name, "value", StatView.format(me, stat)))));
        }

        // Fila de abajo: elegir la estadistica
        List<String> keys = StatView.keys(plugin);
        int slot = 36;
        for (String key : keys) {
            if (slot > 44 || "deaths".equals(key)) {
                continue;
            }
            StatView v = StatView.of(plugin, key);
            ItemStack it = item(v.icon, v.data, Messages.color(v.chat + "&l" + v.name),
                    Arrays.asList(m().get(key.equals(stat) ? "menu.selected" : "menu.click-to-see")));
            if (key.equals(stat)) {
                glow(it);
            }
            inv.setItem(slot, it);
            h.slotAction[slot] = "stat:" + key;
            slot++;
        }
        button(h, 49, Material.ARROW, 0, "menu.btn-back", null, "back");
        viewer.openInventory(inv);
    }

    // ---------- clics ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getRawSlot() < 0 || e.getRawSlot() >= 54) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();
        Holder h = (Holder) e.getInventory().getHolder();
        String action = h.slotAction[e.getRawSlot()];
        if (action == null) {
            return;
        }
        click(p);
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.equals("top")) {
            openTop(p, "kills");
        } else if (action.equals("back")) {
            PlayerStats me = plugin.stats().mine(p);
            if (me != null) {
                openRecap(p, me);
            } else {
                p.closeInventory();
            }
        } else if (action.equals("share")) {
            p.closeInventory();
            // En el siguiente tick, para no ejecutar comandos dentro del evento del inventario.
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    p.performCommand("wrapped share");
                }
            });
        } else if (action.startsWith("stat:")) {
            openTop(p, action.substring(5));
        } else if (action.startsWith("player:") && p.hasPermission("hcfwrapped.others")) {
            PlayerStats other = plugin.stats().findByName(action.substring(7));
            if (other != null) {
                openRecap(p, other);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ---------- ayudas ----------

    private void click(Player p) {
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.CLICK, 0.6f, 1.4f);
        } catch (Throwable ignored) {
            // Por si el sonido no existe en otra version
        }
    }

    /** En 1.8 el titulo de un inventario no puede pasar de 32 caracteres. */
    private static String title(String t) {
        return t.length() > 32 ? t.substring(0, 32) : t;
    }

    private void frame(Inventory inv) {
        ItemStack dark = item(Material.STAINED_GLASS_PANE, 15, " ", null);
        ItemStack gold = item(Material.STAINED_GLASS_PANE, 1, " ", null);
        for (int i = 0; i < 54; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inv.setItem(i, (row == 0 || row == 5) && (col == 0 || col == 8) ? gold : dark);
            }
        }
    }

    private void button(Holder h, int slot, Material mat, int data, String nameKey, String loreKey, String action) {
        List<String> lore = loreKey == null ? null : Arrays.asList(m().get(loreKey));
        h.inv.setItem(slot, item(mat, data, m().get(nameKey), lore));
        h.slotAction[slot] = action;
    }

    static ItemStack item(Material mat, int data, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    static ItemStack head(String owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwner(owner);
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Brillo de encantamiento sin mostrar el encantamiento. */
    static void glow(ItemStack it) {
        it.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        ItemMeta meta = it.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
    }
}
