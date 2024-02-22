package build.wallet.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.notifications.AddressAndKeysetId
import build.wallet.f8e.notifications.RegisterWatchAddressService
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RegisterWatchAddressSenderImpl(
  private val registerWatchAddressService: RegisterWatchAddressService,
) : Processor<RegisterWatchAddressContext> {
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
    return registerWatchAddressService.register(
      addressAndKeysetIds =
        batch.map {
          AddressAndKeysetId(it.address.address, it.spendingKeysetId)
        },
      fullAccountId = FullAccountId(accountId),
      f8eEnvironment = f8eEnvironment
    )
  }
}
