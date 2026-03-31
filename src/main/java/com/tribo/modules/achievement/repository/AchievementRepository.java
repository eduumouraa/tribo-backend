package com.tribo.modules.achievement.repository;

import com.tribo.modules.achievement.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    List<Achievement> findByUserIdOrderByEarnedAtDesc(UUID userId);

    boolean existsByUserIdAndType(UUID userId, String type);
}
