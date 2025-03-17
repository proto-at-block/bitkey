package build.wallet.account

import build.wallet.account.AccountStatus.*
import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

class AccountServiceFake : AccountService {
  var accountState: MutableStateFlow<Result<AccountStatus, Error>> =
    MutableStateFlow(Ok(NoAccount))

  override fun accountStatus(): Flow<Result<AccountStatus, Error>> {
    return accountState
  }

  override fun activeAccount(): Flow<Account?> {
    return accountStatus()
      .mapLatest { result ->
        result.fold(
          success = { status -> (status as? ActiveAccount)?.account },
          failure = { null }
        )
      }
      .distinctUntilChanged()
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
