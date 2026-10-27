package world.sirius.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ResourceServerConfigTest {

    @Test
    void scopesAndRolesBecomeAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("scope", "openid orders.read orders.write")
                .claim("roles", java.util.List.of("USER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        var auth = ResourceServerConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_orders.read", "SCOPE_orders.write", "ROLE_USER");
    }

    @Test
    void tokenWithoutRolesClaimStillConverts() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
                .claim("scope", "orders.read").build();

        var auth = ResourceServerConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_orders.read").doesNotContain("ROLE_USER");
    }
}

