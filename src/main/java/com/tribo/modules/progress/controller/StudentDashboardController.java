package com.tribo.modules.progress.controller;

import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard individual do aluno.
 *
 * GET /api/v1/student/dashboard
 *
 * Retorna:
 * - horas assistidas total
 * - aulas concluídas
 * - cursos em andamento
 * - streak atual (dias consecutivos estudando)
 * - conquistas desbloqueadas
 * - posição no ranking semanal
 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardController {

    private final ProgressRepository progressRepository;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal User currentUser
    ) {
        Map<String, Object> dashboard = new HashMap<>();
        String userId = currentUser.getId().toString();

        // Total de horas assistidas
        Long totalSeconds = progressRepository.sumWatchedSeconds(currentUser.getId());
        long totalHours = (totalSeconds != null ? totalSeconds : 0) / 3600;
        long totalMinutes = ((totalSeconds != null ? totalSeconds : 0) % 3600) / 60;
        dashboard.put("totalWatchedSeconds", totalSeconds != null ? totalSeconds : 0);
        dashboard.put("totalWatchedFormatted", totalHours + "h " + totalMinutes + "min");

        // Total de aulas concluídas
        Long aulasConcluideas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_progress WHERE user_id = ?::uuid AND is_completed = true",
                Long.class, userId
        );
        dashboard.put("totalLessonsCompleted", aulasConcluideas != null ? aulasConcluideas : 0);

        // Cursos em andamento (tem progresso mas não concluiu 100%)
        List<Map<String, Object>> cursosEmAndamento = jdbcTemplate.queryForList("""
                SELECT c.id, c.title, c.slug, c.thumbnail_url,
                       COUNT(DISTINCT l.id) as total_lessons,
                       COUNT(DISTINCT CASE WHEN lp.is_completed = true THEN lp.lesson_id END) as completed_lessons,
                       MAX(lp.last_watched_at) as last_watched_at
                FROM courses c
                JOIN modules m ON m.course_id = c.id
                JOIN lessons l ON l.module_id = m.id
                LEFT JOIN lesson_progress lp ON lp.lesson_id = l.id AND lp.user_id = ?::uuid
                WHERE c.status = 'PUBLISHED'
                  AND lp.user_id IS NOT NULL
                GROUP BY c.id, c.title, c.slug, c.thumbnail_url
                HAVING COUNT(DISTINCT CASE WHEN lp.is_completed = true THEN lp.lesson_id END) > 0
                ORDER BY MAX(lp.last_watched_at) DESC
                LIMIT 5
                """, userId);

        // Calcula % de progresso para cada curso
        cursosEmAndamento.forEach(curso -> {
            long total = ((Number) curso.get("total_lessons")).longValue();
            long concluidas = ((Number) curso.get("completed_lessons")).longValue();
            int percent = total > 0 ? (int) Math.round((concluidas * 100.0) / total) : 0;
            curso.put("percentComplete", percent);
        });
        dashboard.put("coursesInProgress", cursosEmAndamento);

        // Streak atual (dias consecutivos com atividade)
        int streak = calcularStreak(userId);
        dashboard.put("streakDays", streak);

        // Conquistas desbloqueadas
        List<Map<String, Object>> conquistas = jdbcTemplate.queryForList(
                "SELECT type, metadata, earned_at FROM achievements WHERE user_id = ?::uuid ORDER BY earned_at DESC",
                userId
        );
        dashboard.put("achievements", conquistas);
        dashboard.put("totalAchievements", conquistas.size());

        // Posição no ranking semanal (por aulas concluídas nos últimos 7 dias)
        Integer posicaoRanking = jdbcTemplate.queryForObject("""
                SELECT ranking FROM (
                  SELECT user_id,
                         RANK() OVER (ORDER BY COUNT(*) DESC) as ranking
                  FROM lesson_progress
                  WHERE is_completed = true
                    AND last_watched_at >= NOW() - INTERVAL '7 days'
                  GROUP BY user_id
                ) r WHERE user_id = ?::uuid
                """, Integer.class, userId);
        dashboard.put("weeklyRankingPosition", posicaoRanking);

        // Aulas concluídas essa semana
        Long aulasEssaSemana = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_progress WHERE user_id = ?::uuid AND is_completed = true AND last_watched_at >= NOW() - INTERVAL '7 days'",
                Long.class, userId
        );
        dashboard.put("lessonsCompletedThisWeek", aulasEssaSemana != null ? aulasEssaSemana : 0);

        log.debug("Dashboard carregado para userId={}", currentUser.getId());
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Calcula streak de dias consecutivos estudando.
     * Um dia conta se o aluno assistiu pelo menos 1 aula.
     */
    private int calcularStreak(String userId) {
        try {
            List<String> dias = jdbcTemplate.queryForList("""
                    SELECT DISTINCT DATE(last_watched_at AT TIME ZONE 'America/Fortaleza') as dia
                    FROM lesson_progress
                    WHERE user_id = ?::uuid
                      AND last_watched_at >= NOW() - INTERVAL '60 days'
                    ORDER BY dia DESC
                    """, String.class, userId);

            if (dias.isEmpty()) return 0;

            int streak = 0;
            java.time.LocalDate hoje = java.time.LocalDate.now();

            for (String diaStr : dias) {
                java.time.LocalDate dia = java.time.LocalDate.parse(diaStr);
                java.time.LocalDate esperado = hoje.minusDays(streak);

                if (dia.equals(esperado) || (streak == 0 && dia.equals(hoje.minusDays(1)))) {
                    streak++;
                    if (streak == 0 && dia.equals(hoje.minusDays(1))) hoje = hoje.minusDays(1);
                } else {
                    break;
                }
            }

            return streak;
        } catch (Exception e) {
            log.warn("Erro ao calcular streak para userId={}: {}", userId, e.getMessage());
            return 0;
        }
    }
}
