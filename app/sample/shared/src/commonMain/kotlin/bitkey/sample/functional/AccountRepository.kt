package bitkey.sample.functional

import com.github.michaelbull.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds

interface AccountRepository {
  fun activeAccount(): Flow<Account?>

  suspend fun activateAccount(account: Account): Result<Unit, Error>

  suspend fun createAccount(name: String): Result<Account, Error>

  suspend fun removeActiveAccount(): Result<Unit, Error>
}

class AccountRepositoryImpl(
  private val accountDao: AccountDao,
  private val createAccountService: CreateAccountService,
) : AccountRepository {
  override fun activeAccount(): Flow<Account?> = accountDao.activeAccount()

  override suspend fun createAccount(name: String): Result<Account, Error> {
    delay(1.seconds)
    return createAccountService.createAccount(name)
  }

  override suspend fun activateAccount(account: Account): Result<Unit, Error> {
    delay(0.5.seconds)
    return accountDao.setActiveAccount(account)
  }

  override suspend fun removeActiveAccount(): Result<Unit, Error> {
    delay(1.seconds)
    return accountDao.removeActiveAccount()
  }
}
