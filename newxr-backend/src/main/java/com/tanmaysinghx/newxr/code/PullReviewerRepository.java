package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface PullReviewerRepository extends JpaRepository<PullReviewer, Long> {
    List<PullReviewer> findByPullOrderByIdAsc(PullRequest pull);
    @Query("select r from PullReviewer r where r.user = :user and r.pull.status = 'OPEN'")
    List<PullReviewer> openFor(User user);
}
