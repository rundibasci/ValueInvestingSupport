package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SecurityIsinService {
    private final SecurityRepository securities;

    public SecurityIsinService(SecurityRepository securities) {
        this.securities = securities;
    }

    @Transactional
    public Security assignIsin(UUID securityId, String isin) {
        Security target = securities.findById(securityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Security not found"));
        securities.findByIsin(isin).ifPresent(existing -> {
            if (!existing.getId().equals(target.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ISIN is already assigned to another security");
        });
        if (target.getIsin() != null && !target.getIsin().equals(isin))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Security already has another ISIN");
        target.setIsin(isin);
        return securities.save(target);
    }
}
