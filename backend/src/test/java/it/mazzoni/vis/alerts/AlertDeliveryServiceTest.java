package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.Alert;
import it.mazzoni.vis.domain.entity.AlertDeliveryStatus;
import it.mazzoni.vis.domain.entity.AlertPriority;
import it.mazzoni.vis.domain.entity.AlertStatus;
import it.mazzoni.vis.domain.entity.AlertType;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertDeliveryServiceTest {

    @Test
    void sendsOneEmailForEligibleHighPriorityAlert() {
        AlertRepository alerts = mock(AlertRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        AlertDeliveryService service = new AlertDeliveryService(alerts, provider, new AlertEmailComposer(), true,
                "alerts@example.test", 3);
        Alert alert = highPriorityAlert("investor@example.test");

        service.deliver(alert);

        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        verify(alerts).save(alert);
        assertThat(alert.getDeliveryStatus()).isEqualTo(AlertDeliveryStatus.SENT);
        assertThat(alert.getDeliveryAttempts()).isEqualTo(1);
        assertThat(alert.getDeliveredAt()).isNotNull();
    }

    @Test
    void invalidRecipientDoesNotSendAndIsRecorded() {
        AlertRepository alerts = mock(AlertRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        AlertDeliveryService service = new AlertDeliveryService(alerts, provider, new AlertEmailComposer(), true,
                "alerts@example.test", 3);
        Alert alert = highPriorityAlert("not-an-email");

        service.deliver(alert);

        assertThat(alert.getDeliveryStatus()).isEqualTo(AlertDeliveryStatus.SKIPPED);
        assertThat(alert.getDeliveryError()).contains("invalid");
        verify(alerts).save(alert);
    }

    @Test
    void emailContainsFactualContextAndDisclaimer() {
        String body = new AlertEmailComposer().body(highPriorityAlert("investor@example.test"));

        assertThat(body).contains("AAPL", "DIVIDEND_CUT", "15.0", "MiFID II");
    }

    private Alert highPriorityAlert(String email) {
        User user = new User();
        user.setEmail(email);
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setSymbol("AAPL");
        alert.setAlertType(AlertType.DIVIDEND_CUT);
        alert.setThreshold(new BigDecimal("15.0"));
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setPriority(AlertPriority.HIGH);
        alert.setTriggeredAt(LocalDateTime.now());
        return alert;
    }
}
