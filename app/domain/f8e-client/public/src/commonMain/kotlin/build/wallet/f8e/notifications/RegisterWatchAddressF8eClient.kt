package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface RegisterWatchAddressF8eClient {
  /**
   * Register a list of addresses with F8e
   */
  suspend fun register(
    addressAndKeysetIds: List<AddressAndKeysetId>,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error>
}

@Serializable
@Redacted
data class AddressAndKeysetId(
  @SerialName("address")
  val address: String,
  @SerialName("spending_keyset_id")
  val spendingKeysetId: String,
)
