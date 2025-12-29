package org.winlogon.servermanager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.winlogon.servermanager.PluginCommand;
import org.winlogon.servermanager.ServerManagerPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TerminalCommand implements PluginCommand {
    private final ServerManagerPlugin plugin;
    private final ExecutorService commandExecutor;
    private final Logger logger;

    private final String permissionNode = "servermanager.command.terminal";
    private final Permission perm = new Permission(
        permissionNode,
        "Allows execution of terminal commands",
        PermissionDefault.OP
    );

    @Override
    public Permission permission() {
        return perm;
    }

    @Override
    public String permissionNode() {
        return permissionNode;
    }

    public TerminalCommand(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

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

        sender.sendRichMessage(
            "<yellow>Executing command: <cmd></yellow>",
            Placeholder.unparsed("cmd", command)
        );

        CompletableFuture.runAsync(() -> {
            try {
                Process process = new ProcessBuilder(command.split(" ")).redirectErrorStream(true).start();
                String output = readProcessOutput(process.getInputStream());
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    sender.sendRichMessage("<green>Command executed successfully. Output:</green>");
                    sender.sendRichMessage(
                        "<white><out></white>",
                        Placeholder.unparsed("out", output)
                    );
                } else {
                    String errorOutput = readProcessOutput(process.getErrorStream());
                    sender.sendRichMessage(
                        "<red>Command failed with exit code <exit-code>. Error:</red>",
                        Placeholder.unparsed("exit-code", String.valueOf(exitCode))
                    );
                    sender.sendRichMessage(
                        "<red><err></red>",
                        Placeholder.unparsed("err", errorOutput)
                    );
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<red>Error executing command: <error></red>",
                    Placeholder.unparsed("error", e.getMessage())
                );
                logger.log(Level.SEVERE, "Error executing terminal command: " + e.getMessage());
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private String readProcessOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
