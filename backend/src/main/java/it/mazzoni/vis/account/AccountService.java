package it.mazzoni.vis.account;

import it.mazzoni.vis.auth.AuthException;
import it.mazzoni.vis.domain.entity.OAuthIdentity;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountService {

    private static final String GOOGLE = "GOOGLE";

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;

    public AccountService(UserRepository userRepository, OAuthIdentityRepository oauthIdentityRepository) {
        this.userRepository = userRepository;
        this.oauthIdentityRepository = oauthIdentityRepository;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String email) {
        User user = findUser(email);
        return toResponse(user);
    }

    @Transactional
    public AccountResponse unlinkGoogle(String email) {
        User user = findUser(email);
        if (!hasLocalPassword(user)) {
            throw new AuthException("Cannot unlink Google because it is the only sign-in method");
        }

        oauthIdentityRepository.findByProviderAndUser(GOOGLE, user)
                .ifPresent(oauthIdentityRepository::delete);
        return toResponse(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    private AccountResponse toResponse(User user) {
        boolean googleLinked = oauthIdentityRepository.findByProviderAndUser(GOOGLE, user).isPresent();
        return new AccountResponse(
                user.getEmail(),
                user.getRole().name(),
                googleLinked,
                hasLocalPassword(user)
        );
    }

    private boolean hasLocalPassword(User user) {
        return StringUtils.hasText(user.getPasswordHash());
    }
}
