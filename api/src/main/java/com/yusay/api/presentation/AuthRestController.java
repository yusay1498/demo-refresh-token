package com.yusay.api.presentation;

import com.yusay.api.application.AuthApplicationService;
import com.yusay.api.application.exception.UserAuthoritiesUnavailableException;
import com.yusay.api.domain.entity.UserInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthRestController {
    private final AuthApplicationService authApp;

    public AuthRestController(AuthApplicationService authApp) {
        this.authApp = authApp;
    }

    public record SignInRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record SignOutRequest(@NotBlank String refreshToken) {}
    public record TokenResponse(
        String token,
        String username,
        List<String> authorities,
        String refreshToken,
        String tokenType,
        long expiresIn
    ) {}

    @PostMapping("/signIn")
    public ResponseEntity<TokenResponse> signIn(@Valid @RequestBody SignInRequest request) {
        return ResponseEntity.ok(toResponse(authApp.signIn(request.username(), request.password())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthApplicationService.RefreshResult result = authApp.refresh(request.refreshToken());
        if (result instanceof AuthApplicationService.RefreshSucceeded(AuthApplicationService.TokenSet tokens)) {
            return ResponseEntity.ok(toResponse(tokens));
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/signOut")
    public ResponseEntity<Void> signOut(@Valid @RequestBody SignOutRequest request) {
        authApp.signOut(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signOutAll")
    public ResponseEntity<Void> signOutAll(Authentication authentication) {
        authApp.signOutAll(authentication.getName(), authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return authApp.userInfo(jwt);
    }

    @GetMapping("/anon")
    public String anon() {
        return "anonymous";
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> onAuthError(AuthenticationException exception) {
        return ResponseEntity.status(401).build();
    }

    @ExceptionHandler(UserAuthoritiesUnavailableException.class)
    public ResponseEntity<Void> onAuthoritiesUnavailable(
        UserAuthoritiesUnavailableException exception
    ) {
        return ResponseEntity.status(503).build();
    }

    @ExceptionHandler({
        CannotCreateTransactionException.class,
        DataAccessResourceFailureException.class
    })
    public ResponseEntity<Void> onDatabaseUnavailable(RuntimeException exception) {
        return ResponseEntity.status(503).build();
    }

    private TokenResponse toResponse(AuthApplicationService.TokenSet tokens) {
        return new TokenResponse(
            tokens.token(),
            tokens.username(),
            tokens.authorities(),
            tokens.refreshToken(),
            tokens.tokenType(),
            tokens.expiresIn()
        );
    }
}
