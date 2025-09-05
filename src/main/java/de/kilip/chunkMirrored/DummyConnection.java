package de.kilip.chunkMirrored;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import com.mojang.authlib.GameProfile;

public class DummyConnection extends ServerGamePacketListenerImpl {

    public DummyConnection(MinecraftServer server, ServerPlayer player, GameProfile gameProfile) {
        super(
                server,
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(gameProfile, false)
        );
    }

    @Override
    public void send(Packet<?> packet) {
        //we dont want to waste ressources on proccessing the NPC
    }
}
