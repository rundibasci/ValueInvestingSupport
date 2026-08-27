package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.domain.entity.OAuthIdentity;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OAuthAccountResolver {

    static final String PROVIDER_GOOGLE = "GOOGLE";

    private final OAuthIdentityRepository oauthRepo;
    private final UserRepository userRepo;
    private final OAuthSecurityEventService securityEvents;

    public OAuthAccountResolver(OAuthIdentityRepository oauthRepo,
                                UserRepository userRepo,
                                OAuthSecurityEventService securityEvents) {
        this.oauthRepo = oauthRepo;
        this.userRepo = userRepo;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public User resolve(String providerSubject, String verifiedEmail, String displayName) {
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            securityEvents.recordResolutionRejected("missing_verified_email");
            throw new IllegalArgumentException("Verified email is required for Google sign-in");
        }

        String normalizedEmail = verifiedEmail.trim().toLowerCase();
        return oauthRepo.findByProviderAndProviderSubject(PROVIDER_GOOGLE, providerSubject)
                .map(identity -> {
                    securityEvents.recordIdentityReused();
                    // OAuthIdentity.user is FetchType.LAZY: identity.getUser() returns an
                    // uninitialized Hibernate proxy. This method's @Transactional session
                    // closes as soon as resolve() returns, so the caller (OAuthLoginSuccessHandler,
                    // outside any session) would hit LazyInitializationException on its first
                    // field access (e.g. user.isActive()). Initialize while the session is open.
                    User user = identity.getUser();
                    Hibernate.initialize(user);
                    return user;
                })
                .orElseGet(() -> linkOrCreate(providerSubject, normalizedEmail));
    }

    private User linkOrCreate(String providerSubject, String verifiedEmail) {
        Optional<User> existing = userRepo.findByEmail(verifiedEmail);
        User user = existing.orElseGet(() -> createUser(verifiedEmail));

        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider(PROVIDER_GOOGLE);
        identity.setProviderSubject(providerSubject);
        identity.setProviderEmail(verifiedEmail);
        oauthRepo.save(identity);
        if (existing.isPresent()) {
            securityEvents.recordAccountLinked();
        }

        return user;
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setRole(UserRole.INVESTOR);
        securityEvents.recordAccountCreated();
        return userRepo.save(user);
    }
}
