package it.mazzoni.vis.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPingController {

    @GetMapping("/ping")
    ResponseEntity<Map<String, String>> ping(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .orElse("UNKNOWN");
        return ResponseEntity.ok(Map.of("status", "ok", "role", role));
    }
}
