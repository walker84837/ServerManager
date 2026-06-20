package org.winlogon.servermanager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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

        this.commandExecutor = plugin.getProcessesExecutor();
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("terminal")
            .requires(this::hasPermission)
            .then(Commands.argument("command", StringArgumentType.greedyString())
                .executes(this::executeTerminalCommand)
            );
    }

    private int executeTerminalCommand(CommandContext<CommandSourceStack> context) {
        String command = StringArgumentType.getString(context, "command");
        var sender = context.getSource().getSender();

        sender.sendRichMessage(
            "<primary>Executing command: <cmd></primary>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("cmd", command)
        );

        CompletableFuture.runAsync(() -> {
            try {
                Process process = createShellProcess(command).start();
                String output = readProcessOutput(process.getInputStream());
                int exitCode = process.waitFor();
                output = plugin.handleCommandOutput(output, sender);

                if (exitCode == 0) {
                    sender.sendRichMessage("<success>Command executed successfully. Output:</success>", plugin.getMessageTheme().getPaletteResolver());
                    sender.sendRichMessage(
                        "<foreground><out></foreground>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("out", output)
                    );
                } else {
                    sender.sendRichMessage(
                        "<failure>Command failed with exit code <exit-code>. Output:</failure>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("exit-code", String.valueOf(exitCode))
                    );
                    sender.sendRichMessage(
                        "<failure><out></failure>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("out", output)
                    );
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<failure>Error executing command: <error></failure>",
                    plugin.getMessageTheme().getPaletteResolver(),
                    Placeholder.unparsed("error", e.getMessage())
                );
                logger.log(Level.SEVERE, "Error executing terminal command", e);
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private ProcessBuilder createShellProcess(String command) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] shellCmd = isWindows
                ? new String[]{"cmd", "/c", command}
                : new String[]{"/bin/sh", "-c", command};
        return new ProcessBuilder(shellCmd).redirectErrorStream(true);
    }

    private String readProcessOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
