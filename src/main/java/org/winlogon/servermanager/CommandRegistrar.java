package org.winlogon.servermanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.winlogon.servermanager.commands.ProcessRunnerCommand;
import org.winlogon.servermanager.commands.SystemCommand;
import org.winlogon.servermanager.commands.TerminalCommand;

public class CommandRegistrar {
    private final ServerManagerPlugin plugin;
    private final TerminalCommand terminalCommand;
    private final SystemCommand systemCommand;
    private final ProcessRunnerCommand processRunnerCommand;

    public CommandRegistrar(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.terminalCommand = new TerminalCommand(plugin);
        this.systemCommand = new SystemCommand(plugin);
        this.processRunnerCommand = new ProcessRunnerCommand(plugin);

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
                processRunnerCommand.createCommand().build(),
                "Manage external processes"
            );
        });
    }
}
