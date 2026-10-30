package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_conversation_id", columnList = "conversation_id, id"))
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User sender;
    @Column(nullable = false, length = 4000, updatable = false)
    private String body;
    @Column(nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    protected Message() {}

    Message(User sender, Conversation conversation, String text) {
        if (conversation == null || !conversation.hasParticipant(sender)) {
            throw new IllegalArgumentException("Only conversation participants can send messages.");
        }
        if (conversation.getClosedAt() != null) throw new IllegalArgumentException("This conversation is closed.");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Enter a message before sending.");
        if (text.trim().length() > 4000) throw new IllegalArgumentException("Messages must be at most 4000 characters.");
        this.sender = sender;
        this.conversation = conversation;
        this.body = text.trim();
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
}
