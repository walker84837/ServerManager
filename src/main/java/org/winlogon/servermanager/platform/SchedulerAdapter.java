package org.winlogon.servermanager.platform;

import org.bukkit.plugin.Plugin;

public class SchedulerAdapter {
    private final Plugin plugin;
    private final boolean isFolia;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = isFolia();
    }

    public void runNow(Runnable task) {
        var server = plugin.getServer();

        if (isFolia) {
            server.getGlobalRegionScheduler().run(plugin, p -> task.run());
        } else {
            server.getScheduler().runTask(plugin, task);
        }
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
