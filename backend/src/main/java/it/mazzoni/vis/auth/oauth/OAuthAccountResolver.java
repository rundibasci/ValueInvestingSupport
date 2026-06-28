package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.domain.entity.OAuthIdentity;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountResolver {

    static final String PROVIDER_GOOGLE = "GOOGLE";

    private final OAuthIdentityRepository oauthRepo;
    private final UserRepository userRepo;

    public OAuthAccountResolver(OAuthIdentityRepository oauthRepo, UserRepository userRepo) {
        this.oauthRepo = oauthRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public User resolve(String providerSubject, String verifiedEmail, String displayName) {
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new IllegalArgumentException("Verified email is required for Google sign-in");
        }

        return oauthRepo.findByProviderAndProviderSubject(PROVIDER_GOOGLE, providerSubject)
                .map(OAuthIdentity::getUser)
                .orElseGet(() -> linkOrCreate(providerSubject, verifiedEmail));
    }

    private User linkOrCreate(String providerSubject, String verifiedEmail) {
        User user = userRepo.findByEmail(verifiedEmail)
                .orElseGet(() -> createUser(verifiedEmail));

        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider(PROVIDER_GOOGLE);
        identity.setProviderSubject(providerSubject);
        identity.setProviderEmail(verifiedEmail);
        oauthRepo.save(identity);

        return user;
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setRole(UserRole.INVESTOR);
        return userRepo.save(user);
    }
}
