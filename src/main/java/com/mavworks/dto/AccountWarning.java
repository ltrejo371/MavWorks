package com.mavworks.dto;

import com.mavworks.model.ReportCategory;
import java.time.Instant;

/** Only the admin's explanation is exposed, never the reporter's identity or original allegation. */
public record AccountWarning(ReportCategory category, String explanation, Instant issuedAt) {}
