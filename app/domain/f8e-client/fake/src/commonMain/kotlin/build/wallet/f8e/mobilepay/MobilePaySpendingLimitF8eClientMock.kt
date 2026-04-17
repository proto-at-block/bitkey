package build.wallet.f8e.mobilepay

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.MobilePayErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import build.wallet.limit.SpendingLimit
import build.wallet.platform.settings.Locale
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MobilePaySpendingLimitF8eClientMock : MobilePaySpendingLimitF8eClient {
  override suspend fun setSpendingLimit(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    limit: SpendingLimit,
    proof: PrivilegedActionProof,
    locale: Locale,
  ): Result<Unit, NetworkingError> {
    return Ok(Unit)
  }

  override suspend fun disableMobilePay(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    proof: PrivilegedActionProof?,
  ): Result<Unit, F8eError<MobilePayErrorCode>> {
    return Ok(Unit)
  }
}
