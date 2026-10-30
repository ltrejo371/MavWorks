package com.mavworks.dto;

import java.time.Instant;

public record MessageSummary(Long id, Long senderId, String senderName, String body, Instant sentAt) {}
