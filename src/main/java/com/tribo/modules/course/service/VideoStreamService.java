package com.tribo.modules.course.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
@Slf4j
public class VideoStreamService {

    @Value("${safevideo.token:}")
    private String safeVideoToken;

    @Value("${safevideo.player-url:https://player.safevideo.com.br}")
    private String safeVideoPlayerUrl;

    @Value("${panda.api-key:}")
    private String pandaApiKey;

    @Value("${panda.player-url:https://player-vz-tribo.tv.pandavideo.com.br}")
    private String pandaPlayerUrl;

    /**
     * Segredo para assinar URLs do CDN S3.
     * Deve ser configurado via variável de ambiente CDN_SIGNING_SECRET.
     * Se não configurado, URLs S3 não são servidas (lança exceção).
     */
    @Value("${cdn.signing-secret:}")
    private String cdnSigningSecret;

    @Value("${cdn.base-url:https://cdn.triboinvest.com.br}")
    private String cdnBaseUrl;

    /** TTL das URLs de stream: 2 horas */
    private static final long URL_TTL_SECONDS = 7200L;

    public String generateUrl(String videoKey, String provider) {
        if (provider == null) {
            return generateSafeVideoUrl(videoKey);
        }
        return switch (provider.toLowerCase()) {
            case "safevideo" -> generateSafeVideoUrl(videoKey);
            case "panda"     -> generatePandaUrl(videoKey);
            case "s3"        -> generateS3SignedUrl(videoKey);
            default          -> throw new IllegalArgumentException("Provider desconhecido: " + provider);
        };
    }

    private String generateSafeVideoUrl(String videoKey) {
        log.debug("Gerando URL SafeVideo para videoKey={}", videoKey);
        StringBuilder url = new StringBuilder(safeVideoPlayerUrl)
                .append("/embed/")
                .append(videoKey);
        if (safeVideoToken != null && !safeVideoToken.isBlank()) {
            url.append("?token=").append(safeVideoToken);
        }
        return url.toString();
    }

    private String generatePandaUrl(String videoKey) {
        log.debug("Gerando URL Panda Video para videoKey={}", videoKey);
        return pandaPlayerUrl + "/embed/?v=" + videoKey;
    }

    /**
     * Gera URL CDN assinada com HMAC-SHA256.
     *
     * Formato da assinatura: HMAC-SHA256(secret, "videoKey:expires")
     * URL final: https://cdn.triboinvest.com.br/{videoKey}?expires={ts}&sig={hmac}
     *
     * O CDN (CloudFront/Cloudflare) valida a assinatura e o timestamp.
     * Sem assinatura correta, retorna 403. URL expira em 2 horas.
     */
    private String generateS3SignedUrl(String videoKey) {
        if (cdnSigningSecret == null || cdnSigningSecret.isBlank()) {
            throw new IllegalStateException(
                "CDN_SIGNING_SECRET não configurado. URLs S3 não podem ser geradas sem assinatura.");
        }

        long expires = Instant.now().getEpochSecond() + URL_TTL_SECONDS;
        String payload = videoKey + ":" + expires;
        String signature = hmacSha256(cdnSigningSecret, payload);

        String url = cdnBaseUrl + "/" + videoKey
                + "?expires=" + expires
                + "&sig=" + signature;

        log.debug("URL S3 assinada gerada para videoKey={}, expires={}", videoKey, expires);
        return url;
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar HMAC-SHA256: " + e.getMessage(), e);
        }
    }

    public Instant getExpiration() {
        return Instant.now().plusSeconds(URL_TTL_SECONDS);
    }
}
