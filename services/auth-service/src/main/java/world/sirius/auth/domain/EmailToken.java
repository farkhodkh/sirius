package world.sirius.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_tokens")
public class EmailToken {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    private String tokenHash;              // SHA-256 от токена: сам токен в БД не хранится

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailToken() {
    }

    public EmailToken(String tokenHash, UUID userId, String purpose, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public String getPurpose() { return purpose; }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
