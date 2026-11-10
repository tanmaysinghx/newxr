package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pages_spaces")
public class Space extends BaseEntity {
    @Column(nullable = false, unique = true, length = 16) private String spaceKey;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 500) private String description;
    @Column(length = 9) private String color = "#0e9f8e";
    /** TEAM or PERSONAL. */
    @Column(length = 12) private String type = "TEAM";
    @ManyToOne(fetch = FetchType.LAZY) private User owner;
    /** Track project key shown as "Linked project". */
    @Column(length = 10) private String linkedProject;
    private boolean archived;
}
