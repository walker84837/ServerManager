package org.winlogon.servermanager;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.winlogon.servermanager.commands.ProcessRunnerCommand;
import org.winlogon.servermanager.commands.SystemCommand;
import org.winlogon.servermanager.commands.TerminalCommand;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandRegistrar {
    private final ServerManagerPlugin plugin;
    private final Map<PluginCommand, String> commands;

    public CommandRegistrar(ServerManagerPlugin plugin) {
        this.plugin = plugin;

        this.commands = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(new TerminalCommand(plugin), "Execute terminal commands"),
            Map.entry(new SystemCommand(plugin), "Manage system resources and packages"),
            Map.entry(new ProcessRunnerCommand(plugin), "Manage external processes")
        ));

        // Register each command's required permission
        for (var command : commands.keySet()) {
            command.registerIfNotExists(plugin);
        }
    }

    public void registerCommands() {
        var lifecycleManager = plugin.getLifecycleManager();

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();

            // Register each command with Paper
            for (var entry : commands.entrySet()) {
                var command = entry.getKey();
                var description = entry.getValue();

                registrar.register(command.createCommand().build(), description);
            }
        });
    }
}
