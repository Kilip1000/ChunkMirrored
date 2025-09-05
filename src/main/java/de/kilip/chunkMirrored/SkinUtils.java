package de.kilip.chunkMirrored;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.network.protocol.game.*;
import java.util.*;
import java.util.EnumSet;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

public class SkinUtils {

        public static void copySkin(Player source, ServerPlayer target) {
            try {
                // copy textures from gameprofile A to Serverplayer Bs gameprofile, but only the textures
                GameProfile sourceProfile = ((CraftPlayer) source).getHandle().getGameProfile();
                target.getGameProfile().getProperties().removeAll("textures");
                for (Property prop : sourceProfile.getProperties().get("textures")) {
                    target.getGameProfile().getProperties().put("textures", prop);
                }

                // remove and readd
                for (Player online : Bukkit.getOnlinePlayers()) {
                    ServerPlayer viewer = ((CraftPlayer) online).getHandle();
                    ServerGamePacketListenerImpl conn = viewer.connection;
                    // we remove and then readd the skin, basically
                    // remove and readd to update skin
                    conn.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));


                    conn.send(new ClientboundPlayerInfoUpdatePacket(

                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, target
                    ));


                    conn.send(new ClientboundTeleportEntityPacket(
                            target.getId(),
                            new PositionMoveRotation(target.position(), Vec3.ZERO, target.getYRot(), target.getXRot()),
                            EnumSet.noneOf(Relative.class),
                            target.onGround()
                    ));


                    conn.send(new ClientboundRotateHeadPacket(target, (byte) ((target.getYRot() % 360) * 256 / 360)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



}

