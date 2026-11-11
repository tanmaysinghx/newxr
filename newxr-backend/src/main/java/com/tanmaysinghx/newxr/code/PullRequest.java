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


@Getter
@Setter
@Entity
@Table(name = "code_pulls", uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "number"}))
public class PullRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    private int number;
    @Column(nullable = false, length = 255) private String title;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String description;
    @Column(nullable = false, length = 100) private String sourceBranch;
    @Column(nullable = false, length = 100) private String targetBranch;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    /** OPEN, MERGED, DECLINED */
    @Column(nullable = false, length = 12) private String status = "OPEN";
    @ManyToOne(fetch = FetchType.LAZY) private User closedBy;
    private Instant closedAt;
    @Column(length = 40) private String mergeSha;
}
