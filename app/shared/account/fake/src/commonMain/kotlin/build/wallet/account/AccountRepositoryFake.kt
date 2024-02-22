package build.wallet.account

import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.account.AccountStatus.OnboardingAccount
import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AccountRepositoryFake : AccountRepository {
  var accountState: MutableStateFlow<Result<AccountStatus, Error>> =
    MutableStateFlow(Ok(NoAccount))

  override fun accountStatus(): Flow<Result<AccountStatus, Error>> {
    return accountState
  }

  override suspend fun setActiveAccount(account: Account): Result<Unit, Error> {
    accountState.value = Ok(ActiveAccount(account))
    return Ok(Unit)
  }

  override suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, Error> {
    accountState.value = Ok(OnboardingAccount(account))
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    accountState.value = Ok(NoAccount)
    return Ok(Unit)
  }

  fun reset() {
    accountState.value = Ok(NoAccount)
  }
}
