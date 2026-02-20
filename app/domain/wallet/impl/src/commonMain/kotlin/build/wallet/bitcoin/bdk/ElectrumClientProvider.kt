package build.wallet.bitcoin.bdk

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState.BACKGROUND
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uniffi.bdk.ElectrumClient

@BitkeyInject(AppScope::class)
class ElectrumClientProviderImpl(
  appCoroutineScope: CoroutineScope,
  appSessionManager: AppSessionManager,
) : ElectrumClientProvider {
  private val cacheLock = ReentrantLock()
  private var cachedElectrumClient: ElectrumClient? = null
  private var cachedElectrumUrl: String? = null

  init {
    appSessionManager.appSessionState
      .onEach { sessionState ->
        if (sessionState == BACKGROUND) {
          clearCache()
        }
      }
      .launchIn(appCoroutineScope)
  }

  override fun <T> withClient(
    url: String,
    block: (ElectrumClient) -> T,
  ): T =
    cacheLock.withLock {
      val client = getOrCreateLocked(url)
      block(client)
    }

  override fun invalidate(url: String) {
    cacheLock.withLock {
      if (cachedElectrumUrl == url) {
        clearCacheLocked()
      }
    }
  }

  private fun clearCache() {
    cacheLock.withLock {
      clearCacheLocked()
    }
  }

  private fun clearCacheLocked() {
    cachedElectrumClient?.close()
    cachedElectrumClient = null
    cachedElectrumUrl = null
  }

  private fun getOrCreateLocked(url: String): ElectrumClient {
    val cachedClient = cachedElectrumClient
    if (cachedClient != null && cachedElectrumUrl == url) {
      return cachedClient
    }

    cachedElectrumClient?.close()
    val client = ElectrumClient(url)
    cachedElectrumClient = client
    cachedElectrumUrl = url
    return client
  }
}
