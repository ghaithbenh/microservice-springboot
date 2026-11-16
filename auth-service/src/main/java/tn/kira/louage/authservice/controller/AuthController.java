package tn.kira.louage.authservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tn.kira.louage.authservice.dto.AuthResponse;
import tn.kira.louage.authservice.dto.LoginRequest;
import tn.kira.louage.authservice.dto.RegisterRequest;
import tn.kira.louage.authservice.dto.UserResponse;
import tn.kira.louage.authservice.entity.AppUser;
import tn.kira.louage.authservice.entity.Role;
import tn.kira.louage.authservice.repository.AppUserRepository;
import tn.kira.louage.authservice.service.JwtService;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration and JWT login")
public class AuthController {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates a DRIVER or PASSENGER account. Returns 409 if the username already exists.")
    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            return problem(HttpStatus.BAD_REQUEST, "role must be DRIVER or PASSENGER");
        }
        if (repository.existsByUsername(request.username())) {
            return problem(HttpStatus.CONFLICT, "username already exists: " + request.username());
        }
        AppUser user = repository.save(AppUser.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.getId(), user.getUsername(), user.getRole()));
    }

    @Operation(summary = "Log in",
            description = "Verifies the credentials and returns a JWT valid for one hour.")
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        AppUser user = repository.findByUsername(request.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirySeconds(),
                user.getUsername(),
                user.getRole().name()));
    }

    private ResponseEntity<Object> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
