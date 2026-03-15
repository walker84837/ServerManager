package org.winlogon.servermanager.platform;

import org.bukkit.plugin.Plugin;
import org.winlogon.asynccraftr.AsyncCraftr;

import java.time.Duration;

public class SchedulerAdapter {
    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the given task asynchronously on the server's async thread pool.
     * @param task the task to execute
     */
    public void runNow(Runnable task) {
        AsyncCraftr.runAsyncTask(plugin, task);
    }

    /**
     * Executes the given task asynchronously on the server's async thread pool after the specified delay.
     * @param task  the task to execute
     * @param delay the delay before executing the task
     */
    public void runLater(Runnable task, Duration delay) {
        AsyncCraftr.runAsyncTaskLater(plugin, task, delay);
    }
}
