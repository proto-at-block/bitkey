package build.wallet.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.notifications.AddressAndKeysetId
import build.wallet.f8e.notifications.RegisterWatchAddressF8eClient
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RegisterWatchAddressProcessorImpl(
  private val registerWatchAddressF8eClient: RegisterWatchAddressF8eClient,
) : RegisterWatchAddressProcessor {
  override suspend fun processBatch(batch: List<RegisterWatchAddressContext>): Result<Unit, Error> {
    if (batch.isEmpty()) {
      return Ok(Unit)
    }

    val accountId = batch.first().accountId
    val f8eEnvironment = batch.first().f8eEnvironment

    if (batch.any { context -> context.accountId != accountId }) {
      return Err(Error(IllegalStateException("All accountIDs must match: $batch")))
    }
    if (batch.any { context -> context.f8eEnvironment != f8eEnvironment }) {
      return Err(Error(IllegalStateException("All f8eEnvironments must match: $batch")))
    }
    return registerWatchAddressF8eClient.register(
      addressAndKeysetIds =
        batch.map {
          AddressAndKeysetId(it.address.address, it.f8eSpendingKeyset.keysetId)
        },
      fullAccountId = FullAccountId(accountId),
      f8eEnvironment = f8eEnvironment
    )
  }
}
