package com.tribo.modules.course.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Gera URLs de stream seguras para os vídeos.
 *
 * Fase 1: Panda Video — API simples com URL pública + token
 * Fase 2: S3 + CloudFront — URL assinada com expiração (mais seguro)
 *
 * TODO: implementar S3 signed URLs quando migrar do Panda Video
 */
@Service
@Slf4j
public class VideoStreamService {

    @Value("${panda.api-key:}")
    private String pandaApiKey;

    /**
     * Gera a URL de stream baseado no provider configurado na aula.
     *
     * @param videoKey  Chave/ID do vídeo no provider
     * @param provider  "panda" ou "s3"
     */
    public String generateUrl(String videoKey, String provider) {
        if ("panda".equals(provider)) {
            return generatePandaUrl(videoKey);
        } else if ("s3".equals(provider)) {
            return generateS3Url(videoKey);
        } else {
            throw new IllegalArgumentException("Provider desconhecido: " + provider);
        }
    }

    /**
     * Panda Video — retorna a URL de stream HLS.
     * O Panda Video gera automaticamente múltiplas qualidades (480p, 720p, 1080p).
     *
     * Documentação: https://developers.pandavideo.com.br
     */
    private String generatePandaUrl(String videoKey) {
        // TODO: Implementar chamada real à API do Panda Video
        // Exemplo de URL real do Panda: https://player-vz-xxx.tv.pandavideo.com.br/embed/?v={videoKey}
        log.debug("Gerando URL Panda Video para: {}", videoKey);
        return "https://player-vz-tribo.tv.pandavideo.com.br/embed/?v=" + videoKey;
    }

    /**
     * S3 + CloudFront — URL assinada com 2 horas de expiração.
     * Mais seguro que o Panda para conteúdo exclusivo.
     *
     * TODO: implementar com software.amazon.awssdk:cloudfront
     */
    private String generateS3Url(String videoKey) {
        // TODO: gerar CloudFront signed URL
        log.debug("Gerando URL S3 para: {}", videoKey);
        return "https://cdn.triboinvest.com.br/" + videoKey + "?expires=" + (Instant.now().getEpochSecond() + 7200);
    }

    /** Retorna quando a URL vai expirar (para o frontend saber quando renovar) */
    public Instant getExpiration() {
        return Instant.now().plusSeconds(7200); // 2 horas
    }
}
