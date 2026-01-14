package org.winlogon.servermanager.platform;

import org.bukkit.plugin.Plugin;
import org.winlogon.asynccraftr.AsyncCraftr;

import java.time.Duration;

public class SchedulerAdapter {
    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runNow(Runnable task) {
        AsyncCraftr.runAsyncTask(plugin, task);
    }

    public void runLater(Runnable task, Duration delay) {
        AsyncCraftr.runAsyncTaskLater(plugin, task, delay);
    }
}
