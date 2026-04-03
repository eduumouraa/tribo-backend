package com.tribo.modules.ranking.controller;

import com.tribo.modules.ranking.repository.MemberPointsRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "Ranking de membros por pontuação")
public class RankingController {

    private final MemberPointsRepository pointsRepository;
    private final UserRepository userRepository;

    @Operation(summary = "Top N membros por pontuação")
    @GetMapping
    public ResponseEntity<List<RankingEntry>> getTopRanking(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User currentUser
    ) {
        List<Object[]> rows = pointsRepository.findTopRanking();

        List<RankingEntry> result = new ArrayList<>();
        int position = 1;

        for (Object[] row : rows) {
            if (position > limit) break;
            UUID userId = (UUID) row[0];
            long totalPoints = ((Number) row[1]).longValue();

            userRepository.findById(userId).ifPresent(user ->
                result.add(new RankingEntry(
                    position,
                    userId.toString(),
                    user.getName(),
                    user.getAvatarUrl(),
                    totalPoints
                ))
            );
            position++;
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Pontuação do usuário autenticado")
    @GetMapping("/me")
    public ResponseEntity<MyRankingResponse> getMyRanking(
            @AuthenticationPrincipal User currentUser
    ) {
        long totalPoints = pointsRepository.sumByUserId(currentUser.getId());

        List<Object[]> rows = pointsRepository.findTopRanking();
        int position = 1;
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            if (userId.equals(currentUser.getId())) break;
            position++;
        }

        return ResponseEntity.ok(new MyRankingResponse(position, totalPoints));
    }

    public record RankingEntry(
            int position,
            String userId,
            String name,
            String avatarUrl,
            long totalPoints
    ) {}

    public record MyRankingResponse(int position, long totalPoints) {}
}
