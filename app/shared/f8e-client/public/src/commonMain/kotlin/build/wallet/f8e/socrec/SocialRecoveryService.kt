package build.wallet.f8e.socrec

interface SocialRecoveryService :
  GetRecoveryRelationshipsService,
  CreateTrustedContactInvitationService,
  RefreshTrustedContactInvitationService,
  RemoveRecoveryRelationshipService,
  StartSocialChallengeService,
  GetSocialChallengeService,
  RetrieveTrustedContactInvitationService,
  AcceptTrustedContactInvitationService,
  VerifySocialChallengeService
