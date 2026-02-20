package build.wallet.bitcoin.bdk

import uniffi.bdk.BlockHash
import uniffi.bdk.BlockId
import uniffi.bdk.ElectrumClient
import uniffi.bdk.FullScanRequest
import uniffi.bdk.FullScanRequestBuilder
import uniffi.bdk.NoPointer
import uniffi.bdk.Persister
import uniffi.bdk.SyncRequest
import uniffi.bdk.SyncRequestBuilder
import uniffi.bdk.Update
import uniffi.bdk.Wallet

class ElectrumClientFake(
  val update: Update = Update(NoPointer),
) : ElectrumClient(NoPointer) {
  data class FullScanCall(
    val request: FullScanRequest,
    val stopGap: ULong,
    val batchSize: ULong,
    val fetchPrevTxouts: Boolean,
  )

  data class SyncCall(
    val request: SyncRequest,
    val batchSize: ULong,
    val fetchPrevTxouts: Boolean,
  )

  val fullScanCalls = mutableListOf<FullScanCall>()
  val syncCalls = mutableListOf<SyncCall>()

  override fun fullScan(
    request: FullScanRequest,
    stopGap: ULong,
    batchSize: ULong,
    fetchPrevTxouts: Boolean,
  ): Update {
    fullScanCalls.add(FullScanCall(request, stopGap, batchSize, fetchPrevTxouts))
    return update
  }

  override fun sync(
    request: SyncRequest,
    batchSize: ULong,
    fetchPrevTxouts: Boolean,
  ): Update {
    syncCalls.add(SyncCall(request, batchSize, fetchPrevTxouts))
    return update
  }

  override fun destroy() = Unit
}

class ElectrumClientFactoryFake(
  private val client: ElectrumClient,
) {
  val requestedUrls = mutableListOf<String>()

  fun create(url: String): ElectrumClient {
    requestedUrls.add(url)
    return client
  }
}

class BdkWalletSyncerV2WalletFake(
  private val checkpointHeight: UInt,
) : Wallet(NoPointer) {
  val fullScanRequest = FullScanRequest(NoPointer)
  val syncRequest = SyncRequest(NoPointer)
  var appliedUpdate: Update? = null
  var persistCalls: Int = 0

  override fun latestCheckpoint(): BlockId {
    return BlockId(height = checkpointHeight, hash = BlockHash(NoPointer))
  }

  override fun startFullScan(): FullScanRequestBuilder {
    return FullScanRequestBuilderFake(fullScanRequest)
  }

  override fun startSyncWithRevealedSpks(): SyncRequestBuilder {
    return SyncRequestBuilderFake(syncRequest)
  }

  override fun applyUpdate(update: Update) {
    appliedUpdate = update
  }

  override fun persist(persister: Persister): Boolean {
    persistCalls += 1
    return true
  }
}

class RecordingElectrumClient : ElectrumClient(NoPointer) {
  var closeCalls = 0

  override fun destroy() {
    closeCalls += 1
  }
}

class RecordingElectrumClientFactory {
  var createCalls = 0

  fun create(url: String): ElectrumClient {
    createCalls += 1
    return RecordingElectrumClient()
  }
}

class ElectrumClientProviderFake(
  private val factory: ElectrumClientFactoryFake,
) : ElectrumClientProvider {
  override fun <T> withClient(
    url: String,
    block: (ElectrumClient) -> T,
  ): T {
    val client = factory.create(url)
    return block(client)
  }

  override fun invalidate(url: String) {
    // No-op for fake
  }
}

private class FullScanRequestBuilderFake(
  private val request: FullScanRequest,
) : FullScanRequestBuilder(NoPointer) {
  override fun build(): FullScanRequest = request
}

private class SyncRequestBuilderFake(
  private val request: SyncRequest,
) : SyncRequestBuilder(NoPointer) {
  override fun build(): SyncRequest = request
}
