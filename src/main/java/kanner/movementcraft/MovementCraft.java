package kanner.movementcraft;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public final class MovementCraft extends JavaPlugin implements Listener {

    FileConfiguration config = getConfig();
    Map<Player, Location> oldLocations = new HashMap<Player, Location>();
    Map<Player, BukkitTask> protectionTimers = new HashMap<Player, BukkitTask>();

    private boolean isSameXYZ(double x1, double y1, double z1, double x2, double y2, double z2){
        if(x1 == x2 && y1 == y2 && z1 == z2){
            return true;
        }
        return false;
    }

    private Location getBlockLocation(Player player){
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        Location playerBlockLocation = new Location(player.getWorld(), x, y, z);

        return playerBlockLocation;
    }

    private void runWallJumpFor(Player player, Location blockToJumpFrom){
        Location playerLoc = getBlockLocation(player);

        double x = (playerLoc.getX() - blockToJumpFrom.getX()) * 0.5f;
        double z = (playerLoc.getZ() - blockToJumpFrom.getZ()) * 0.5f;
        Vector moveInDirection = new Vector(x, 0.5f, z);

        player.setVelocity(moveInDirection);

    }

    private void longJumpFor(Player player){
        // long jump, while moving
        float multi = 0.9f;
        Vector lookDirection = player.getLocation().getDirection();
        Vector moveInDirection = new Vector(lookDirection.getX() * multi, 0.33f, lookDirection.getZ() * multi);
        player.setVelocity(moveInDirection);
    }

    private void backFlipFor(Player player){
        // backflip, not moving
        float multi = 0.5f;
        Vector lookDirection = player.getLocation().getDirection();
        Vector moveInDirection = new Vector(-lookDirection.getX() * multi, 1f, -lookDirection.getZ() * multi);
        player.setVelocity(moveInDirection);
    }

    private Block[] getAdjacentBlocks(Player player){
        // get blocks around player
        Block[] b = {
                player.getLocation().getBlock().getRelative(BlockFace.NORTH),
                player.getLocation().getBlock().getRelative(BlockFace.EAST),
                player.getLocation().getBlock().getRelative(BlockFace.SOUTH),
                player.getLocation().getBlock().getRelative(BlockFace.WEST),
        };
        return b;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        config.addDefault("EnableFallDamageProtection", true);
        config.options().copyDefaults(true);
        saveConfig();

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        // every 5 ticks, check location.
        Player player = event.getPlayer();
        oldLocations.put(player, player.getLocation()); // place a "base variable" down
        new BukkitRunnable() {
            @Override
            public void run(){
                // if our player is connected:
                if(player.isOnline()){
                    // get their old location, and new location.
                    Location ol = oldLocations.get(player);
                    Location nw = player.getLocation();
                    // compare them! if they are the same, then we aren't moving. no change in position
                    if(isSameXYZ(ol.getX(), ol.getY(), ol.getZ(), nw.getX(), nw.getY(), nw.getZ())){
                        player.removeScoreboardTag("isMoving");
                    }else if(!player.getScoreboardTags().contains("isMoving")){ // otherwise, we are moving. add the tag! (only if we don't have it already)
                        player.addScoreboardTag("isMoving");
                    }
                    // now put our current location in the oldLocation variable. next tick, we will be comparing this position!
                    oldLocations.put(player, player.getLocation());
                }else{ // if we are not online, remove the location from the map and cancel our loop
                    oldLocations.remove(player);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1, 1); // every tick
    }

    @EventHandler
    public void onPlayerFly(PlayerToggleFlightEvent event){
        Player player = event.getPlayer();
        if(player.getGameMode().equals(GameMode.SURVIVAL)){
            event.setCancelled(true);

            // get blocks around player
            Block[] cardinalBlocks = getAdjacentBlocks(player);
            for(Block block : cardinalBlocks){
                Location blockLocation = block.getLocation();
                // if the block is solid, and we have tried to fly:
                // essentially "pressing space twice", a double jump if you will
                if(block.getType().isSolid()){
                    // try a double jump on the block we are adjacent to
                    runWallJumpFor(player, blockLocation);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location moveFrom = event.getFrom();
        Location moveTo = event.getTo();
        Player player = event.getPlayer();

        Block b = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        // get the blocks around our player, and set a boolean if there is one!
        Block[] cardinalBlocks = getAdjacentBlocks(player);
        for(Block block : cardinalBlocks){
            // when we move, check if we are sneaking, if we are on the ground and if we aren't in any state.
            if(block.getType().isSolid() && player.isSneaking()
            && !player.getScoreboardTags().contains("isLongJumping")
            && !player.getScoreboardTags().contains("isBackFlipping"))
            {
                    PotionEffect SLOW_FALLING = new PotionEffect(PotionEffectType.SLOW_FALLING, 5, 2);
                    player.addPotionEffect(SLOW_FALLING);
                    break;
            }
        }

        // if a player is jumping,
        if(moveTo.getY() > moveFrom.getY()){
            // in survival, set them to be able to fly!
            if(player.getGameMode().equals(GameMode.SURVIVAL)){
                player.setAllowFlight(true);
            }

            // with a block below them,:
            if(b.getType().isSolid()){
                // and they are sneaking, and they aren't currently long jumping then:
                if(player.isSneaking()
                && !player.getScoreboardTags().contains("isBackFlipping")
                && !player.getScoreboardTags().contains("isLongJumping"))
                {
                    // if we are not moving
                    if(player.getScoreboardTags().contains("isMoving")){
                        // send them in their facing direction and put them into the LongJumping state
                        longJumpFor(player);
                        player.addScoreboardTag("isLongJumping");
                    }else{
                        // send them in their respective and put them into the BackFlipping state
                        backFlipFor(player);
                        player.addScoreboardTag("isBackFlipping");
                    }
                    // remove the states
                    new BukkitRunnable() {
                        @Override
                        public void run(){
                            player.removeScoreboardTag("isBackFlipping");
                            player.removeScoreboardTag("isLongJumping");
                        }
                    }.runTaskLater(this, 10); // after 10 ticks
                }
            }
        }

        // If we are falling, in survival, and the block below us is solid:
        if(player.getGameMode().equals(GameMode.SURVIVAL) && moveTo.getY() < moveFrom.getY() && b.getType().isSolid()){
            player.setAllowFlight(false);
        }

    }

    // Fall Damage Protection
    private void addProtection(Player player){
        if(!player.getScoreboardTags().contains("hasProtection")){
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
    public void onPlayerJoinFDP(PlayerJoinEvent event){
        Player player = event.getPlayer();
        BukkitTask protectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                addProtection(player);
                protectionTimers.remove(player);
            }
        }.runTaskLater(this, 60 * 20);
        protectionTimers.put(player, protectionTask);
    }

    @EventHandler
    public void onPlayerLeaveFDP(PlayerQuitEvent event){
        // the player who leaves, cancel their "fall damage protect timer"
        if(!event.getPlayer().getScoreboardTags().contains("hasProtection")){
            protectionTimers.get(event.getPlayer()).cancel();
            protectionTimers.remove(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerFallDamageFDP(EntityDamageEvent event){
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
                        }.runTaskLater(this, 60 * 20);
                        protectionTimers.put(player, protectionTask);

                    }
                }
            }
        }
    }

}
