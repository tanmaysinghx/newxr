package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    Optional<ServiceRequest> findByRequestKeyIgnoreCase(String key);
    List<ServiceRequest> findByDeskOrderByCreatedAtDesc(ServiceDesk desk);
    List<ServiceRequest> findByReporterOrderByCreatedAtDesc(User reporter);
    List<ServiceRequest> findByDeskAndReporter(ServiceDesk desk, User reporter);
    long countByDeskAndStatusNotIn(ServiceDesk desk, List<String> statuses);
    long countByReporterAndStatusNotIn(User reporter, List<String> statuses);
    long countByType(RequestType type);
    List<ServiceRequest> findByReporterOrderByCreatedAtDesc(User reporter, Pageable page);
    List<ServiceRequest> findByAssigneeAndStatusNotInOrderByUpdatedAtDesc(User assignee, List<String> statuses);
    List<ServiceRequest> findByTypeAndStatusInAndIdNotOrderByResolvedAtDesc(RequestType type, List<String> statuses, Long id, Pageable page);

    @Query("select r from ServiceRequest r where lower(r.summary) like lower(concat('%', :q, '%')) or r.requestKey = :key order by r.updatedAt desc")
    List<ServiceRequest> search(String q, String key, Pageable page);
}
