package build.wallet.f8e.recovery

import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import io.ktor.client.request.post
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
    oldAppAuthPublicKey: PublicKey<AppGlobalAuthKey>,
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

      f8eHttpClient
        .authenticated(
          f8eEnvironment,
          fullAccountId,
          hwFactorProofOfPossession = hwFactorProofOfPossession,
          appFactorProofOfPossessionAuthKey = oldAppAuthPublicKey
        )
        .catching {
          post("/api/accounts/${fullAccountId.serverId}/authentication-keys") {
            withDescription("Rotating auth keys")
            setRedactedBody(
              RotateAuthKeysetResponse(
                application = AuthenticationKey(
                  newAppAuthPublicKeys.appGlobalAuthPublicKey.value,
                  signedAppGlobalAuthPublicKey
                ),
                hardware = AuthenticationKey(
                  hwAuthPublicKey.pubKey.value,
                  hwSignedAccountId
                ),
                recovery = AuthenticationKey(
                  newAppAuthPublicKeys.appRecoveryAuthPublicKey.value,
                  signedAppRecoveryAuthPublicKey
                )
              )
            )
          }
        }
        .mapUnit()
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
  ) : RedactedRequestBody
}
