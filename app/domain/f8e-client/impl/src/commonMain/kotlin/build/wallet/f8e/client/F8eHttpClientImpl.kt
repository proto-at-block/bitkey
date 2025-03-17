package build.wallet.f8e.client

import build.wallet.crypto.WsmVerifier
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class, boundTypes = [F8eHttpClient::class])
class F8eHttpClientImpl(
  private val authenticatedF8eHttpClient: AuthenticatedF8eHttpClient,
  private val unauthenticatedF8eHttpClient: UnauthenticatedF8eHttpClient,
  override val wsmVerifier: WsmVerifier,
) : F8eHttpClient,
  AuthenticatedF8eHttpClient by authenticatedF8eHttpClient,
  UnauthenticatedF8eHttpClient by unauthenticatedF8eHttpClient {
  companion object {
    const val CONSTANT_PROOF_OF_POSSESSION_APP_HEADER = "X-App-Signature"
    const val CONSTANT_PROOF_OF_POSSESSION_HW_HEADER = "X-Hw-Signature"
  }
}
