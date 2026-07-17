package it.mazzoni.vis.admin;

import it.mazzoni.vis.admin.dto.SecurityIsinResponse;
import it.mazzoni.vis.admin.dto.SetSecurityIsinRequest;
import it.mazzoni.vis.domain.entity.Security;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/securities")
@Profile("!demo")
public class AdminSecurityController {
    private final SecurityIsinService securityIsinService;

    public AdminSecurityController(SecurityIsinService securityIsinService) {
        this.securityIsinService = securityIsinService;
    }

    @PutMapping("/{securityId}/isin")
    public SecurityIsinResponse setIsin(@PathVariable UUID securityId, @Valid @RequestBody SetSecurityIsinRequest request) {
        Security saved = securityIsinService.assignIsin(securityId, request.isin());
        return new SecurityIsinResponse(saved.getId(), saved.getSymbol(), saved.getIsin());
    }
}
