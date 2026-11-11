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

/** A file on one branch. {@code baseHash} is the content hash when the branch was forked (null: new on this branch). */
@Getter
@Setter
@Entity
@Table(name = "code_files", uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "branch", "path"}))
public class RepoFile extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepo repo;
    @Column(nullable = false, length = 100) private String branch;
    @Column(nullable = false, length = 400) private String path;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String content;
    @Column(nullable = false, length = 64) private String hash;
    @Column(length = 64) private String baseHash;
    @Column(length = 40) private String lastCommitSha;
    @Column(length = 255) private String lastCommitMessage;
    private Instant lastCommitAt;
}
