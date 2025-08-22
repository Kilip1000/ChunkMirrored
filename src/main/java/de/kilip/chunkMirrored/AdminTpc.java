package de.kilip.chunkMirrored;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminTpc implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String alias, String[] args) {

        Player player = (Player) commandSender;

        if (args.length < 1) {
            player.sendMessage("Usage: /tpc <player>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            player.sendMessage("Player is not online!");
            return true;
        }

        double relX = player.getX() - player.getChunk().getX() * 16;
        double relZ = player.getZ() - player.getChunk().getZ() * 16;

        int chunkX = targetPlayer.getChunk().getX() * 16;
        int chunkZ = targetPlayer.getChunk().getZ() * 16;

        Location targetLoc = new Location(targetPlayer.getWorld(), chunkX+relX, player.getY(), chunkZ+relZ, player.getYaw(), player.getPitch());

        player.teleport(targetLoc);
        player.sendMessage("Teleported to " + targetPlayer.getName() + "'s chunk.");
        return true;
    }
}
