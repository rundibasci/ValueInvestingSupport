package it.mazzoni.vis.realdemo;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("realDemo")
public class RealDemoStartupListener {

    @EventListener(ApplicationReadyEvent.class)
    @Order(40)
    public void onReady() {
        System.out.println("""

            Real demo ready: http://localhost:5173
            API health:      http://localhost:8080/actuator/health
            Admin login:     admin@realdemo.local / admin
            Investor login:  investor@realdemo.local / admin
            """);
    }
}
