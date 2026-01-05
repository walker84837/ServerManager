package org.winlogon.servermanager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.winlogon.servermanager.PluginCommand;
import org.winlogon.servermanager.ServerManagerPlugin;

import java.util.Map;

public class ProcessRunnerCommand implements PluginCommand {
    private final ServerManagerPlugin plugin;
    public ProcessRunnerCommand(ServerManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        // TODO: shorten .suggests and .executes chain using interface methods
        return Commands.literal("process")
                .requires(this::hasPermission)
                .then(Commands.literal("start")
                    .then(Commands.argument("program", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            plugin.getServiceConfigs().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var programName = context.getArgument("program", String.class);
                            var source = context.getSource();
                        
                            plugin.getProcessesExecutor().submit(() -> {
                                plugin.getProcessManager().startProcess(programName, source.getSender());
                            });
                        
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("stop")
                    .then(Commands.argument("program", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            plugin.getProcessManager().getRunningProcesses().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var programName = context.getArgument("program", String.class);
                            var source = context.getSource();
                        
                            plugin.getProcessManager().stopProcess(programName, source.getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("list")
                    .executes(context -> {
                        var source = context.getSource();
                        plugin.getProcessManager().listProcesses(source);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("reload")
                    // TODO: it doesn't really make sense that an admin can do everything EXCEPT reload the process runner's configuration
                    // This should be removed as it's unnecessary and adds more complexity for no reason.
                    .requires(source -> source.getSender().hasPermission("processrunner.reload"))
                    .executes(context -> {
                        var source = context.getSource();
                        plugin.reloadConfigs(source);
                        return Command.SINGLE_SUCCESS;
                    })
                );
    }

    public Permission permission() {
        var root = permissionNode();
        var reload = root + ".reload";

        var process = new Permission(
            root,
            "Allows access to process management commands",
            PermissionDefault.OP,
            Map.of(reload, true)
        );

        var reloadPerm = new Permission(
            reload,
            "Allows reloading process manager configuration",
            PermissionDefault.OP
        );

        // Explicitly link child -> parent (important for registration order safety)
        reloadPerm.addParent(process, true);

        return process;
    }

    public String permissionNode() {
        return "servermanager.command.process";
    }
}
