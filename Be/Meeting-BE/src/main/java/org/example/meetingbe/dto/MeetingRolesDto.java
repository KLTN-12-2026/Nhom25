package org.example.meetingbe.dto;

import java.util.List;

public record MeetingRolesDto(Long meetingId, String roomCode, Long creatorId,
                              Long hostId, Long coHostId, long version, boolean ended,
                              List<Member> participants) {
    public record Member(Long userId, String userName, String displayName, String role) {}
}
