package world.sirius.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.sirius.auth.domain.EmailToken;

public interface EmailTokenRepository extends JpaRepository<EmailToken, String> {
}
