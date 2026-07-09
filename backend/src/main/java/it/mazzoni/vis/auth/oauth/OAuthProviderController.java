package it.mazzoni.vis.auth.oauth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/oauth2/providers")
@Profile("!demo")
public class OAuthProviderController {

    private final ClientRegistrationRepository registrations;

    public OAuthProviderController(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations.getIfAvailable();
    }

    @GetMapping
    ResponseEntity<OAuthProviderStatus> providers() {
        boolean google = registrations != null
                && registrations.findByRegistrationId("google") != null;
        return ResponseEntity.ok(new OAuthProviderStatus(google));
    }

    record OAuthProviderStatus(boolean google) {}
}
