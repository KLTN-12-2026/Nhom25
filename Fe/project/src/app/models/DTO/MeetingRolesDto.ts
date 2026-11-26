export type MeetingRole = 'HOST' | 'CO_HOST' | 'PARTICIPANT';
export type MeetingAction = 'TRANSFER_HOST' | 'SET_CO_HOST' | 'REVOKE_CO_HOST'
  | 'REMOVE' | 'MUTE' | 'REQUEST_UNMUTE' | 'STOP_SCREEN' | 'END';

export interface MeetingParticipant {
  userId: number;
  userName: string;
  displayName: string;
  googleAvatarUrl?: string | null;
  role: MeetingRole;
}

export interface MeetingRolesDto {
  meetingId: number;
  roomCode: string;
  creatorId: number;
  hostId: number;
  coHostId: number | null;
  version: number;
  ended: boolean;
  participants: MeetingParticipant[];
}

export interface MeetingRoleCommand {
  action: MeetingAction;
  targetUserId: number | null;
  version: number;
}
