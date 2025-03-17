package build.wallet.f8e.relationships

/**
 * A client for creating relationships between accounts, used for social recovery and inheritance
 */
interface RelationshipsF8eClient :
  GetRelationshipsF8eClient,
  CreateTrustedContactInvitationF8eClient,
  RefreshTrustedContactInvitationF8eClient,
  RemoveRelationshipF8eClient,
  RetrieveTrustedContactInvitationF8eClient,
  AcceptTrustedContactInvitationF8eClient,
  EndorseTcsF8eClient,
  UploadSealedDelegatedDecryptionKeyF8eClient,
  GetSealedDelegatedDecryptionKeyF8eClient,
  RetrieveInvitationPromotionCodeF8eClient
