package de.kilip.chunkMirrored;

import net.minecraft.server.level.ServerPlayer;



import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.ChatVisiblity;
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
import org.jetbrains.annotations.NotNull;

public class NPC extends ServerPlayer {

    public NPC(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
        this.connection = new DummyConnection(server, this, profile);
    }
    @Override
    public void die(@NotNull DamageSource cause)
    {
    }
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {

        return false;
    }
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return true;
    }
    @Override
    public void setHealth(float health) {
        super.setHealth(getMaxHealth());
    }




}
