package org.winlogon.servermanager.platform;

import org.winlogon.servermanager.ServerManagerPlugin;

public class SchedulerAdapter {
    private final ServerManagerPlugin plugin;
    private final boolean isFolia;

    public SchedulerAdapter(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.isFolia = isFolia();
    }

    public void runNow(Runnable task) {
        if (isFolia) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, p -> task.run());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
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
