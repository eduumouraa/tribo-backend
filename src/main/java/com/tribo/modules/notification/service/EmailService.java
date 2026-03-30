package com.tribo.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Serviço de email transacional via Resend.
 *
 * Usa HTTP direto ao invés do SDK para evitar dependência extra.
 * Resiliente: falha de email nunca derruba o fluxo principal.
 *
 * Configurar no application.yml:
 *   resend.api-key: ${RESEND_API_KEY:}
 *   resend.from: Tribo Invest Play <noreply@triboinvest.com.br>
 */
@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from:Tribo Invest Play <noreply@triboinvest.com.br>}")
    private String from;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Emails de autenticação ────────────────────────────────────

    @Async
    public void enviarBoasVindas(String email, String nome) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#1D9E75;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Bem-vindo à Tribo! 🎉</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Sua conta foi criada com sucesso. Agora você tem acesso à plataforma de educação financeira da Tribo do Investidor.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="https://play.triboinvest.com.br" style="background:#1D9E75;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Acessar a Plataforma
                  </a>
                </div>
                <p style="font-size:13px;color:#999">Se você não criou esta conta, pode ignorar este email.</p>
              </div>
            </div>
            """.formatted(nome);

        enviar(email, "Bem-vindo à Tribo Invest Play! 🚀", html);
    }

    @Async
    public void enviarAcessoLiberado(String email, String nome, String plano) {
        String nomePlano = switch (plano) {
            case "financas" -> "Organização Financeira e Negociação de Dívidas";
            case "combo" -> "Tribo Completo (todos os cursos)";
            default -> "Tribo do Investidor";
        };

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#1D9E75;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Acesso Liberado! ✅</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Seu acesso ao <strong>%s</strong> foi liberado com sucesso.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="https://play.triboinvest.com.br" style="background:#1D9E75;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Começar a Estudar
                  </a>
                </div>
                <p style="font-size:13px;color:#999">Qualquer dúvida, entre em contato pelo WhatsApp da Tribo.</p>
              </div>
            </div>
            """.formatted(nome, nomePlano);

        enviar(email, "Seu acesso foi liberado! Comece a estudar agora 📚", html);
    }

    @Async
    public void enviarRecuperacaoSenha(String email, String nome, String token) {
        String link = "https://play.triboinvest.com.br/reset-password?token=" + token;

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#185FA5;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Recuperação de Senha</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Recebemos uma solicitação para redefinir sua senha.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="%s" style="background:#185FA5;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Redefinir Senha
                  </a>
                </div>
                <p style="font-size:13px;color:#999">Este link expira em 1 hora. Se você não solicitou, ignore este email.</p>
              </div>
            </div>
            """.formatted(nome, link);

        enviar(email, "Redefinição de senha — Tribo Invest Play", html);
    }

    @Async
    public void enviarPagamentoFalhou(String email, String nome) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#E24B4A;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Problema no Pagamento ⚠️</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Não conseguimos processar seu pagamento. Para não perder o acesso à plataforma, atualize seu cartão.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="https://play.triboinvest.com.br/perfil" style="background:#E24B4A;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Atualizar Cartão
                  </a>
                </div>
                <p style="font-size:13px;color:#999">Tentaremos novamente em breve. Em caso de dúvida, entre em contato.</p>
              </div>
            </div>
            """.formatted(nome);

        enviar(email, "Problema no pagamento — atualize seu cartão", html);
    }

    @Async
    public void enviarAssinaturaCancelada(String email, String nome) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#BA7517;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Assinatura Cancelada</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Sua assinatura foi cancelada. Você mantém acesso até o fim do período pago.</p>
                <p style="font-size:15px;color:#555">Se foi um engano ou mudou de ideia, fale com a gente.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="https://play.triboinvest.com.br" style="background:#BA7517;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Renovar Assinatura
                  </a>
                </div>
              </div>
            </div>
            """.formatted(nome);

        enviar(email, "Sua assinatura foi cancelada", html);
    }

    @Async
    public void enviarBoasVindasNovoPagante(String email, String nome, String plano, String resetToken) {
        String nomePlano = switch (plano) {
            case "financas" -> "Organização Financeira e Negociação de Dívidas";
            case "combo" -> "Tribo Completo (todos os cursos)";
            default -> "Tribo do Investidor";
        };
        String link = "https://play.triboinvest.com.br/reset-password?token=" + resetToken;

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px">
              <div style="background:#1D9E75;padding:24px;border-radius:12px 12px 0 0;text-align:center">
                <h1 style="color:#fff;margin:0;font-size:28px">Bem-vindo à Tribo! 🎉</h1>
              </div>
              <div style="background:#f9f9f9;padding:32px;border-radius:0 0 12px 12px">
                <p style="font-size:16px;color:#333">Olá, <strong>%s</strong>!</p>
                <p style="font-size:15px;color:#555">Seu acesso ao <strong>%s</strong> foi confirmado. Antes de começar, você precisa criar sua senha.</p>
                <div style="text-align:center;margin:32px 0">
                  <a href="%s" style="background:#1D9E75;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-size:16px;font-weight:bold">
                    Criar Minha Senha
                  </a>
                </div>
                <p style="font-size:13px;color:#999">Este link expira em 48 horas. Se você não realizou esta compra, entre em contato imediatamente.</p>
              </div>
            </div>
            """.formatted(nome, nomePlano, link);

        enviar(email, "Bem-vindo! Crie sua senha para acessar a Tribo 🚀", html);
    }

    // ── Core de envio ─────────────────────────────────────────────

    private void enviar(String para, String assunto, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY não configurada — email não enviado para {}", para);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", new String[]{para},
                    "subject", assunto,
                    "html", html
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails", request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email enviado para {} — assunto: {}", para, assunto);
            } else {
                log.warn("Falha ao enviar email para {} — status: {}", para, response.getStatusCode());
            }

        } catch (Exception e) {
            // Email nunca derruba o fluxo principal
            log.error("Erro ao enviar email para {}: {}", para, e.getMessage());
        }
    }
}
