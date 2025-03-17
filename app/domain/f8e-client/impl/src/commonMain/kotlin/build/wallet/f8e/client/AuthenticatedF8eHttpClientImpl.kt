package build.wallet.f8e.client

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async

/**
 * This class creates and retains a single [HttpClient] for the
 * Authenticated F8e request context.
 */
@BitkeyInject(AppScope::class)
class AuthenticatedF8eHttpClientImpl(
  appCoroutineScope: CoroutineScope,
  private val authenticatedF8eHttpClientFactory: AuthenticatedF8eHttpClientFactory,
) : AuthenticatedF8eHttpClient {
  private val httpClient = appCoroutineScope.async(Dispatchers.IO, start = LAZY) {
    authenticatedF8eHttpClientFactory.createClient()
  }

  override suspend fun authenticated(): HttpClient {
    return httpClient.await()
  }
}
