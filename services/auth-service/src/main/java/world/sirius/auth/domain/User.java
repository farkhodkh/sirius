package world.sirius.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;           // null у пользователей, вошедших только через внешнего провайдера

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private String roles = "USER";         // через запятую: "USER,ADMIN"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // для Hibernate
    }

    public User(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getRoles() { return roles; }
    public Instant getCreatedAt() { return createdAt; }

    public void markEmailVerified() {
        this.emailVerified = true;
    }
}

