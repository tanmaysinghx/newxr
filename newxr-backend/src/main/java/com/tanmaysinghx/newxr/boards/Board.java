package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "boards_boards")
public class Board extends BaseEntity {
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 500) private String description;
    /** CSS background: a colour or "from,to" gradient pair. */
    @Column(length = 40) private String background = "#7c3aed,#d9466f";
    /** WORKSPACE (everyone) or PRIVATE (members only). */
    @Column(length = 12) private String visibility = "WORKSPACE";
    @ManyToOne(fetch = FetchType.LAZY) private User owner;
    @ManyToMany
    @JoinTable(name = "boards_board_members", joinColumns = @JoinColumn(name = "board_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new LinkedHashSet<>();
    private boolean archived;
}
