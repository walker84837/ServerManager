package org.winlogon.servermanager;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface PluginCommand {
    LiteralArgumentBuilder<CommandSourceStack> createCommand();

    Permission permission();

    String permissionNode();

    default boolean hasPermission(CommandSourceStack source) {
        return source.getSender().hasPermission(permission());
    }

    default boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(permission());
    }

    default void registerIfNotExists(JavaPlugin plugin) {
        var pm = plugin.getServer().getPluginManager();
        if (pm.getPermission(permissionNode()) == null) {
            pm.addPermission(permission());
        }
    }
}
