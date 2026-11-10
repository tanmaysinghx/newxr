package com.tanmaysinghx.newxr.desk;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {
    List<RequestComment> findByRequestOrderByCreatedAtAsc(ServiceRequest request);
}
