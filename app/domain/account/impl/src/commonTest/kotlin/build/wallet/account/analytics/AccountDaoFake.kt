package build.wallet.account.analytics

import build.wallet.account.AccountDao
import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AccountDaoFake : AccountDao {
  val activeAccount: MutableStateFlow<Result<Account?, Error>> = MutableStateFlow(Ok(null))
  val onboardingAccount: MutableStateFlow<Result<Account?, Error>> = MutableStateFlow(Ok(null))

  override fun activeAccount(): Flow<Result<Account?, Error>> {
    return activeAccount
  }

  override fun onboardingAccount(): Flow<Result<Account?, Error>> {
    return onboardingAccount
  }

  override suspend fun setActiveAccount(account: Account): Result<Unit, Error> {
    activeAccount.value = Ok(account)
    return Ok(Unit)
  }

  override suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, Error> {
    onboardingAccount.value = Ok(account)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    activeAccount.value = Ok(null)
    onboardingAccount.value = Ok(null)
    return Ok(Unit)
  }
}
