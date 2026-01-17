package org.winlogon.servermanager;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface PluginCommand {
    /**
     * Builds a command (...)
     * @return the built command
     */
    LiteralArgumentBuilder<CommandSourceStack> createCommand();

    /**
     * Returns the command's required permission
     * @return the permission
     */
    Permission permission();

    /**
     * Returns the bare permission string (x.y.z.w) to be used elsewhere
     * @return the permission node
     */
    String permissionNode();

    /**
     * Checks whether the player running the command has enough permissions to run this command
     * @param source The command runner
     * @return whether the player is allowed to run this command
     */
    default boolean hasPermission(CommandSourceStack source) {
        return source.getSender().hasPermission(permission());
    }

    /**
     * Checks whether the player running the command has enough permissions to run this command
     * @param sender The command runner
     * @return whether the player is allowed to run this command
     */
    default boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(permission());
    }

    /**
     * Registers the command's permission with the server if it doesn't exist yet
     * @param plugin The main plugin
     */
    default void registerIfNotExists(Plugin plugin) {
        var pm = plugin.getServer().getPluginManager();

        if (pm.getPermission(permissionNode()) == null) {
            pm.addPermission(permission());
        }
    }
}
