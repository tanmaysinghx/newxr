package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A CI run reported through the repository webhook. Newxr does not run builds itself. */
@Getter
@Setter
@Entity
@Table(name = "code_pipelines", uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "number"}))
public class Pipeline extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    private int number;
    @Column(length = 64) private String externalId;
    @Column(nullable = false, length = 100) private String branch;
    @Column(length = 40) private String commitSha;
    @Column(length = 255) private String commitMessage;
    /** QUEUED, RUNNING, PASSED, FAILED, STOPPED */
    @Column(nullable = false, length = 10) private String status = "RUNNING";
    @ManyToOne(fetch = FetchType.LAZY) private User triggeredBy;
    @Column(length = 120) private String triggeredByName;
    @Column(length = 400) private String url;
    private Instant startedAt;
    private Instant finishedAt;
}
