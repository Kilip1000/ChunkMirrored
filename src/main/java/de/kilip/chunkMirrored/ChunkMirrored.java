package de.kilip.chunkMirrored;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChunkMirrored extends JavaPlugin implements Listener {

    private static class NPCInfo {
        public final NPC npc;
        public double offsetX, offsetZ;
        public Location lastPlayerLoc = null;

        public NPCInfo(NPC npc, double offsetX, double offsetZ) {
            this.npc = npc;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }

    // Each player gets a fixed torus grid of mirrors
    private final Map<UUID, List<NPCInfo>> playerMirrors = new HashMap<>();

    // Block mask system
    private final Map<BlockVector, BlockData> globalMask = new HashMap<>();
    private final Queue<Runnable> blockUpdateQueue = new ArrayDeque<>();
    private File maskFile;
    private FileConfiguration maskConfig;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("tpc").setExecutor(new Tpc());
        getLogger().info("ChunkMirrorPlugin enabled!");

        maskFile = new File(getDataFolder(), "changedBlocks.yml");
        if (!maskFile.exists()) {
            try {
                if (!getDataFolder().exists()) getDataFolder().mkdirs();
                maskFile.createNewFile();
            } catch (Exception e) {
                getLogger().severe("Could not create changedBlocks.yml: " + e.getMessage());
            }
        }

        loadMask();

        // Block update processor
        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (!blockUpdateQueue.isEmpty() && processed++ < 50) {
                    blockUpdateQueue.poll().run();
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    //Update torus-mirrored NPCs
                    updateNPCsForPlayer(player);

                    //Lock Y-position if in Creative or Spectator
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        lockNPCsForPlayer(player);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L); // runs every 2 ticks


    }

    @Override
    public void onDisable() {
        saveMask();
        for (List<NPCInfo> mirrors : playerMirrors.values()) {
            for (NPCInfo info : mirrors) {
                info.npc.destroy();
            }
        }
        playerMirrors.clear();
        getLogger().info("ChunkMirrorPlugin disabled and mask saved.");
    }

    // --------------------------
    // Block masking
    // --------------------------

    private void loadMask() {
        maskConfig = YamlConfiguration.loadConfiguration(maskFile);
        globalMask.clear();
        if (!maskConfig.contains("blocks")) return;

        for (String key : maskConfig.getConfigurationSection("blocks").getKeys(false)) {
            String[] parts = key.split(",");
            if (parts.length != 3) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                BlockData data = Bukkit.createBlockData(maskConfig.getString("blocks." + key + ".data"));
                globalMask.put(new BlockVector(x, y, z), data);
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        if (globalMask.isEmpty()) return;

        for (Map.Entry<BlockVector, BlockData> entry : globalMask.entrySet()) {
            BlockVector relPos = entry.getKey();
            BlockData data = entry.getValue();

            int worldX = (chunk.getX() << 4) + relPos.getBlockX();
            int worldY = relPos.getBlockY();
            int worldZ = (chunk.getZ() << 4) + relPos.getBlockZ();

            Block block = world.getBlockAt(worldX, worldY, worldZ);
            block.setBlockData(data, false); // NO physics on chunk load
        }
    }

    private void saveMask() {
        if (maskConfig == null) maskConfig = new YamlConfiguration();
        maskConfig.set("blocks", null);
        for (Map.Entry<BlockVector, BlockData> entry : globalMask.entrySet()) {
            BlockVector vec = entry.getKey();
            BlockData data = entry.getValue();
            String key = vec.getBlockX() + "," + vec.getBlockY() + "," + vec.getBlockZ();
            maskConfig.set("blocks." + key + ".data", data.getAsString());
        }
        try {
            maskConfig.save(maskFile);
        } catch (IOException ignored) {}
    }

    private void queueBlockUpdate(Block block, BlockData data) {
        BlockVector relPos = new BlockVector(block.getX() & 15, block.getY(), block.getZ() & 15);
        globalMask.put(relPos, data);

        blockUpdateQueue.add(() -> updateBlockChangeInternal(block.getWorld(), relPos, data, block));
    }


    private void updateBlockChangeInternal(World world, BlockVector relPos, BlockData data, Block source) {
        for (Chunk chunk : world.getLoadedChunks()) {
            // skip the source chunk
            if (chunk.getX() == (source.getX() >> 4) && chunk.getZ() == (source.getZ() >> 4)) continue;

            int worldX = (chunk.getX() << 4) + relPos.getBlockX();
            int worldY = relPos.getBlockY();
            int worldZ = (chunk.getZ() << 4) + relPos.getBlockZ();

            // safety: check bounds
            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) continue;

            Block targetBlock = world.getBlockAt(worldX, worldY, worldZ);
            targetBlock.setBlockData(data, false); // no physics
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        queueBlockUpdate(event.getBlock(), event.getBlock().getBlockData());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        queueBlockUpdate(event.getBlock(), Bukkit.createBlockData(Material.AIR));
    }

    // --------------------------
    // Player + NPC system
    // --------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("Initialising copies, don't worry, this might take 20-30 seconds");
        spawnFullMirrorGrid(player);
        player.sendMessage("You may now move");
        boolean visible = player.getGameMode() != org.bukkit.GameMode.SPECTATOR;

        // Hide/show all NPCs for this player
        var mirrors = this.playerMirrors.get(player.getUniqueId());
        if (mirrors == null) return;

        for (Player other : Bukkit.getOnlinePlayers()) {
            for (NPCInfo info : mirrors) {
                NPC npc = info.npc;
                if (npc.isSpawned() && npc.getEntity() != null) {
                    if (visible) {
                        other.showEntity(this, npc.getEntity());
                    } else {
                        other.hideEntity(this, npc.getEntity());
                    }
                }
            }
        }


    }
    private void lockNPCsForPlayer(Player player) {
        List<NPCInfo> mirrors = playerMirrors.get(player.getUniqueId());
        if (mirrors == null) return;

        for (NPCInfo info : mirrors) {
            if (!info.npc.isSpawned()) continue;
            Entity e = info.npc.getEntity();
            if (e == null) continue;

            // Keep exact Y-position
            Location loc = e.getLocation();
            loc.setY(info.lastPlayerLoc != null ? info.lastPlayerLoc.getY() : loc.getY());
            e.teleport(loc);
        }
    }


    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        boolean visible = event.getNewGameMode() != org.bukkit.GameMode.SPECTATOR;

        // Hide/show all NPCs for this player
        var mirrors = this.playerMirrors.get(player.getUniqueId());
        if (mirrors == null) return;

        for (Player other : Bukkit.getOnlinePlayers()) {
            for (NPCInfo info : mirrors) {
                NPC npc = info.npc;
                if (npc.isSpawned() && npc.getEntity() != null) {
                    if (visible) {
                        other.showEntity(this, npc.getEntity());
                    } else {
                        other.hideEntity(this, npc.getEntity());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        List<NPCInfo> mirrors = playerMirrors.remove(id);
        if (mirrors != null) {
            for (NPCInfo info : mirrors) {
                if (info.npc != null && info.npc.isSpawned()) info.npc.destroy();
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition() && !event.hasChangedOrientation()) return;
        // Just force update cycle
        updateNPCsForPlayer(event.getPlayer());
    }

    private void spawnFullMirrorGrid(Player player) {
        int viewDistance = player.getViewDistance();
        List<NPCInfo> mirrors = new ArrayList<>();

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                if (dx == 0 && dz == 0) continue; // skip the real player

                double offsetX = dx * 16;
                double offsetZ = dz * 16;

                Location npcLoc = player.getLocation().clone().add(offsetX, 0, offsetZ);

                NPC npc = NPCUtils.spawnNPC(npcLoc, player.getName());
                NPCUtils.setSkin(npc, player.getName());
                npc.spawn(npcLoc);

                if (npc.isSpawned()) {
                    Entity entity = npc.getEntity();
                    if (entity != null) {
                        entity.setGravity(false);
                        entity.setNoPhysics(true);
                    }
                    else{
                        getLogger().warning("NPC is unable to be spawned");
                    }
                }


                mirrors.add(new NPCInfo(npc, offsetX, offsetZ));
            }
        }

        playerMirrors.put(player.getUniqueId(), mirrors);
    }

    private void updateNPCsForPlayer(Player player) {
        List<NPCInfo> mirrors = playerMirrors.get(player.getUniqueId());
        if (mirrors == null) return;

        Location playerLoc = player.getLocation();
        int viewDistance = player.getViewDistance();
        double wrapSize = (2 * viewDistance + 1) * 16;

        for (NPCInfo info : mirrors) {
            double offsetX = info.offsetX;
            double offsetZ = info.offsetZ;

            // Wrap torus offsets
            if (offsetX >  viewDistance * 16) offsetX -= wrapSize;
            if (offsetX < -viewDistance * 16) offsetX += wrapSize;
            if (offsetZ >  viewDistance * 16) offsetZ -= wrapSize;
            if (offsetZ < -viewDistance * 16) offsetZ += wrapSize;

            info.offsetX = offsetX;
            info.offsetZ = offsetZ;

            Location mirrored = playerLoc.clone().add(offsetX, 0, offsetZ);
            mirrored.setYaw(playerLoc.getYaw());
            mirrored.setPitch(playerLoc.getPitch());


            Entity e = info.npc.getEntity();
            if (info.npc.isSpawned()) {
                World world = mirrored.getWorld();
                int chunkX = mirrored.getBlockX() >> 4;
                int chunkZ = mirrored.getBlockZ() >> 4;

                // Asynchronously load chunk, then teleport NPC
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                    Bukkit.getScheduler().runTask(this, () -> {
                        e.teleport(mirrored);
                    });

                });
            }

            info.lastPlayerLoc = playerLoc.clone();
        }
    }


    private void updateAllNPCs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNPCsForPlayer(player);
        }
    }
}
