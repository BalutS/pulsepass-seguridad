package com.pulsepass.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** Solo emisión (etapa 4). Los claves son aleatorias y exclusivas del test: no hay secretos en el repo. */
class JwtServiceTest {

    private static final String ISSUER = "pulsepass-api";

    private static String randomSecret(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static JwtService service(String secret) {
        return new JwtService(new JwtProperties(ISSUER, secret, Duration.ofMinutes(15)), Clock.systemUTC());
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private static Jws<Claims> parse(String token, SecretKey key) {
        return Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build().parseSignedClaims(token);
    }

    @Test
    void generatesSignedTokenWithExpectedClaims() {
        String secret = randomSecret(32);
        String token = service(secret).generateAccessToken(42L);

        Claims claims = parse(token, key(secret)).getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(900_000L);
        assertThat(claims.getExpiration()).isInTheFuture();
    }

    @Test
    void payloadContainsOnlyMinimalClaims_noSensitiveData() {
        String secret = randomSecret(32);
        Claims claims = parse(service(secret).generateAccessToken(1L), key(secret)).getPayload();

        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "jti");
    }

    @Test
    void algorithmIsAlwaysHS256_evenWithALongerKey() {
        String secret = randomSecret(48); // sin algoritmo explícito JJWT elegiría HS384
        String token = service(secret).generateAccessToken(1L);

        assertThat(parse(token, key(secret)).getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void everyTokenHasAUniqueJti() {
        JwtService service = service(randomSecret(32));

        assertThat(service.generateAccessToken(1L)).isNotEqualTo(service.generateAccessToken(1L));
    }

    @Test
    void exposesTtlInSeconds() {
        assertThat(service(randomSecret(32)).accessTokenTtlSeconds()).isEqualTo(900L);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String secret = randomSecret(32);
        String[] parts = service(secret).generateAccessToken(1L).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\",\"iss\":\"pulsepass-api\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> parse(forged, key(secret))).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(randomSecret(32)).generateAccessToken(1L);

        assertThatThrownBy(() -> parse(token, key(randomSecret(32)))).isInstanceOf(JwtException.class);
    }

    @Test
    void missingSecretFailsAtStartup() {
        assertThatThrownBy(() -> service(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> service("   "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
    }

    @Test
    void weakSecretFailsAtStartup() {
        assertThatThrownBy(() -> service(randomSecret(31)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("too weak");
        assertThatThrownBy(() -> service(randomSecret(16)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidBase64FailsAtStartup() {
        assertThatThrownBy(() -> service("%%%esto-no-es-base64%%%"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void errorMessagesNeverLeakTheSecret() {
        String weak = randomSecret(16);

        assertThatThrownBy(() -> service(weak))
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(weak));
    }

    @Test
    void nullUserIdIsRejected() {
        assertThatThrownBy(() -> service(randomSecret(32)).generateAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesToStringMasksTheSecret() {
        String secret = randomSecret(32);

        assertThat(new JwtProperties(ISSUER, secret, Duration.ofMinutes(15)).toString())
                .doesNotContain(secret);
    }

    @Test
    void propertiesRejectNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(ISSUER, randomSecret(32), Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
