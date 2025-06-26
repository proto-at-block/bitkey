package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.privilegedactions.Authorization
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.CancelPrivilegedActionRequest
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionInstanceRef
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureUtils
import build.wallet.grants.GRANT_SIGNATURE_LEN
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class FingerprintResetServiceImpl(
  override val privilegedActionF8eClient: FingerprintResetF8eClient,
  override val accountService: AccountService,
  private val signatureUtils: SignatureUtils,
) : FingerprintResetService {
  /**
   * Create a fingerprint reset privileged action using a GrantRequest
   */
  override suspend fun createFingerprintResetPrivilegedAction(
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError> {
    if (grantRequest.action != GrantAction.FINGERPRINT_RESET) {
      return Err(PrivilegedActionError.UnsupportedActionType)
    }

    return accountService.getAccount<FullAccount>()
      .mapError { accountError ->
        PrivilegedActionError.IncorrectAccountType
      }
      .flatMap { account ->
        val hwAuthPublicKey = account.keybox.activeHwKeyBundle.authKey

        val derEncodedSignature = try {
          derEncodedSignature(grantRequest.signature)
        } catch (e: IllegalArgumentException) {
          return@flatMap Err(PrivilegedActionError.InvalidRequest(e))
        }

        val request = FingerprintResetRequest(
          version = grantRequest.version.toInt(),
          action = grantRequest.action.value,
          deviceId = grantRequest.deviceId.toByteString().base64(),
          challenge = grantRequest.challenge.toByteString().base64(),
          signature = derEncodedSignature.base64(),
          hwAuthPublicKey = hwAuthPublicKey.pubKey.value
        )
        createAction(request)
      }
  }

  override suspend fun completeFingerprintResetAndGetGrant(
    actionId: String,
  ): Result<Grant, PrivilegedActionError> {
    return accountService.getAccount<FullAccount>()
      .mapError { err: Error ->
        PrivilegedActionError.IncorrectAccountType
      }
      .flatMap { account: FullAccount ->
        privilegedActionF8eClient.getPrivilegedActionInstances(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId
        ).mapError { throwable: Throwable -> PrivilegedActionError.ServerError(throwable) }
          .flatMap { instances: List<PrivilegedActionInstance> ->
            val actionInstance = instances.find { it.id == actionId }
              ?: return@flatMap Err(PrivilegedActionError.NotAuthorized)

            val completionToken = (
              actionInstance
                .takeIf { it.privilegedActionType == PrivilegedActionType.RESET_FINGERPRINT }
                ?.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
            )
              ?.completionToken
              ?: return@flatMap Err(PrivilegedActionError.UnsupportedActionType)

            val auth = Authorization(
              authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
              completionToken = completionToken
            )

            val actionRef = PrivilegedActionInstanceRef(
              id = actionInstance.id,
              authorizationStrategy = auth
            )

            val request = ContinuePrivilegedActionRequest(
              privilegedActionInstance = actionRef
            )

            privilegedActionF8eClient.continuePrivilegedAction(
              f8eEnvironment = account.config.f8eEnvironment,
              fullAccountId = account.accountId,
              request = request
            ).mapError { throwable: Throwable -> PrivilegedActionError.ServerError(throwable) }
              .flatMap { fingerprintResetResponse ->
                try {
                  Ok(
                    Grant(
                      version = fingerprintResetResponse.version.toByte(),
                      serializedRequest = fingerprintResetResponse.serializedRequest.decodeHex().toByteArray(),
                      signature = fingerprintResetResponse.signature.decodeHex().toByteArray()
                    )
                  )
                } catch (e: IllegalArgumentException) {
                  Err(PrivilegedActionError.InvalidResponse(e))
                }
              }
          }
      }
  }

  override suspend fun cancelFingerprintReset(
    cancellationToken: String,
  ): Result<Unit, PrivilegedActionError> {
    return accountService.getAccount<FullAccount>()
      .mapError { accountError ->
        PrivilegedActionError.IncorrectAccountType
      }
      .flatMap { account ->
        privilegedActionF8eClient.cancelFingerprintReset(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId,
          request = CancelPrivilegedActionRequest(cancellationToken = cancellationToken)
        ).mapError { error ->
          PrivilegedActionError.ServerError(error)
        }
      }.flatMap { Ok(Unit) }
  }

  override suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError> {
    return getPrivilegedActionsByType(PrivilegedActionType.RESET_FINGERPRINT)
      .flatMap { actions ->
        // There should be at most one pending action for fingerprint reset
        when (actions.size) {
          0 -> Ok(null)
          1 -> Ok(actions.first().instance)
          else -> Err(PrivilegedActionError.MultiplePendingActionsFound)
        }
      }
  }

  private fun derEncodedSignature(signature: ByteArray): ByteString {
    require(signature.size == GRANT_SIGNATURE_LEN) {
      "Invalid signature length: expected $GRANT_SIGNATURE_LEN bytes, got ${signature.size}"
    }

    return signatureUtils.encodeSignatureToDer(signature)
  }
}
