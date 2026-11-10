package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "desk_requests", indexes = @Index(name = "ix_request_desk", columnList = "desk_id,status"))
public class ServiceRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ServiceDesk desk;
    @ManyToOne(fetch = FetchType.LAZY) private RequestType type;
    @Column(nullable = false, unique = true, length = 24) private String requestKey;
    @Column(nullable = false, length = 255) private String summary;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String description;
    @ManyToOne(fetch = FetchType.LAZY) private User reporter;
    @ManyToOne(fetch = FetchType.LAZY) private User assignee;
    /** WAITING_FOR_SUPPORT, IN_PROGRESS, WAITING_FOR_CUSTOMER, ESCALATED, RESOLVED, CLOSED. */
    @Column(nullable = false, length = 24) private String status = "WAITING_FOR_SUPPORT";
    /** P1..P4 */
    @Column(nullable = false, length = 2) private String priority = "P3";
    @Column(length = 80) private String organization;
    @Column(length = 60) private String component;
    @Column(length = 40) private String linked;
    private Instant firstResponseDue;
    private Instant resolutionDue;
    private Instant firstRespondedAt;
    private Instant resolvedAt;
    private Integer csat;
}
