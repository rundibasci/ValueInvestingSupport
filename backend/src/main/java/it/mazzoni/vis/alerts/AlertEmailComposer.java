package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.Alert;
import org.springframework.stereotype.Component;

@Component
public class AlertEmailComposer {
    private static final String DISCLAIMER = "This is a decision-support notification, not investment advice (MiFID II).";

    public String subject(Alert alert) {
        return "Value Investing Support alert: " + alert.getSymbol();
    }

    public String body(Alert alert) {
        String threshold = alert.getThreshold() == null ? "not applicable" : alert.getThreshold().toPlainString();
        return "A high-priority " + alert.getAlertType() + " alert was triggered for " + alert.getSymbol() + ".\n\n"
                + "Triggered at: " + alert.getTriggeredAt() + "\n"
                + "Threshold/context value: " + threshold + "\n\n"
                + "Review your active alerts in the application: /api/v1/watchlist/alerts\n\n"
                + DISCLAIMER;
    }
}
