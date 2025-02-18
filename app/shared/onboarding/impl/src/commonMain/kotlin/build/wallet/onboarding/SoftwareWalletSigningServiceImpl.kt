package build.wallet.onboarding

import build.wallet.account.AccountService
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.noiseKeyVariant
import build.wallet.f8e.signing.FrostTransactionSigningF8eClient
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.frost.FrostSignerFactory
import build.wallet.frost.SealedRequest
import build.wallet.frost.ShareDetails
import build.wallet.frost.UnsealedRequest
import build.wallet.frost.UnsealedResponse
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class SoftwareWalletSigningServiceImpl(
  private val accountService: AccountService,
  private val frostSignerFactory: FrostSignerFactory,
  private val frostTransactionSigningF8eClient: FrostTransactionSigningF8eClient,
  private val noiseService: NoiseService,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
) : SoftwareWalletSigningService {
  // TODO [W-10273]: Remove shareDetails parameter, and read from accountService instead.
  override suspend fun sign(
    psbt: Psbt,
    shareDetails: ShareDetails,
  ): Result<Psbt, Throwable> =
    coroutineBinding {
      val softwareWalletFeatureEnabled = softwareWalletIsEnabledFeatureFlag.isEnabled()
      ensure(softwareWalletFeatureEnabled) {
        Error("Software wallet feature flag is not enabled.")
      }
      val account = accountService.activeAccount().first()
      ensure(account is SoftwareAccount) { Error("No active software account found.") }

      val frostSigner = frostSignerFactory
        .create(
          psbt = psbt.base64,
          shareDetails = shareDetails
        ).result
        .bind()

      val unsealedRequest = UnsealedRequest(
        value = frostSigner.generateSealedSignPsbtRequest().result.value
      )

      val noiseSessionState = noiseService
        .establishNoiseSecureChannel(account.config.f8eEnvironment)
        .bind()
      val noiseInitiator = noiseService.getNoiseInitiator()

      val response = catchingResult {
        frostTransactionSigningF8eClient
          .getSealedPartialSignatures(
            f8eEnvironment = account.config.f8eEnvironment,
            softwareAccountId = account.accountId,
            noiseSessionId = noiseSessionState.sessionId,
            sealedRequest = SealedRequest(
              value = noiseInitiator.encryptMessage(
                keyType = account.config.f8eEnvironment.noiseKeyVariant,
                message = unsealedRequest.value.decodeBase64Bytes()
              ).encodeBase64()
            )
          ).bind()
      }.bind()

      val signedPsbtBase64 = catchingResult {
        frostSigner
          .signPsbt(
            unsealedResponse = UnsealedResponse(
              value = noiseInitiator.decryptMessage(
                keyType = account.config.f8eEnvironment.noiseKeyVariant,
                ciphertext = response.sealedResponse.decodeBase64Bytes()
              ).encodeBase64()
            )
          ).result.value
      }.bind()

      psbt.copy(base64 = signedPsbtBase64)
    }
}
