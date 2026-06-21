package it.mazzoni.vis.jobs;

import it.mazzoni.vis.alerts.AlertDetectionService;
import it.mazzoni.vis.alerts.AlertDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertDetectionJob {
    private final AlertDetectionService detection;
    private final AlertDeliveryService delivery;
    private final JobRunLogger logger;

    public AlertDetectionJob(AlertDetectionService detection, AlertDeliveryService delivery, JobRunLogger logger) {
        this.detection = detection;
        this.delivery = delivery;
        this.logger = logger;
    }

    @Scheduled(cron = "${app.jobs.cron.alert-detection}")
    public void run() {
        logger.run("alert-detection", () -> {
            int created = detection.execute();
            delivery.deliverPendingHighPriorityAlerts();
            return created;
        });
    }
}
