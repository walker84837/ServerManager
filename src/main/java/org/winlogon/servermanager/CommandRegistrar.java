package org.winlogon.servermanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.winlogon.servermanager.commands.SystemCommand;
import org.winlogon.servermanager.commands.TerminalCommand;

public class CommandRegistrar {
    private final ServerManagerPlugin plugin;
    private final TerminalCommand terminalCommand;
    private final SystemCommand systemCommand;

    public CommandRegistrar(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.terminalCommand = new TerminalCommand(plugin);
        this.systemCommand = new SystemCommand(plugin);

        terminalCommand.registerIfNotExists(plugin);
        systemCommand.registerIfNotExists(plugin);
    }

    public void registerCommands() {
        var lifecycleManager = plugin.getLifecycleManager();

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(
                terminalCommand.createCommand().build(),
                "Execute terminal commands"
            );
            commands.registrar().register(
                systemCommand.createCommand().build(),
                "Manage system resources and packages"
            );

            commands.registrar().register(
                Commands.literal("process")
                    .requires(source -> source.getSender().hasPermission("processrunner.admin"))
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
                        .requires(source -> source.getSender().hasPermission("processrunner.reload"))
                        .executes(context -> {
                            var source = context.getSource();
                            plugin.reloadConfigs(source);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .build(),
                "Manage external processes"
            );
        });
    }
}
