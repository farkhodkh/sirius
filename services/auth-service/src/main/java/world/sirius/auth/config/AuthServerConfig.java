package world.sirius.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import world.sirius.auth.repository.UserRepository;

@Configuration
public class AuthServerConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthServerConfig.class);

    /** Цепочка 1: протокольные эндпоинты (/oauth2/authorize, /oauth2/token, /oauth2/jwks, /.well-known/...). */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(as -> {
            http.securityMatcher(as.getEndpointsMatcher());     // эта цепочка - ТОЛЬКО для протокольных эндпоинтов
            as.oidc(Customizer.withDefaults());                 // OIDC: discovery, userinfo, id_token
        });
        http.authorizeHttpRequests(a -> a.anyRequest().authenticated()); 
        http.exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),                     // браузер без входа - на страницу логина
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /** Цепочка 2: всё остальное - форма входа, регистрация, подтверждение e-mail, health. */
    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/register", "/api/auth/verify", "/error",
                                "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .csrf(c -> c.ignoringRequestMatchers("/api/auth/register"));        // JSON-регистрация без CSRF; форма входа - С CSRF
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();          // по умолчанию BCrypt, префикс {bcrypt}
    }

    /** Войти может только пользователь с подтверждённым e-mail. */
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                        .password(u.getPasswordHash() == null ? "{noop}" : u.getPasswordHash())
                        .disabled(!u.isEmailVerified())
                        .roles(u.getRoles().split(","))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("not found"));      // клиент увидит общее «неверные данные»
    }

    /**
     * Клиенты: публичные (секрета нет), только Authorization Code + PKCE.
     * Публичным клиентам Authorization Server НЕ выдаёт refresh-токен (правило библиотеки): по истечении access token
     * приложение заново идёт в браузер за кодом, а живая SSO-сессия делает это без повторного ввода пароля.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository() {
        RegisteredClient web = publicClient("sirius-web")
                .redirectUri("http://localhost:5173/callback")
                .redirectUri("https://app.sirius.localhost/callback")
                .build();
        RegisteredClient mobile = publicClient("sirius-mobile")
                .redirectUri("world.sirius.app:/oauth2redirect")                    // лучше App Links / Universal Links
                .build();
        return new InMemoryRegisteredClientRepository(web, mobile);
    }

    private static RegisteredClient.Builder publicClient(String clientId) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope(OidcScopes.OPENID).scope(OidcScopes.EMAIL)
                .scope("orders.read").scope("orders.write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)                                      // PKCE обязателен
                        .requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))        // короткий access token
                        .build());
    }

    /**
     * УЧЕБНЫЙ вариант: ключ создаётся при каждом старте, поэтому после перезапуска все выданные токены
     * недействительны, а две реплики несовместимы. В боевой системе ключ загружают из секрета.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        log.warn("JWT signing key generated in memory (kid={}): tokens will not survive a restart", rsa.getKeyID());
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }

    /** iss в токенах - внешний адрес сервера, а не имя контейнера. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${sirius.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /** В access token: sub = UUID пользователя (e-mail может смениться), плюс email и roles. */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(UserRepository users) {
        return ctx -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(ctx.getTokenType())) {
                var user = users.findByEmailIgnoreCase(ctx.getPrincipal().getName()).orElseThrow();
                ctx.getClaims()
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("roles", List.of(user.getRoles().split(",")));
            }
        };
    }
}
