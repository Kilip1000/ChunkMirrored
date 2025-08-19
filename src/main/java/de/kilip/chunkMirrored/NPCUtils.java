package de.kilip.chunkMirrored;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NPCUtils {

    public static NPC spawnNPC(Location location, String name) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.spawn(location);
        npc.setProtected(true);
        return npc;
    }

    public static void killNPC(NPC npc) {
        npc.destroy();
    }

    public static void setSkin(NPC npc, String skinOwner) {
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setSkinName(skinOwner);
    }

    public static void mirrorPlayer(JavaPlugin plugin, NPC npc, Player player, double offsetX, double offsetY, double offsetZ) {
        setSkin(npc, player.getName());

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (npc.isSpawned() && player.isOnline()) {
                Location playerLoc = player.getLocation();
                if (playerLoc.getChunk().isLoaded()) {
                    Location targetLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);
                    targetLoc.setYaw(playerLoc.getYaw());
                    targetLoc.setPitch(playerLoc.getPitch());
                    npc.getEntity().teleport(targetLoc);
                }
            }
        }, 0L, 1L);
    }
}
