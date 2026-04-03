package com.tribo.modules.ranking.repository;

import com.tribo.modules.ranking.entity.MemberPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemberPointsRepository extends JpaRepository<MemberPoints, UUID> {

    @Query("""
        SELECT mp.userId, SUM(mp.points)
        FROM MemberPoints mp
        GROUP BY mp.userId
        ORDER BY SUM(mp.points) DESC
        """)
    List<Object[]> findTopRanking();

    @Query("SELECT COALESCE(SUM(mp.points), 0) FROM MemberPoints mp WHERE mp.userId = :userId")
    long sumByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndReasonAndRefId(UUID userId, String reason, UUID refId);
}
