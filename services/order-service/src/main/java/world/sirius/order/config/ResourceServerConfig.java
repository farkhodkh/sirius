package world.sirius.order.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Боевая защита API: JWT выдаёт auth-service, подпись проверяется открытым ключом из его JWKS.
 * Действует во всех профилях, кроме dev (там DevSecurityConfig с общим секретом).
 */
@Configuration
@Profile("!dev")
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .csrf(c -> c.disable())                     // Bearer-токен, cookie-сессий нет
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Ключи берутся по ВНУТРЕННЕМУ адресу (jwks-uri), а iss проверяется как СТРОКА с внешним адресом:
     * контейнер order-service не может достучаться до https://auth.sirius.localhost, но iss в токене именно он.
     * JWKS загружается лениво при первом запросе, поэтому order-service стартует и без auth-service.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${sirius.auth.jwks-uri:http://localhost:9000/oauth2/jwks}") String jwksUri,
                          @Value("${sirius.auth.issuer:http://localhost:9000}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));   // exp/nbf + iss
        return decoder;
    }

    /** claim "scope" -> SCOPE_orders.read (для @PreAuthorize), claim "roles" -> ROLE_USER. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> out = new ArrayList<>(scopes.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            return out;
        });
        return converter;
    }
}
