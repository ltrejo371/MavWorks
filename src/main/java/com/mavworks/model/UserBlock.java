package com.mavworks.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Directed block; messaging checks both directions. */
@Entity
@org.hibernate.annotations.Check(constraints = "blocker_id <> blocked_id")
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(
        name = "user_blocks_pair_unique", columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocker;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocked;

    protected UserBlock() {}
    public UserBlock(User blocker, User blocked) {
        if (blocker == null || blocked == null || blocker.getId() == null || blocked.getId() == null
                || blocker.getId().equals(blocked.getId())) throw new IllegalArgumentException("Select another user to block.");
        this.blocker = blocker;
        this.blocked = blocked;
    }
}
