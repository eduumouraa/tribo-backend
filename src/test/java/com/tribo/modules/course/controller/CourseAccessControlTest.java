package com.tribo.modules.course.controller;

import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.service.CourseService;
import com.tribo.modules.course.service.VideoStreamService;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.PlanAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseController — controle de acesso por plano")
class CourseAccessControlTest {

    @Mock CourseService courseService;
    @Mock VideoStreamService videoStreamService;
    @Mock SubscriptionService subscriptionService;
    @Mock CourseRepository courseRepository;
    @InjectMocks CourseController courseController;

    private UUID lessonId;
    private User studentUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        lessonId = UUID.randomUUID();

        studentUser = User.builder()
                .name("João Silva")
                .email("joao@test.com")
                .role(User.Role.STUDENT)
                .status(User.AccountStatus.ACTIVE)
                .build();

        adminUser = User.builder()
                .name("Admin")
                .email("admin@test.com")
                .role(User.Role.ADMIN)
                .status(User.AccountStatus.ACTIVE)
                .build();
    }

    // ── findBySlug — campos hasAccess e upgradeUrl ───────────────

    @Test
    @DisplayName("findBySlug: usuário com plano correto recebe hasAccess=true e upgradeUrl=null")
    void findBySlug_comAcessoCorreto() {
        var course = courseTribo();
        when(courseService.findBySlug("tribo-do-investidor")).thenReturn(course);
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "tribo")).thenReturn(true);
        when(courseRepository.countPublishedLessons(any())).thenReturn(10);
        when(courseRepository.sumPublishedDuration(any())).thenReturn(3600);

        var response = courseController.findBySlug("tribo-do-investidor", studentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().hasAccess()).isTrue();
        assertThat(response.getBody().requiredPlan()).isNull();
        assertThat(response.getBody().upgradeUrl()).isNull();
    }

    @Test
    @DisplayName("findBySlug: usuário sem plano recebe hasAccess=false e upgradeUrl preenchida")
    void findBySlug_semAcesso_retornaUpgradeUrl() {
        var course = courseTribo();
        when(courseService.findBySlug("tribo-do-investidor")).thenReturn(course);
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "tribo")).thenReturn(false);
        when(courseRepository.countPublishedLessons(any())).thenReturn(10);
        when(courseRepository.sumPublishedDuration(any())).thenReturn(3600);

        var response = courseController.findBySlug("tribo-do-investidor", studentUser);

        assertThat(response.getBody().hasAccess()).isFalse();
        assertThat(response.getBody().requiredPlan()).isEqualTo("tribo");
        assertThat(response.getBody().upgradeUrl()).isEqualTo("/checkout?plan=tribo");
    }

    @Test
    @DisplayName("findBySlug: ADMIN acessa qualquer curso independente do plano")
    void findBySlug_adminAcessaSemVerificarPlano() {
        var course = courseFinancas();
        when(courseService.findBySlug("organizacao-financeira")).thenReturn(course);
        when(courseRepository.countPublishedLessons(any())).thenReturn(5);
        when(courseRepository.sumPublishedDuration(any())).thenReturn(1800);

        var response = courseController.findBySlug("organizacao-financeira", adminUser);

        assertThat(response.getBody().hasAccess()).isTrue();
        // Admin nunca consulta o subscriptionService
        verify(subscriptionService, never()).hasAccessToCourse(any(), any());
    }

    @Test
    @DisplayName("findBySlug: plano financas bloqueado para usuário com plano tribo")
    void findBySlug_triboNaoPodeAcessarFinancas() {
        var course = courseFinancas();
        when(courseService.findBySlug("organizacao-financeira")).thenReturn(course);
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "financas")).thenReturn(false);
        when(courseRepository.countPublishedLessons(any())).thenReturn(5);
        when(courseRepository.sumPublishedDuration(any())).thenReturn(1800);

        var response = courseController.findBySlug("organizacao-financeira", studentUser);

        assertThat(response.getBody().hasAccess()).isFalse();
        assertThat(response.getBody().upgradeUrl()).isEqualTo("/checkout?plan=financas");
    }

    @Test
    @DisplayName("findBySlug: usuário não autenticado não acessa curso pago")
    void findBySlug_naoAutenticadoSemAcesso() {
        var course = courseTribo();
        when(courseService.findBySlug("tribo-do-investidor")).thenReturn(course);
        when(courseRepository.countPublishedLessons(any())).thenReturn(10);
        when(courseRepository.sumPublishedDuration(any())).thenReturn(3600);

        var response = courseController.findBySlug("tribo-do-investidor", null);

        assertThat(response.getBody().hasAccess()).isFalse();
    }

    // ── getStreamUrl — PlanAccessException ──────────────────────

    @Test
    @DisplayName("getStreamUrl: lança PlanAccessException se usuário não tem plano")
    void getStreamUrl_semPlano_lancaExcecao() {
        when(courseRepository.findRequiredPlanByLessonId(lessonId)).thenReturn("tribo");
        when(courseRepository.findSlugByLessonId(lessonId)).thenReturn("tribo-do-investidor");
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "tribo")).thenReturn(false);

        assertThatThrownBy(() -> courseController.getStreamUrl(lessonId, studentUser))
                .isInstanceOf(PlanAccessException.class)
                .hasMessageContaining("plano")
                .extracting(e -> ((PlanAccessException) e).getRequiredPlan())
                .isEqualTo("tribo");
    }

    @Test
    @DisplayName("getStreamUrl: lança exceção com courseSlug preenchido para upsell no frontend")
    void getStreamUrl_excecaoContemCourseSlug() {
        when(courseRepository.findRequiredPlanByLessonId(lessonId)).thenReturn("financas");
        when(courseRepository.findSlugByLessonId(lessonId)).thenReturn("organizacao-financeira");
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "financas")).thenReturn(false);

        assertThatThrownBy(() -> courseController.getStreamUrl(lessonId, studentUser))
                .isInstanceOf(PlanAccessException.class)
                .extracting(e -> ((PlanAccessException) e).getCourseSlug())
                .isEqualTo("organizacao-financeira");
    }

    @Test
    @DisplayName("getStreamUrl: aula free (requiredPlan=null) passa sem verificar assinatura")
    void getStreamUrl_aulaFreePassaSemVerificacao() {
        when(courseRepository.findRequiredPlanByLessonId(lessonId)).thenReturn(null);
        when(courseService.generateStreamUrl(eq(lessonId), any())).thenReturn("https://stream.example.com/free");
        when(videoStreamService.getExpiration()).thenReturn(java.time.Instant.now().plusSeconds(3600));

        var response = courseController.getStreamUrl(lessonId, studentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(subscriptionService, never()).hasAccessToCourse(any(), any());
    }

    @Test
    @DisplayName("getStreamUrl: ADMIN acessa qualquer aula sem verificar plano")
    void getStreamUrl_adminAcessaSemVerificacao() {
        when(courseService.generateStreamUrl(eq(lessonId), any())).thenReturn("https://stream.example.com/aula");
        when(videoStreamService.getExpiration()).thenReturn(java.time.Instant.now().plusSeconds(3600));

        var response = courseController.getStreamUrl(lessonId, adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(courseRepository, never()).findRequiredPlanByLessonId(any());
        verify(subscriptionService, never()).hasAccessToCourse(any(), any());
    }

    @Test
    @DisplayName("getStreamUrl: usuário com acesso correto recebe URL de stream")
    void getStreamUrl_comAcessoCorreto_retornaUrl() {
        when(courseRepository.findRequiredPlanByLessonId(lessonId)).thenReturn("tribo");
        when(subscriptionService.hasAccessToCourse(studentUser.getId(), "tribo")).thenReturn(true);
        when(courseService.generateStreamUrl(eq(lessonId), any())).thenReturn("https://stream.example.com/aula");
        when(videoStreamService.getExpiration()).thenReturn(java.time.Instant.now().plusSeconds(3600));

        var response = courseController.getStreamUrl(lessonId, studentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().streamUrl()).isEqualTo("https://stream.example.com/aula");
        assertThat(response.getBody().provider()).isEqualTo("panda");
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Course courseTribo() {
        Course c = Course.builder()
                .title("Tribo do Investidor")
                .slug("tribo-do-investidor")
                .requiredPlan("tribo")
                .status(Course.CourseStatus.PUBLISHED)
                .build();
        // Force a non-null ID so countPublishedLessons(c.getId()) doesn't NPE
        try {
            var field = Course.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(c, UUID.randomUUID());
        } catch (Exception ignored) {}
        return c;
    }

    private Course courseFinancas() {
        Course c = Course.builder()
                .title("Organização Financeira e Negociação de Dívidas")
                .slug("organizacao-financeira")
                .requiredPlan("financas")
                .status(Course.CourseStatus.PUBLISHED)
                .build();
        try {
            var field = Course.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(c, UUID.randomUUID());
        } catch (Exception ignored) {}
        return c;
    }
}
