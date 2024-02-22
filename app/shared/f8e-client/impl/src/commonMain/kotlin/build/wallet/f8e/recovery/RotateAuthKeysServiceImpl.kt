package build.wallet.f8e.recovery

import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.catching
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8

class RotateAuthKeysServiceImpl(
  val f8eHttpClient: F8eHttpClient,
  val signer: AppAuthKeyMessageSigner,
) : RotateAuthKeysService {
  override suspend fun rotateKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    oldAppAuthPublicKey: AppAuthPublicKey,
    newAppAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Throwable> =
    binding {
      /**
       * This endpoint expects us to sign the account id with the private key associated
       * with the public key we're sending up
       */
      val accountIdByteString = fullAccountId.serverId.encodeUtf8()

      val signedAppGlobalAuthPublicKey = signer.signMessage(
        newAppAuthPublicKeys.appGlobalAuthPublicKey,
        accountIdByteString
      ).logFailure { "Couldn't sign app global auth key" }.bind()

      val signedAppRecoveryAuthPublicKey = signer.signMessage(
        newAppAuthPublicKeys.appRecoveryAuthPublicKey,
        accountIdByteString
      ).bind()

      f8eHttpClient.authenticated(
        f8eEnvironment,
        fullAccountId,
        hwFactorProofOfPossession = hwFactorProofOfPossession,
        appFactorProofOfPossessionAuthKey = oldAppAuthPublicKey
      )
        .catching {
          post("/api/accounts/${fullAccountId.serverId}/authentication-keys") {
            setBody(
              RotateAuthKeysetResponse(
                application = AuthenticationKey(
                  newAppAuthPublicKeys.appGlobalAuthPublicKey.pubKey.value,
                  signedAppGlobalAuthPublicKey
                ),
                hardware = AuthenticationKey(
                  hwAuthPublicKey.pubKey.value,
                  hwSignedAccountId
                ),
                recovery = AuthenticationKey(
                  newAppAuthPublicKeys.appRecoveryAuthPublicKey.pubKey.value,
                  signedAppRecoveryAuthPublicKey
                )
              )
            )
          }
        }.map { Unit }
        .logNetworkFailure { "Failed to send verification code during recovery" }
        .bind()
    }

  @Serializable
  data class AuthenticationKey(
    @SerialName("key")
    val key: String,
    @SerialName("signature")
    val signature: String,
  )

  @Serializable
  data class RotateAuthKeysetResponse(
    @SerialName("application")
    val application: AuthenticationKey,
    @SerialName("hardware")
    val hardware: AuthenticationKey,
    @SerialName("recovery")
    val recovery: AuthenticationKey,
  )
}
