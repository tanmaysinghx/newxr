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

/** A repository: files per branch, commits, pull requests, pipelines and deployments reported by CI. */
@Getter
@Setter
@Entity
@Table(name = "code_repos")
public class CodeRepo extends BaseEntity {
    /** Workspace shown before the name (northwind / orbit-mobile). */
    @Column(nullable = false, length = 40) private String namespace;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, unique = true, length = 80) private String slug;
    @Column(length = 300) private String description;
    @Column(nullable = false, length = 100) private String defaultBranch = "main";
    @Column(length = 30) private String language;
    @ManyToMany @JoinTable(name = "code_repo_watchers") private Set<User> watchers = new LinkedHashSet<>();
    private int prSeq;
    private int pipelineSeq;
    /** SHA-256 of the CI webhook token; null until one is generated. */
    @Column(unique = true, length = 64) private String ciTokenHash;
    @Column(length = 8) private String ciTokenHint;
}
