package build.wallet.f8e.socrec

interface SocRecF8eClient :
  GetRecoveryRelationshipsF8eClient,
  CreateTrustedContactInvitationF8eClient,
  RefreshTrustedContactInvitationF8eClient,
  RemoveRecoveryRelationshipF8eClient,
  StartSocialChallengeF8eClient,
  GetSocialChallengeF8eClient,
  RetrieveTrustedContactInvitationF8eClient,
  AcceptTrustedContactInvitationF8eClient,
  VerifySocialChallengeF8eClient,
  EndorseTcsF8eClient
