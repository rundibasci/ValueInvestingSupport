package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.Alert;
import it.mazzoni.vis.domain.entity.AlertDeliveryStatus;
import it.mazzoni.vis.domain.entity.AlertPriority;
import it.mazzoni.vis.domain.entity.AlertStatus;
import it.mazzoni.vis.domain.repository.AlertRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AlertDeliveryService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AlertRepository alerts;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AlertEmailComposer composer;
    private final boolean enabled;
    private final String from;
    private final int maxAttempts;

    public AlertDeliveryService(AlertRepository alerts, ObjectProvider<JavaMailSender> mailSender,
                                AlertEmailComposer composer,
                                @Value("${app.alert-email.enabled:false}") boolean enabled,
                                @Value("${app.alert-email.from:}") String from,
                                @Value("${app.alert-email.max-attempts:3}") int maxAttempts) {
        this.alerts = alerts;
        this.mailSender = mailSender;
        this.composer = composer;
        this.enabled = enabled;
        this.from = from;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void deliverPendingHighPriorityAlerts() {
        List<Alert> pending = alerts.findByPriorityAndStatusAndDeliveryStatus(
                AlertPriority.HIGH, AlertStatus.ACTIVE, AlertDeliveryStatus.PENDING);
        pending.forEach(this::deliver);
    }

    @Transactional
    public void deliver(Alert alert) {
        if (alert.getPriority() != AlertPriority.HIGH || alert.getStatus() != AlertStatus.ACTIVE
                || alert.getDeliveryStatus() == AlertDeliveryStatus.SENT || alert.getDeliveryAttempts() >= maxAttempts) {
            return;
        }
        String recipient = alert.getUser().getEmail();
        if (recipient == null || !EMAIL.matcher(recipient).matches()) {
            alert.setDeliveryError("Recipient email is missing or invalid");
            alert.setDeliveryStatus(AlertDeliveryStatus.SKIPPED);
            alerts.save(alert);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null || from.isBlank()) return;

        alert.setDeliveryAttempts(alert.getDeliveryAttempts() + 1);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setFrom(from);
            message.setSubject(composer.subject(alert));
            message.setText(composer.body(alert));
            sender.send(message);
            alert.setDeliveryStatus(AlertDeliveryStatus.SENT);
            alert.setDeliveredAt(LocalDateTime.now());
            alert.setDeliveryError(null);
        } catch (RuntimeException ex) {
            fail(alert, "SMTP delivery failed", false);
        }
        alerts.save(alert);
    }

    private void fail(Alert alert, String reason, boolean ignored) {
        alert.setDeliveryError(reason);
        alert.setDeliveryStatus(alert.getDeliveryAttempts() >= maxAttempts ? AlertDeliveryStatus.FAILED : AlertDeliveryStatus.PENDING);
        alerts.save(alert);
    }
}
