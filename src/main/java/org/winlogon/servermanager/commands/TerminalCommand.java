package org.winlogon.servermanager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.winlogon.servermanager.ServerManagerPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class TerminalCommand {

    private final ServerManagerPlugin plugin;
    private final ExecutorService commandExecutor;

    private final String permissionNode = "servermanager.command.terminal";
    private final Permission perm = new Permission(
        permissionNode,
         "Allows execution of terminal commands",
         PermissionDefault.OP
    );

    public TerminalCommand(ServerManagerPlugin plugin) {
        this.plugin = plugin;

        // Using the same executor as process management
        this.commandExecutor = plugin.getProcessesExecutor();
        var pm = Bukkit.getPluginManager();

        // Register permission if it doesn't exist
        if (pm.getPermission(permissionNode) == null) {
            pm.addPermission(perm);
        }
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("terminal")
            .requires(source -> source.getSender().hasPermission(permissionNode))
            .then(Commands.argument("command", StringArgumentType.greedyString())
                .executes(this::executeTerminalCommand)
            );
    }

    private int executeTerminalCommand(CommandContext<CommandSourceStack> context) {
        String command = StringArgumentType.getString(context, "command");
        var sender = context.getSource().getSender();

        sender.sendMessage(Component.text("Executing command: " + command, NamedTextColor.YELLOW));

        CompletableFuture.runAsync(() -> {
            try {
                var process = Runtime.getRuntime().exec(command);

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    sender.sendMessage(Component.text("Command executed successfully. Output:", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(output.toString(), NamedTextColor.WHITE));
                } else {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder errorOutput = new StringBuilder();
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    sender.sendMessage(Component.text("Command failed with exit code " + exitCode + ". Error:", NamedTextColor.RED));
                    sender.sendMessage(Component.text(errorOutput.toString(), NamedTextColor.RED));
                }

            } catch (Exception e) {
                sender.sendMessage(Component.text("Error executing command: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().severe("Error executing terminal command: " + e.getMessage());
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }
}
