package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.auth.AuthException;
import it.mazzoni.vis.auth.dto.LoginResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/oauth2")
@Profile("!demo")
public class OAuthTokenController {

    private final OAuthLoginSuccessHandler successHandler;

    public OAuthTokenController(OAuthLoginSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @GetMapping("/token")
    ResponseEntity<LoginResponse> exchangeHandoffCode(@RequestParam String code) {
        String accessToken = successHandler.consumeHandoffCode(code);
        if (accessToken == null) {
            throw new AuthException("Invalid or expired handoff code");
        }
        return ResponseEntity.ok(new LoginResponse(accessToken, 900));
    }
}
