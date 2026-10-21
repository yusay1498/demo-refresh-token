package com.yusay.api.presentation;

import com.yusay.api.application.AuthApplicationService;
import com.yusay.api.domain.entity.UserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthRestController {
    private final AuthenticationManager authManager;
    private final AuthApplicationService authApp;

    public AuthRestController(AuthenticationManager authManager, AuthApplicationService authApp) {
        this.authManager = authManager;
        this.authApp = authApp;
    }

    public record SigninRequest(String username, String password) {}
    public record SigninResponse(String token, String username, List<String> authorities) {}

    @PostMapping("/signin")
    public ResponseEntity<SigninResponse> login(@RequestBody SigninRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));

        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String token = authApp.issueToken(auth.getName(), authorities);
        return ResponseEntity.ok(new SigninResponse(token, auth.getName(), authorities));
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return authApp.userInfo(jwt);
    }

    @GetMapping("/anon")
    public String anon() {
        return "anonymous";
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<Void> onAuthError(Exception e) {
        return ResponseEntity.status(401).build();
    }
}
