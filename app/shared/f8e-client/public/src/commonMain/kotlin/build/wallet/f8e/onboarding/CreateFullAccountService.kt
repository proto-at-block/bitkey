package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Result

interface CreateFullAccountService {
  /**
   * Creates a [FullAccountId] and [F8eSpendingKeyset] with f8e.
   * Requires app and hardware keys.
   */
  suspend fun createAccount(
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<Success, F8eError<CreateAccountClientErrorCode>>

  data class Success(
    val f8eSpendingKeyset: F8eSpendingKeyset,
    val fullAccountId: FullAccountId,
  )
}