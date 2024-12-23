package build.wallet.f8e.client

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * This class creates and retains a single [HttpClient] for the
 * Unauthenticated F8e request context.
 */

@BitkeyInject(AppScope::class)
class UnauthenticatedOnlyF8eHttpClientImpl(
  appCoroutineScope: CoroutineScope,
  private val unauthenticatedF8eHttpClientFactory: UnauthenticatedF8eHttpClientFactory,
) : UnauthenticatedF8eHttpClient {
  private val httpClientFlow: Flow<HttpClient> = flow {
    emit(unauthenticatedF8eHttpClientFactory.createClient())
  }.shareIn(appCoroutineScope, SharingStarted.Lazily, replay = 1)

  override suspend fun unauthenticated(): HttpClient {
    return httpClientFlow.first()
  }
}
