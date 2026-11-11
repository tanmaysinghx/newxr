package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PulseTeamRepository extends JpaRepository<PulseTeam, Long> {
    Optional<PulseTeam> findByTeamKeyIgnoreCase(String key);
    Optional<PulseTeam> findByStatusSlugIgnoreCase(String slug);
    List<PulseTeam> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PulseTeam t where t.id = :id")
    Optional<PulseTeam> lockById(Long id);
}
