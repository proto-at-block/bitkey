package build.wallet.bitcoin.bdk

import app.cash.turbine.Turbine
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

class BdkWalletSyncerMock(
  turbine: (String) -> Turbine<Any>,
) : BdkWalletSyncer {
  val syncCalls = turbine("sync bdk wallet calls")

  var syncDelay: Duration = ZERO
  var syncResult: Result<Unit, BdkError> = Ok(Unit)

  override suspend fun sync(
    bdkWallet: BdkWallet,
    networkType: BitcoinNetworkType,
  ): Result<Unit, BdkError> {
    syncCalls.add(bdkWallet)
    delay(syncDelay)
    return syncResult
  }

  fun reset() {
    syncDelay = ZERO
    syncResult = Ok(Unit)
  }
}
