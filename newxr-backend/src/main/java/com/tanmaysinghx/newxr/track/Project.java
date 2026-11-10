package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track_projects")
public class Project extends BaseEntity {

    /** ORB, HELP... 2–10 upper-case letters/digits, starting with a letter. */
    @Column(nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectType type = ProjectType.SOFTWARE;

    @ManyToOne(fetch = FetchType.LAZY)
    private User lead;

    @Column(length = 9)
    private String color = "#5a4bf5";

    /** Next issue number; incremented under a pessimistic lock when an issue is created. */
    private int issueSeq;

    private boolean archived;
}
