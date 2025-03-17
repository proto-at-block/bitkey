package build.wallet.onboarding

import bitkey.account.AccountConfigService
import build.wallet.crypto.NoiseInitiator
import build.wallet.crypto.NoiseSessionState
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.NoiseF8eClient
import build.wallet.f8e.auth.NoiseInitiateBundleRequest
import build.wallet.f8e.noiseKeyVariant
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.util.*

@BitkeyInject(AppScope::class)
class NoiseServiceImpl(
  private val noiseInitiator: NoiseInitiator,
  private val noiseF8eClient: NoiseF8eClient,
  private val accountConfigService: AccountConfigService,
) : NoiseService {
  override suspend fun establishNoiseSecureChannel(): Result<NoiseSessionState, Throwable> {
    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
    val rawRequest = noiseInitiator.initiateHandshake(f8eEnvironment.noiseKeyVariant)
    val body = NoiseInitiateBundleRequest(
      bundleBase64 = rawRequest.payload.encodeBase64(),
      serverStaticPubkeyBase64 = f8eEnvironment.noiseKeyVariant.serverStaticPubkey
    )

    return noiseF8eClient
      .initiateNoiseSecureChannel(f8eEnvironment, body)
      .map { noiseResponse ->
        noiseInitiator.advanceHandshake(
          f8eEnvironment.noiseKeyVariant,
          noiseResponse.bundleBase64.decodeBase64Bytes()
        )
        noiseInitiator.finalizeHandshake(f8eEnvironment.noiseKeyVariant)

        NoiseSessionState(
          sessionId = noiseResponse.noiseSessionId, // Server generates the id and we pass it along
          keyType = f8eEnvironment.noiseKeyVariant
        )
      }.logFailure { "Failed to establish Noise secure channel" }
  }

  override fun getNoiseInitiator(): NoiseInitiator = noiseInitiator
}
