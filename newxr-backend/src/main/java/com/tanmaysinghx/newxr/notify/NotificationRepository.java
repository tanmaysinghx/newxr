package com.tanmaysinghx.newxr.notify;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable page);

    long countByRecipientIdAndReadFlagFalse(Long recipientId);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.recipientId = :recipientId and n.readFlag = false")
    int markAllRead(Long recipientId);
}
