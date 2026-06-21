package it.mazzoni.vis.jobs;

import it.mazzoni.vis.alerts.AlertDetectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertDetectionJob {
    private final AlertDetectionService detection;
    private final JobRunLogger logger;

    public AlertDetectionJob(AlertDetectionService detection, JobRunLogger logger) {
        this.detection = detection;
        this.logger = logger;
    }

    @Scheduled(cron = "${app.jobs.cron.alert-detection}")
    public void run() {
        logger.run("alert-detection", detection::execute);
    }
}
