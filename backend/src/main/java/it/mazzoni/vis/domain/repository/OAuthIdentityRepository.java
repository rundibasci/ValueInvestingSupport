package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.OAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    Optional<OAuthIdentity> findByProviderAndProviderEmail(String provider, String providerEmail);
}
