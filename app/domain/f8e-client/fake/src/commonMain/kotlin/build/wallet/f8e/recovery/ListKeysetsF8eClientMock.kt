package build.wallet.f8e.recovery

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class ListKeysetsF8eClientMock : ListKeysetsF8eClient {
  var numKeysets = 4

  var result: Result<ListKeysetsResponse, NetworkingError>? = null

  override suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsResponse, NetworkingError> {
    if (result != null) {
      return result!!
    }
    return Ok(
      (0 until numKeysets)
        .map { index ->
          LegacyRemoteKeyset(
            keysetId = "spending-public-keyset-fake-server-id-$index",
            networkType = "SIGNET",
            appDescriptor = DescriptorPublicKeyMock(identifier = "app-dpub-$index").dpub,
            hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hardware-xpub-$index").dpub,
            serverDescriptor = DescriptorPublicKeyMock(identifier = "server-xpub-$index").dpub
          )
        }
    ).map { ListKeysetsResponse(keysets = it, wrappedSsek = null, descriptorBackups = emptyList()) }
  }

  fun reset() {
    result = null
  }
}
