package build.wallet.f8e.recovery

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.ListKeysetsF8eClient.ListKeysetsResponse
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class ListKeysetsF8eClientMock : ListKeysetsF8eClient {
  var numKeysets = 3

  var result: Result<ListKeysetsResponse, NetworkingError>? = null

  override suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsResponse, NetworkingError> {
    if (result != null) {
      return result!!
    }
    return Ok(
      (0..numKeysets)
        .map { num ->
          SpendingKeyset(
            localId = "spending-public-keyset-fake-id-$num",
            f8eSpendingKeyset =
              F8eSpendingKeyset(
                keysetId = "spending-public-keyset-fake-server-id-$num",
                spendingPublicKey =
                  F8eSpendingPublicKey(
                    DescriptorPublicKeyMock(identifier = "server-xpub-$num")
                  )
              ),
            networkType = SIGNET,
            appKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "app-dpub-$num")),
            hardwareKey =
              HwSpendingPublicKey(
                DescriptorPublicKeyMock(identifier = "hardware-xpub-$num")
              )
          )
        }
    ).map { ListKeysetsResponse(keysets = it, descriptorBackups = null) }
  }

  fun reset() {
    result = null
  }
}
