package de.kilip.chunkMirrored;

import net.minecraft.world.level.GameType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import net.minecraft.network.protocol.game.*;

import java.util.*;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

import java.util.EnumSet;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChunkMirrored extends JavaPlugin implements Listener {

    private static class NPCInfo {
        public final ServerPlayer npc;
        public double offsetX, offsetZ;
        public Location lastPlayerLoc = null;

        public NPCInfo(ServerPlayer npc, double offsetX, double offsetZ) {
            this.npc = npc;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }

    private final Map<UUID, List<NPCInfo>> playerMirrors = new HashMap<>();
    private final Map<BlockVector, BlockData> globalMask = new HashMap<>();
    private final Queue<Runnable> blockUpdateQueue = new ArrayDeque<>();

    private File maskFile;
    private FileConfiguration maskConfig;
    private NPCHandler npcHandler;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("tpc").setExecutor(new Tpc());
        npcHandler = new NPCHandler(this);

        loadMask();

        // Process BlockUpdates
        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (!blockUpdateQueue.isEmpty() && processed++ < 50) {
                    blockUpdateQueue.poll().run();
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        // Process NPCUpdates
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateNPCsForPlayer(player);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @Override
    public void onDisable() {
        saveMask();
        for (List<NPCInfo> mirrors : playerMirrors.values()) {
            for (NPCInfo info : mirrors) {
                npcHandler.removeNPC(info.npc);
            }
        }
        playerMirrors.clear();
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event){
        Player player = event.getPlayer();
        List<NPCInfo> mirrors = playerMirrors.get(player.getUniqueId());
        if (mirrors == null) return;

        GameType gamemode = (event.getNewGameMode()==GameMode.SPECTATOR) ? GameType.SPECTATOR :  GameType.CREATIVE;

        for (NPCInfo info : mirrors) {
            info.npc.setGameMode(gamemode);
        }

    }
    // --------------------------
    // Block masking
    // --------------------------

    private void loadMask() {
        maskFile = new File(getDataFolder(), "changedBlocks.yml");
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
                BlockData data = Bukkit.createBlockData(
                        maskConfig.getString("blocks." + key + ".data"));
                globalMask.put(new BlockVector(x, y, z), data);
            } catch (Exception ignored) {}
        }
    }

    private void saveMask() {
        if (maskConfig == null) maskConfig = new YamlConfiguration();
        maskConfig.set("blocks", null);
        for (Map.Entry<BlockVector, BlockData> entry : globalMask.entrySet()) {
            BlockVector vec = entry.getKey();
            String key = vec.getBlockX() + "," + vec.getBlockY() + "," + vec.getBlockZ();
            maskConfig.set("blocks." + key + ".data", entry.getValue().getAsString());
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
            if (chunk.getX() == (source.getX() >> 4) && chunk.getZ() == (source.getZ() >> 4))
                continue;

            int worldX = (chunk.getX() << 4) + relPos.getBlockX();
            int worldY = relPos.getBlockY();
            int worldZ = (chunk.getZ() << 4) + relPos.getBlockZ();

            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight())
                continue;

            Block targetBlock = world.getBlockAt(worldX, worldY, worldZ);
            targetBlock.setBlockData(data, false);
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

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(true);
        BlockFace dir = event.getDirection();
        List<Block> blocks = new ArrayList<>(event.getBlocks());

        blocks.sort(Comparator.comparingDouble(
                b -> -b.getLocation().distanceSquared(event.getBlock().getLocation())));

        for (Block block : blocks) {
            queueBlockUpdate(block.getRelative(dir), block.getBlockData());
            if (block.getRelative(dir.getOppositeFace()).getType().isAir())
                queueBlockUpdate(block, Bukkit.createBlockData(Material.AIR));
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;
        event.setCancelled(true);

        BlockFace dir = event.getDirection();
        List<Block> blocks = new ArrayList<>(event.getBlocks());

        blocks.sort(Comparator.comparingDouble(
                b -> b.getLocation().distanceSquared(event.getBlock().getLocation())));

        for (Block block : blocks) {
            queueBlockUpdate(block.getRelative(dir.getOppositeFace()), block.getBlockData());
            queueBlockUpdate(block, Bukkit.createBlockData(Material.AIR));
        }
    }

    // --------------------------
    // Player + NPC system
    // --------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        spawnFullMirrorGrid(event.getPlayer());
        npcHandler.sendPacketsForAllNPCs();
    }

    private void spawnFullMirrorGrid(Player player) {
        int viewDistance = player.getViewDistance();
        List<NPCInfo> mirrors = new ArrayList<>();

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                if (dx == 0 && dz == 0) continue;

                double offsetX = dx * 16;
                double offsetZ = dz * 16;

                Location npcLoc = player.getLocation().clone().add(offsetX, 0, offsetZ);
                ServerPlayer npc = npcHandler.createServerPlayerNPC(
                        player.getName(),
                        npcLoc.getX(), npcLoc.getY(), npcLoc.getZ(),
                        npcLoc.getYaw(), npcLoc.getPitch()
                );

                npcHandler.queueSkinUpdate(npc, player);
                npcHandler.setHelmet(npc, player.getInventory().getHelmet());
                npcHandler.setChest(npc, player.getInventory().getChestplate());
                npcHandler.setLegs(npc, player.getInventory().getLeggings());
                npcHandler.setFeet(npc, player.getInventory().getBoots());
                npcHandler.setMainhand(npc, player.getInventory().getItemInMainHand());
                npcHandler.setOffhand(npc, player.getInventory().getItemInOffHand());

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

            if (offsetX > viewDistance * 16) offsetX -= wrapSize;
            if (offsetX < -viewDistance * 16) offsetX += wrapSize;
            if (offsetZ > viewDistance * 16) offsetZ -= wrapSize;
            if (offsetZ < -viewDistance * 16) offsetZ += wrapSize;

            info.offsetX = offsetX;
            info.offsetZ = offsetZ;

            Vec3 npcPos = new Vec3(
                    playerLoc.getX() + offsetX,
                    playerLoc.getY(),
                    playerLoc.getZ() + offsetZ
            );

            info.npc.setPos(npcPos.x, npcPos.y, npcPos.z);
            info.npc.setYRot(playerLoc.getYaw());
            info.npc.setXRot(playerLoc.getPitch());

            npcHandler.setHelmet(info.npc, player.getInventory().getHelmet());
            npcHandler.setChest(info.npc, player.getInventory().getChestplate());
            npcHandler.setLegs(info.npc, player.getInventory().getLeggings());
            npcHandler.setFeet(info.npc, player.getInventory().getBoots());
            npcHandler.setMainhand(info.npc, player.getInventory().getItemInMainHand());
            npcHandler.setOffhand(info.npc, player.getInventory().getItemInOffHand());
            npcHandler.queueSkinUpdate(info.npc, player);

            info.lastPlayerLoc = playerLoc.clone();
        }
    }



    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        List<NPCInfo> mirrors = playerMirrors.remove(event.getPlayer().getUniqueId());
        if (mirrors == null) return;

        for (NPCInfo info : mirrors) {
            npcHandler.removeNPC(info.npc);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition() && !event.hasChangedOrientation()) return;
        updateNPCsForPlayer(event.getPlayer());
    }
}
