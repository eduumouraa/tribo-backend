package com.tribo.shared.exception;

import lombok.Getter;

/**
 * Lançada quando o usuário tenta acessar um curso cujo plano não cobre.
 * Retornada como HTTP 403 com informações para o frontend exibir o upsell.
 */
@Getter
public class PlanAccessException extends RuntimeException {

    private final String requiredPlan;
    private final String courseSlug;

    public PlanAccessException(String requiredPlan, String courseSlug) {
        super("Este conteúdo requer o plano " + planLabel(requiredPlan) + ".");
        this.requiredPlan = requiredPlan;
        this.courseSlug = courseSlug;
    }

    private static String planLabel(String plan) {
        return switch (plan) {
            case "financas" -> "Organização Financeira e Negociação de Dívidas";
            case "combo"    -> "Tribo Completo";
            default         -> "Tribo do Investidor";
        };
    }
}
