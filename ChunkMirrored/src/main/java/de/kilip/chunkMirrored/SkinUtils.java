package de.kilip.chunkMirrored;

import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class SkinUtils {

    private final ChunkMirrored plugin;

    public SkinUtils(ChunkMirrored plugin) {
        this.plugin = plugin;
    }

    /**
     * Copies the skin from one Bukkit Player and sets it on another Player.
     *
     * @param source The player whose skin will be copied.
     * @param target The player whose skin will be changed.
     */
    public void copySkin(Player source, Player target) {
        try {
            // Get source player's profile and textures
            PlayerProfile sourceProfile = source.getPlayerProfile();
            PlayerTextures sourceTextures = sourceProfile.getTextures();

            // Get target player's profile and apply source textures
            PlayerProfile targetProfile = target.getPlayerProfile();
            targetProfile.setTextures(sourceTextures);

            // Apply the updated profile to the target player
            target.setPlayerProfile((com.destroystokyo.paper.profile.PlayerProfile) targetProfile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
