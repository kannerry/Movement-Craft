package kanner.movementcraft;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public final class FallDamageProtection extends JavaPlugin implements Listener {

    FileConfiguration config = getConfig();
    Map<Player, BukkitTask> protectionTimers = new HashMap<Player, BukkitTask>();

    @Override
    public void onEnable() {
        super.onEnable();

        config.addDefault("EnableFallDamageProtection", true);
        config.options().copyDefaults(true);
        saveConfig();

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private void addProtection(Player player){
        if(!player.getScoreboardTags().contains("hasProtection") && player.getScoreboardTags().contains("canBeProtected")){
            player.addScoreboardTag("hasProtection");
            player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1f);
            player.sendMessage(ChatColor.GRAY + "[fall damage protection]" + ChatColor.GOLD +" » " + ChatColor.GREEN + "granted");
        }
    }

    private void fdParticle(Player player){
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(255, 160, 0), 1.0F);
        for (int i = -5; i < 6; i++) {
            float iNormal = i * 0.1f;
            Location baseLoc = player.getLocation();
            baseLoc.setX(baseLoc.getX() + iNormal);
            baseLoc.setY(baseLoc.getY() + Math.abs(iNormal));
            player.spawnParticle(Particle.REDSTONE, baseLoc, 10, dustOptions);
        }
        for (int i = -5; i < 6; i++) {
            float iNormal = i * 0.1f;
            Location baseLoc = player.getLocation();
            baseLoc.setZ(baseLoc.getZ() + iNormal);
            baseLoc.setY(baseLoc.getY() + Math.abs(iNormal));
            player.spawnParticle(Particle.REDSTONE, baseLoc, 10, dustOptions);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        BukkitTask protectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                addProtection(player);
                protectionTimers.remove(player);
            }
        }.runTaskLater(this, 6);
        protectionTimers.put(player, protectionTask);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        // the player who leaves, cancel their "fall damage protect timer"
        protectionTimers.get(event.getPlayer()).cancel();
        protectionTimers.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerFallDamage(EntityDamageEvent event){
        if(config.getBoolean("EnableFallDamageProtection")){
            if(event.getEntity() instanceof Player){
                Player player = (Player)event.getEntity();
                if(event.getCause() == EntityDamageEvent.DamageCause.FALL){
                    if(player.getScoreboardTags().contains("hasProtection")){
                        event.setCancelled(true);
                        player.removeScoreboardTag("hasProtection");
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.1f, 0.1f);
                        player.sendMessage(ChatColor.GRAY + "[fall damage protection]" + ChatColor.GOLD +" » " + ChatColor.RED + "broken");
                        fdParticle(player);

                        BukkitTask protectionTask = new BukkitRunnable() {
                            @Override
                            public void run() {
                                addProtection(player);
                            }
                        }.runTaskLater(this, 6);
                        protectionTimers.put(player, protectionTask);

                    }
                }
            }
        }
    }

}
