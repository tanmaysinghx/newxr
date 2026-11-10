package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "boards_cards")
public class Card extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private BoardList list;
    @Column(nullable = false, length = 255) private String title;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String description;
    /** Comma-separated label names; colours come from a fixed palette by name. */
    @Column(length = 300) private String labels;
    /** Cover: a colour or "from,to" gradient pair; null for none. */
    @Column(length = 40) private String cover;
    private Instant due;
    private boolean dueDone;
    private double position;
    private boolean archived;
    @Column(length = 24) private String linkedIssue;
    private Long linkedPage;
    @ManyToMany
    @JoinTable(name = "boards_card_members", joinColumns = @JoinColumn(name = "card_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new LinkedHashSet<>();
    @ElementCollection
    @CollectionTable(name = "boards_card_checklist", joinColumns = @JoinColumn(name = "card_id"))
    @OrderColumn(name = "position")
    private List<CardCheck> checklist = new ArrayList<>();

    public List<String> labelList() {
        return labels == null || labels.isBlank() ? List.of() : Arrays.stream(labels.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
