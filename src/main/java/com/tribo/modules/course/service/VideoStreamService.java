package com.tribo.modules.course.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

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

    public String generateUrl(String videoKey, String provider) {
        if (provider == null) {
            return generateSafeVideoUrl(videoKey);
        }
        if ("safevideo".equalsIgnoreCase(provider)) {
            return generateSafeVideoUrl(videoKey);
        } else if ("panda".equalsIgnoreCase(provider)) {
            return generatePandaUrl(videoKey);
        } else if ("s3".equalsIgnoreCase(provider)) {
            return generateS3Url(videoKey);
        } else {
            throw new IllegalArgumentException("Provider desconhecido: " + provider);
        }
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

    private String generateS3Url(String videoKey) {
        log.debug("Gerando URL S3 para videoKey={}", videoKey);
        return "https://cdn.triboinvest.com.br/" + videoKey
                + "?expires=" + (Instant.now().getEpochSecond() + 7200);
    }

    public Instant getExpiration() {
        return Instant.now().plusSeconds(7200);
    }
}