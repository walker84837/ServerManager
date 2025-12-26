package org.winlogon.servermanager.cron;

import org.bukkit.Bukkit;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.winlogon.servermanager.ServerManagerPlugin;
import org.winlogon.servermanager.config.CronConfig;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CronJobManager {
    private final ServerManagerPlugin plugin;
    private final Logger logger;
    private Scheduler scheduler;

    public CronJobManager(ServerManagerPlugin plugin) throws SchedulerException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        var props = new Properties();
        // Set thread count to 1
        props.put("org.quartz.threadPool.threadCount", "1");
        this.scheduler = new StdSchedulerFactory(props).getScheduler();
    }

    public void startScheduler() {
        if (scheduler == null) {
            return;
        }

        try {
            if (scheduler.isStarted()) {
                throw new IllegalStateException("Scheduler is already started");
            }
        } catch (SchedulerException e) {
            logger.log(Level.SEVERE, "Failed to check scheduler status: " + e.getMessage());
            return;
        }

        try {
            scheduler.start();
        } catch (SchedulerException e) {
            logger.log(Level.SEVERE, "Failed to start scheduler: " + e.getMessage());
        }

        logger.log(Level.INFO, "Cron scheduler started.");
    }

    public void shutdownScheduler() {
        if (scheduler == null) {
            return;
        }

        try {
            if (scheduler.isShutdown()) {
                return;
            }
            // Wait for jobs to complete
            scheduler.shutdown(true);
            logger.log(Level.INFO, "Cron scheduler shut down.");
        } catch (SchedulerException e) {
            logger.log(Level.SEVERE, "Failed to shut down scheduler: " + e.getMessage());
        }
    }

    public void scheduleJobs(List<CronConfig> cronConfigs) {
        if (scheduler == null) return;

        try {
            scheduler.clear(); // Clear existing jobs before rescheduling

            for (int i = 0; i < cronConfigs.size(); i++) {
                var config = cronConfigs.get(i);
                if (!config.enabled || config.expression.isEmpty() || config.command.isEmpty()) {
                    continue;
                }

                var jobData = new JobDataMap();
                jobData.put("command", config.command);
                jobData.put("plugin", plugin);

                JobDetail job = JobBuilder.newJob(CronCommandJob.class)
                    .withIdentity("cronJob-" + i, "serverManager")
                    .usingJobData(jobData)
                    .build();

                CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("cronTrigger-" + i, "serverManager")
                    .withSchedule(CronScheduleBuilder.cronSchedule(config.expression))
                    .build();

                scheduler.scheduleJob(job, trigger);
                logger.log(Level.INFO, "Scheduled cron job: " + config.command + " with expression: " + config.expression);
            }
        } catch (SchedulerException e) {
            logger.log(Level.SEVERE, "Failed to schedule cron jobs: " + e.getMessage());
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Invalid cron expression: " + e.getMessage());
        }
    }

    public static class CronCommandJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap data = context.getJobDetail().getJobDataMap();
            var command = data.getString("command");
            var plugin = (ServerManagerPlugin) data.get("plugin");

            plugin.getLogger().info("Executing cron command: " + command);

            // Execute the command on the main thread to interact with Bukkit API safely
            // TODO: make this Folia-compatible
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
        }
    }
}
