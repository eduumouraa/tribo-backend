package com.tribo.modules.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel de alunos inativos para o admin.
 *
 * GET /api/v1/admin/inactive          → lista de alunos sem atividade
 * GET /api/v1/admin/inactive/summary  → resumo de engajamento
 *
 * Usado pelo Lucas, Ezequiel, Deivis e David para contato proativo.
 */
@RestController
@RequestMapping("/api/v1/admin/inactive")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class InactiveStudentsController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Lista alunos inativos com filtro por dias sem atividade.
     * Padrão: sem atividade há mais de 14 dias.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listarInativos(
            @RequestParam(defaultValue = "14") int diasSemAtividade,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int page
    ) {
        int offset = page * size;

        List<Map<String, Object>> inativos = jdbcTemplate.queryForList("""
                SELECT
                    u.id,
                    u.name,
                    u.email,
                    u.created_at,
                    u.last_login_at,
                    COALESCE(MAX(lp.last_watched_at), u.created_at) as ultima_atividade,
                    EXTRACT(DAY FROM NOW() - COALESCE(MAX(lp.last_watched_at), u.created_at)) as dias_inativo,
                    COUNT(DISTINCT lp.lesson_id) as aulas_assistidas,
                    COUNT(DISTINCT CASE WHEN lp.is_completed = true THEN lp.lesson_id END) as aulas_concluidas,
                    s.status as assinatura_status,
                    s.plan as plano
                FROM users u
                LEFT JOIN lesson_progress lp ON lp.user_id = u.id
                LEFT JOIN subscriptions s ON s.user_id = u.id AND s.status = 'ACTIVE'
                WHERE u.role = 'STUDENT'
                  AND u.status = 'ACTIVE'
                GROUP BY u.id, u.name, u.email, u.created_at, u.last_login_at, s.status, s.plan
                HAVING EXTRACT(DAY FROM NOW() - COALESCE(MAX(lp.last_watched_at), u.created_at)) >= ?
                ORDER BY ultima_atividade ASC
                LIMIT ? OFFSET ?
                """, diasSemAtividade, size, offset);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT u.id
                    FROM users u
                    LEFT JOIN lesson_progress lp ON lp.user_id = u.id
                    WHERE u.role = 'STUDENT' AND u.status = 'ACTIVE'
                    GROUP BY u.id
                    HAVING EXTRACT(DAY FROM NOW() - COALESCE(MAX(lp.last_watched_at), u.created_at)) >= ?
                ) t
                """, Long.class, diasSemAtividade);

        Map<String, Object> result = new HashMap<>();
        result.put("students", inativos);
        result.put("total", total != null ? total : 0);
        result.put("page", page);
        result.put("size", size);
        result.put("diasSemAtividade", diasSemAtividade);

        return ResponseEntity.ok(result);
    }

    /**
     * Resumo de engajamento da plataforma — visão geral para o Lucas.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> resumoEngajamento() {
        Map<String, Object> summary = new HashMap<>();

        // Ativos hoje
        Long ativosHoje = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM lesson_progress WHERE last_watched_at >= NOW() - INTERVAL '24 hours'",
                Long.class
        );
        summary.put("activesToday", ativosHoje != null ? ativosHoje : 0);

        // Ativos essa semana
        Long ativosEssaSemana = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM lesson_progress WHERE last_watched_at >= NOW() - INTERVAL '7 days'",
                Long.class
        );
        summary.put("activeThisWeek", ativosEssaSemana != null ? ativosEssaSemana : 0);

        // Inativos há mais de 7 dias (com assinatura ativa)
        Long inativos7dias = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT u.id) FROM users u
                LEFT JOIN lesson_progress lp ON lp.user_id = u.id
                JOIN subscriptions s ON s.user_id = u.id AND s.status = 'ACTIVE'
                WHERE u.role = 'STUDENT'
                GROUP BY u.id
                HAVING COALESCE(MAX(lp.last_watched_at), u.created_at) < NOW() - INTERVAL '7 days'
                """, Long.class);
        summary.put("inactiveOver7Days", inativos7dias != null ? inativos7dias : 0);

        // Inativos há mais de 14 dias (com assinatura ativa) — prioridade para contato
        Long inativos14dias = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT u.id) FROM users u
                LEFT JOIN lesson_progress lp ON lp.user_id = u.id
                JOIN subscriptions s ON s.user_id = u.id AND s.status = 'ACTIVE'
                WHERE u.role = 'STUDENT'
                GROUP BY u.id
                HAVING COALESCE(MAX(lp.last_watched_at), u.created_at) < NOW() - INTERVAL '14 days'
                """, Long.class);
        summary.put("inactiveOver14Days", inativos14dias != null ? inativos14dias : 0);

        // Novos cadastros últimos 7 dias
        Long novosCadastros = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= NOW() - INTERVAL '7 days'",
                Long.class
        );
        summary.put("newUsersThisWeek", novosCadastros != null ? novosCadastros : 0);

        // Taxa de engajamento semanal
        Long totalComAssinatura = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM subscriptions WHERE status = 'ACTIVE'",
                Long.class
        );
        if (totalComAssinatura != null && totalComAssinatura > 0 && ativosEssaSemana != null) {
            int taxa = (int) Math.round((ativosEssaSemana * 100.0) / totalComAssinatura);
            summary.put("weeklyEngagementRate", taxa);
        } else {
            summary.put("weeklyEngagementRate", 0);
        }

        return ResponseEntity.ok(summary);
    }
}
