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
@Table(name = "code_commits", indexes = @Index(name = "ix_commit_repo_branch", columnList = "repo_id,branch"))
public class Commit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    @Column(nullable = false, length = 40) private String sha;
    @Column(nullable = false, length = 100) private String branch;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String message;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    private int additions;
    private int deletions;
    @Column(length = 400) private String paths;
}
