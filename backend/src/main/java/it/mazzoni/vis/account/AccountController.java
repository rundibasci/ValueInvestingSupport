package it.mazzoni.vis.account;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@Profile("!demo")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    AccountResponse getAccount(Authentication authentication) {
        return accountService.getAccount(authentication.getName());
    }

    @DeleteMapping("/oauth/google")
    ResponseEntity<AccountResponse> unlinkGoogle(Authentication authentication) {
        return ResponseEntity.ok(accountService.unlinkGoogle(authentication.getName()));
    }
}
