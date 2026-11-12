package org.example.meetingbe.dto;

import jakarta.validation.constraints.NotNull;

public record MeetingRoleRequest(Long targetUserId, @NotNull Long version) {}
