package de.kilip.chunkMirrored;

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
import java.util.EnumSet;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

public class NPCHandler {

    private static final List<ServerPlayer> NPC_LIST = new ArrayList<>();
    private final JavaPlugin plugin;
    private final Queue<Pair<ServerPlayer, Player>> pendingSkinUpdates = new ArrayDeque<>();

    public NPCHandler(JavaPlugin plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                sendPacketsForAllNPCs();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                int updatesPerTick = 2;
                int processed = 0;

                while (!pendingSkinUpdates.isEmpty() && processed < updatesPerTick) {
                    Pair<ServerPlayer, Player> pair = pendingSkinUpdates.poll();
                    updateNPCSkin(pair.getFirst(), pair.getSecond());
                    processed++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void queueSkinUpdate(ServerPlayer npc, Player player) {
        if (npc == null || player == null) return;
        pendingSkinUpdates.add(new Pair<>(npc, player));
    }

    /** Copies a Bukkit player's skin to the NPC using reflection-free Paper API */
    private void updateNPCSkin(ServerPlayer npc, Player player) {
        if (npc == null || player == null) return;

        GameProfile playerProfile = ((CraftPlayer) player).getHandle().getGameProfile();
        npc.getGameProfile().getProperties().removeAll("textures");

        for (Property property : playerProfile.getProperties().get("textures")) {
            npc.getGameProfile().getProperties().put("textures", property);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            ServerPlayer viewer = ((CraftPlayer) online).getHandle();
            ServerGamePacketListenerImpl connection = viewer.connection;

            connection.send(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    npc
            ));

            connection.send(new ClientboundTeleportEntityPacket(
                    npc.getId(),
                    new PositionMoveRotation(npc.position(), Vec3.ZERO, npc.getYRot(), npc.getXRot()),
                    EnumSet.noneOf(Relative.class),
                    npc.onGround()
            ));

            byte yawByte = (byte) ((npc.getYRot() % 360) * 256 / 360);
            connection.send(new ClientboundRotateHeadPacket(npc, yawByte));
        }
    }

    public ServerPlayer createServerPlayerNPC(String name, double x, double y, double z, float yRot, float xRot) {
        MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel serverLevel = minecraftServer.getLevel(Level.OVERWORLD);
        if (serverLevel == null) throw new IllegalStateException("Overworld not loaded!");

        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), name);
        ClientInformation clientInfo = new ClientInformation(
                "en_us",
                10,
                ChatVisiblity.FULL,
                true,
                0,
                HumanoidArm.RIGHT,
                false,
                true,
                ParticleStatus.ALL
        );

        ServerPlayer npc = new NPC(minecraftServer, serverLevel, gameProfile, clientInfo);
        npc.connection = new DummyConnection(minecraftServer, npc, gameProfile);
        npc.setOldPosAndRot(new Vec3(x, y, z), yRot, xRot);

        serverLevel.addNewPlayer(npc);
        NPC_LIST.add(npc);
        addNPCPacket(npc);

        return npc;
    }

    public void spawnNPC(String name, double x, double y, double z, float yRot, float xRot) {
        createServerPlayerNPC(name, x, y, z, yRot, xRot);
    }

    public void removeNPC(ServerPlayer npc) {
        if (npc == null) return;

        NPC_LIST.remove(npc);

        for (Player online : Bukkit.getOnlinePlayers()) {
            ServerPlayer viewer = ((CraftPlayer) online).getHandle();
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(npc.getId()));
        }

        npc.level().removePlayerImmediately(npc, ServerPlayer.RemovalReason.DISCARDED);
        npc.connection = null;
    }

    public void sendPacketsForAllNPCs() {
        for (ServerPlayer npc : NPC_LIST) {
            byte yawByte = (byte) ((npc.getYRot() % 360) * 256 / 360);

            for (Player online : Bukkit.getOnlinePlayers()) {
                ServerPlayer viewer = ((CraftPlayer) online).getHandle();
                ServerGamePacketListenerImpl connection = viewer.connection;

                connection.send(new ClientboundTeleportEntityPacket(
                        npc.getId(),
                        new PositionMoveRotation(npc.position(), Vec3.ZERO, npc.getYRot(), npc.getXRot()),
                        EnumSet.noneOf(Relative.class),
                        npc.onGround()
                ));

                connection.send(new ClientboundRotateHeadPacket(npc, yawByte));
            }
        }
    }

    // --- Equipment API ---
    public void setItem(ServerPlayer npc, EquipmentSlot slot, ItemStack itemStack) {
        npc.setItemSlot(slot, net.minecraft.world.item.ItemStack.fromBukkitCopy(itemStack));
    }

    public ItemStack getItem(ServerPlayer npc, EquipmentSlot slot) {
        return CraftItemStack.asBukkitCopy(npc.getItemBySlot(slot));
    }

    public void setHelmet(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.HEAD, item); }
    public void setChest(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.CHEST, item); }
    public void setLegs(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.LEGS, item); }
    public void setFeet(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.FEET, item); }
    public void setMainhand(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.MAINHAND, item); }
    public void setOffhand(ServerPlayer npc, ItemStack item) { setItem(npc, EquipmentSlot.OFFHAND, item); }

    public ItemStack getHelmetBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.HEAD); }
    public ItemStack getChestBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.CHEST); }
    public ItemStack getLegsBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.LEGS); }
    public ItemStack getFeetBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.FEET); }
    public ItemStack getMainHandBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.MAINHAND); }
    public ItemStack getOffHandBukkit(ServerPlayer npc) { return getItem(npc, EquipmentSlot.OFFHAND); }

    public void addNPCPacket(ServerPlayer npc) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            ServerPlayer viewer = ((CraftPlayer) online).getHandle();
            ServerGamePacketListenerImpl connection = viewer.connection;

            if (viewer == npc) continue;

            byte yawByte = (byte) ((npc.getYRot() % 360) * 256 / 360);
            byte pitchByte = (byte) ((npc.getXRot() % 360) * 256 / 360);

            connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc));
            connection.send(new ClientboundRotateHeadPacket(npc, yawByte));

            List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                equipment.add(new Pair<>(slot, npc.getItemBySlot(slot)));
            }
            connection.send(new ClientboundSetEquipmentPacket(npc.getId(), equipment));

            connection.send(new ClientboundTeleportEntityPacket(
                    npc.getId(),
                    new PositionMoveRotation(npc.position(), Vec3.ZERO, npc.getYRot(), npc.getXRot()),
                    EnumSet.noneOf(Relative.class),
                    npc.onGround()
            ));

            connection.send(new ClientboundMoveEntityPacket.Rot(npc.getId(), yawByte, pitchByte, npc.onGround()));
        }
    }
}
