package build.wallet.fwup

/** Maps to [FwupFinishRspStatus] in core */
enum class FwupFinishResponseStatus {
  Unspecified,
  Success,
  SignatureInvalid,
  VersionInvalid,
  Error,
  WillApplyPatch,
  Unauthenticated,
}
