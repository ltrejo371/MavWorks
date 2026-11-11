package com.mavworks.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** One available half hour; missing rows are unavailable. */
@Entity
@Table(name = "availability", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "available_date", "slot"}))
public class Availability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
    @Column(name = "available_date", nullable = false) private LocalDate date;
    @Column(nullable = false) private int slot;
    protected Availability() {}
    public Availability(User user, LocalDate date, int slot) { this.user = user; this.date = date; this.slot = slot; }
    public LocalDate getDate() { return date; }
    public int getSlot() { return slot; }
}
