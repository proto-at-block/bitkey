package build.wallet.nfc.transaction

import build.wallet.auth.AccessToken
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.signChallenge
import build.wallet.nfc.transaction.SignAccessTokensAndAccountId.SignedAccessTokensAndAccountId

class SignAccessTokensAndAccountId(
  private val accessToken: AccessToken,
  private val fullAccountId: FullAccountId,
  private val success: suspend (SignedAccessTokensAndAccountId) -> Unit,
  private val failure: () -> Unit,
  override val isHardwareFake: Boolean,
  override val needsAuthentication: Boolean = true,
  // Don't lock because we quickly call [SignChallenge] next to get HW PoP
  override val shouldLock: Boolean = false,
) : NfcTransaction<SignedAccessTokensAndAccountId> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = SignedAccessTokensAndAccountId(
    signedAccessToken = commands.signAccessToken(session, accessToken),
    signedAccountId = commands.signChallenge(session, fullAccountId.serverId),
    hwAuthPublicKey = commands.getAuthenticationKey(session)
  )

  override suspend fun onSuccess(response: SignedAccessTokensAndAccountId) = success(response)

  override fun onCancel() = failure()

  data class SignedAccessTokensAndAccountId(
    val signedAccessToken: String,
    val signedAccountId: String,
    val hwAuthPublicKey: HwAuthPublicKey,
  )
}
