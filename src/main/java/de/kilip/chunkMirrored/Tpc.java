package de.kilip.chunkMirrored;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Tpc implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player commandPlayer = (Player) sender;

        // Command requires at least one argument
        if (args.length < 1) {
            if (commandPlayer.hasPermission("ChunkMirrored.admin_tpc")) {
                sender.sendMessage("Usage: /tpc <player> [targetPlayer]");
            } else {
                sender.sendMessage("Usage: /tpc <player>");
            }
            return true;
        }

        Player player1;
        Player player2;

        // If there are two arguments and sender has permission, use admin teleport
        if (args.length > 1 && commandPlayer.hasPermission("ChunkMirrored.admin_tpc")) {
            player1 = Bukkit.getPlayer(args[0]);
            player2 = Bukkit.getPlayer(args[1]);

            if (player1 == null || player2 == null) {
                sender.sendMessage("One of the specified players is not online!");
                return true;
            }
        } else {
            // Otherwise, player1 is sender, player2 is args[0]
            player1 = commandPlayer;
            player2 = Bukkit.getPlayer(args[0]);

            if (player2 == null) {
                sender.sendMessage("That player is not online!");
                return true;
            }
        }

        // Calculate relative position in chunk
        double relX = player2.getX() - player2.getChunk().getX() * 16;
        double relZ = player2.getZ() - player2.getChunk().getZ() * 16;

        int chunkX = player1.getChunk().getX() * 16;
        int chunkZ = player1.getChunk().getZ() * 16;

        Location targetLoc = new Location(player1.getWorld(), chunkX + relX, player2.getY(), chunkZ + relZ, player2.getYaw(), player2.getPitch());

        // Teleport player1 to player2's chunk
        player1.teleport(targetLoc);

        // Send messages
        if (sender.equals(player1) && !sender.equals(player2)) {
            sender.sendMessage("Teleported to " + player2.getName() + "'s chunk.");
            return true;
        }
        if (sender.equals(player2) && !sender.equals(player1)) {
            sender.sendMessage(player1.getName() + " has been teleported to your chunk.");
            return true;
        }
        if (!sender.equals(player1) && !sender.equals(player2)) {
            sender.sendMessage("Teleported " + player1.getName() + " to " + player2.getName() + "'s chunk.");
            player1.sendMessage("You have been teleported to " + player2.getName() + "'s chunk.");
            return true;
        }
        sender.sendMessage("Teleported " + player1.getName() + " to " + player2.getName() + "'s chunk.");

        return true;
    }
}
