package org.winlogon.servermanager.cron;

import org.bukkit.Bukkit;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.winlogon.servermanager.ServerManagerPlugin;
import org.winlogon.servermanager.config.CronConfig;
import org.winlogon.servermanager.ExceptionWrappers;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;

import java.util.List;
import java.util.logging.Logger;

public class CronJobManager {
    private final ServerManagerPlugin plugin;
    private final Logger logger;
    private Scheduler scheduler;

    public CronJobManager(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        try {
            this.scheduler = new StdSchedulerFactory().getScheduler();
        } catch (SchedulerException e) {
            logger.severe("Failed to initialize scheduler: " + e.getMessage());
        }
    }

    private void verifyState() {
        Verify.verifyNotNull(scheduler);
        // Check if scheduler is already started
        Preconditions.checkState(
            !ExceptionWrappers.safe(() -> scheduler.isStarted()),
            "Scheduler is already started"
        );
    }

    public void startScheduler() {
        verifyState();

        try {
            scheduler.start();
        } catch (SchedulerException e) {
            logger.severe("Failed to start scheduler: " + e.getMessage());
        }

        logger.info("Cron scheduler started.");
    }

    public void shutdownScheduler() {
        verifyState();

        try {
            // Wait for jobs to complete
            scheduler.shutdown(true);
        } catch (SchedulerException e) {
            logger.severe("Failed to shut down scheduler: " + e.getMessage());
        }

        logger.info("Cron scheduler shut down.");
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
                logger.info("Scheduled cron job: " + config.command + " with expression: " + config.expression);
            }
        } catch (SchedulerException e) {
            logger.severe("Failed to schedule cron jobs: " + e.getMessage());
        } catch (RuntimeException e) {
            logger.severe("Invalid cron expression: " + e.getMessage());
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
        }
    }
}
