package dev.thanly.wrapped;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Cuenta en silencio lo que hace cada jugador mientras la temporada esta activa.
 * Usa prioridad MONITOR: solo mira, no cambia nada, y respeta lo que cancele el core de HCF.
 */
public final class StatTracker implements Listener {

    private final HCFWrapped plugin;

    public StatTracker(HCFWrapped plugin) {
        this.plugin = plugin;
    }

    private boolean on() {
        return plugin.stats().active();
    }

    /** Suma tiempo jugado a todos los conectados, cada minuto. */
    public void startPlaytimeTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!on()) {
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.stats().of(p).playtimeSeconds += 60;
                }
            }
        }, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (on()) {
            plugin.stats().of(e.getPlayer()); // registra el nombre actual
        } else if (plugin.stats().ended() && plugin.settings().getBoolean("remind-on-join", true)) {
            final Player p = e.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline() && plugin.stats().find(p.getUniqueId()) != null) {
                        plugin.messages().send(p, "join-reminder");
                    }
                }
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!on()) {
            return;
        }
        Player victim = e.getEntity();
        PlayerStats v = plugin.stats().of(victim);
        v.deaths++;
        v.streak = 0;
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        PlayerStats k = plugin.stats().of(killer);
        k.kills++;
        k.streak++;
        if (k.streak > k.bestStreak) {
            k.bestStreak = k.streak;
        }
        PlayerStats.add(k.victims, victim.getUniqueId().toString());
        PlayerStats.add(v.killers, killer.getUniqueId().toString());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!on() || !(e.getEntity() instanceof Player)) {
            return;
        }
        Player attacker = playerFrom(e.getDamager());
        if (attacker == null || attacker.equals(e.getEntity())) {
            return;
        }
        double dmg = e.getFinalDamage();
        plugin.stats().of(attacker).damageDealt += dmg;
        plugin.stats().of((Player) e.getEntity()).damageTaken += dmg;
    }

    /** El jugador detras del golpe: directo, o quien disparo la flecha/tiro la pocion. */
    private static Player playerFrom(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!on()) {
            return;
        }
        Projectile proj = e.getEntity();
        if (!(proj.getShooter() instanceof Player)) {
            return;
        }
        PlayerStats p = plugin.stats().of((Player) proj.getShooter());
        if (proj instanceof EnderPearl) {
            p.pearls++;
        } else if (proj instanceof ThrownPotion) {
            p.pots++;
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!on()) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GOLDEN_APPLE) {
            return;
        }
        PlayerStats p = plugin.stats().of(e.getPlayer());
        // En 1.8 la god apple (Notch apple) es GOLDEN_APPLE con dato 1.
        if (item.getDurability() == 1) {
            p.godApples++;
        } else {
            p.gapples++;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        if (on() && e.getBlock().getType() == Material.DIAMOND_ORE) {
            plugin.stats().of(e.getPlayer()).diamonds++;
        }
    }
}
