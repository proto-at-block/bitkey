package build.wallet.nfc.transaction

import build.wallet.auth.AccessToken
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.signChallenge
import build.wallet.nfc.transaction.SignAccountIdAndAuthData.SignedAccountIdAndAuthData

/**
 * NFC transaction that signs: [fullAccountId], [accessToken], [appAuthGlobalAuthPublicKey]
 */
class SignAccountIdAndAuthData(
  private val appAuthGlobalAuthPublicKey: AppGlobalAuthPublicKey,
  private val accessToken: AccessToken,
  private val fullAccountId: FullAccountId,
  private val success: suspend (SignedAccountIdAndAuthData) -> Unit,
  private val failure: () -> Unit,
  override val isHardwareFake: Boolean,
  override val needsAuthentication: Boolean = true,
  // Don't lock because we quickly call [SignChallenge] next to get HW PoP
  override val shouldLock: Boolean = false,
) : NfcTransaction<SignedAccountIdAndAuthData> {
  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = SignedAccountIdAndAuthData(
    appGlobalAuthKeyHwSignature =
      commands
        .signChallenge(session, appAuthGlobalAuthPublicKey.pubKey.value)
        .let(::AppGlobalAuthKeyHwSignature),
    signedAccessToken = commands.signAccessToken(session, accessToken),
    signedAccountId = commands.signChallenge(session, fullAccountId.serverId),
    hwAuthPublicKey = commands.getAuthenticationKey(session)
  )

  override suspend fun onSuccess(response: SignedAccountIdAndAuthData) = success(response)

  override fun onCancel() = failure()

  data class SignedAccountIdAndAuthData(
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    val signedAccessToken: String,
    val signedAccountId: String,
    val hwAuthPublicKey: HwAuthPublicKey,
  )
}
