package build.wallet.f8e.client

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
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
  private val httpClient = appCoroutineScope.async(Dispatchers.IO, start = LAZY) {
    unauthenticatedF8eHttpClientFactory.createClient()
  }

  override suspend fun unauthenticated(): HttpClient {
    return httpClient.await()
  }
}
