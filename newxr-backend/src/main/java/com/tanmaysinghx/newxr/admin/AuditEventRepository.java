package com.tanmaysinghx.newxr.admin;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable page);

    @Query("""
            select e from AuditEvent e
            where (:action = '' or e.action like concat(:action, '%'))
              and (:q = '' or lower(e.actorName) like concat('%', :q, '%') or lower(e.actorEmail) like concat('%', :q, '%')
                   or lower(e.target) like concat('%', :q, '%') or lower(e.detail) like concat('%', :q, '%'))
            order by e.createdAt desc""")
    List<AuditEvent> search(String q, String action, Pageable page);
}
