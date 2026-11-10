package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pages_reactions", uniqueConstraints = @UniqueConstraint(name = "uq_reaction", columnNames = {"page_id", "userId", "emoji"}))
public class PageReaction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Page page;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 16) private String emoji;
}
