package world.sirius.auth.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.sirius.auth.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registration;

    public AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        registration.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();   // 202 в любом случае, см. RegistrationService
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verify(@RequestParam String token) {
        return registration.verifyEmail(token)
                ? ResponseEntity.ok("E-mail подтверждён. Теперь можно войти.")
                : ResponseEntity.badRequest().body("Ссылка недействительна или устарела.");
    }
}