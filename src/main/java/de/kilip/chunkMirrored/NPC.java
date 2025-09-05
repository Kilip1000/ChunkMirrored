package de.kilip.chunkMirrored;

import net.minecraft.server.level.ServerPlayer;



import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

public class NPC extends ServerPlayer {

    public NPC(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
        this.connection = new DummyConnection(server, this, profile);
    }

    public NPC(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, NPCUtils.dummyClientInformation());
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

    public void setSkin(Player sourcePlayer){
        SkinUtils.copySkin(sourcePlayer, this);
    }


    @Override
    public void setHealth(float health) {
        super.setHealth(getMaxHealth());
    }

}
