package world.sirius.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.sirius.auth.domain.EmailToken;
import world.sirius.auth.domain.User;
import world.sirius.auth.repository.EmailTokenRepository;
import world.sirius.auth.repository.UserRepository;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    private static final SecureRandom RANDOM = new SecureRandom();    // криптостойкий источник, не java.util.Random

    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final String publicUrl;
    private final String mailFrom;

    public RegistrationService(UserRepository users, EmailTokenRepository tokens, PasswordEncoder encoder,
                               JavaMailSender mail,
                               @Value("${sirius.auth.public-url}") String publicUrl,
                               @Value("${sirius.auth.mail-from}") String mailFrom) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.publicUrl = publicUrl;
        this.mailFrom = mailFrom;
    }

    /** Всегда завершается одинаково, занят e-mail или нет: иначе по ответу можно перебирать зарегистрированных. */
    @Transactional
    public void register(String email, String rawPassword) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            log.info("Registration skipped: e-mail already registered");
            return;
        }
        User user = users.save(new User(UUID.randomUUID(), normalized, encoder.encode(rawPassword)));

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new EmailToken(sha256Hex(token), user.getId(), VERIFY_EMAIL,
                Instant.now().plus(Duration.ofHours(24))));

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(normalized);
        msg.setSubject("Подтвердите e-mail - Сириус");
        msg.setText("Перейдите по ссылке, чтобы подтвердить e-mail: "
                + publicUrl + "/api/auth/verify?token=" + token);
        try {
            mail.send(msg);   // в боевой системе отправку выносят в outbox/очередь, как заказы
        } catch (MailException e) {
            log.warn("Verification mail was not sent: {}", e.getMessage());
        }
    }

    /** @return true, если токен найден, не просрочен и ещё не использован. */
    @Transactional
    public boolean verifyEmail(String token) {
        Instant now = Instant.now();
        return tokens.findById(sha256Hex(token))
                .filter(t -> VERIFY_EMAIL.equals(t.getPurpose()) && t.isUsable(now))
                .flatMap(t -> users.findById(t.getUserId()).map(u -> {
                    u.markEmailVerified();
                    t.markUsed(now);
                    return true;
                }))
                .orElse(false);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
