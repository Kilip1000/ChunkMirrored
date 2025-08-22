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
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Only players can use this command.");
            return true;
        }
        Player sender  = (Player) commandSender;
        Player player2 = sender;


        if (args.length < 1) {
            if(sender.hasPermission("ChunkMirrored.admin_tpc"))
            {
                sender.sendMessage("Or, for admins: /tpc <player> <player>");
                return true;
            }
            sender.sendMessage("Usage: /tpc <player>");
            return true;
        }

        if(args.length>1)
        {
            if(sender.hasPermission("ChunkMirrored.admin_tpc"))
            {
                Player override = Bukkit.getPlayer(args[1]);
                if (override == null) {
                    sender.sendMessage("That player is not online!");
                    return true;
                }
                player2 = override;
            }
        }

        Player player1 = Bukkit.getPlayer(args[0]);
        if (player1 == null) {
            sender.sendMessage("Player is not online!");
            return true;
        }


        double relX = player2.getX() - player2.getChunk().getX() * 16;
        double relZ = player2.getZ() - player2.getChunk().getZ() * 16;

        int chunkX = player1.getChunk().getX() * 16;
        int chunkZ = player1.getChunk().getZ() * 16;

        Location targetLoc = new Location(player1.getWorld(), chunkX+relX, player2.getY(), chunkZ+relZ, player2.getYaw(), player2.getPitch());
        //teleports player2 to player1's chunk
        player2.teleport(targetLoc);
        if (!sender.equals(player2) && !sender.equals(player1)) {
            sender.sendMessage("Teleported " + player2.getName() + " to " + player1.getName() + "'s chunk.");
            return true;
        }
        if (sender.equals(player2)) {
            sender.sendMessage("Teleported to " + player1.getName() + "'s chunk.");
            return true;
        }
        if (sender.equals(player1)) {
            sender.sendMessage("Teleported " + player2.getName() + "' to your chunk.");
            return true;
        }
        return true;

    }
}
