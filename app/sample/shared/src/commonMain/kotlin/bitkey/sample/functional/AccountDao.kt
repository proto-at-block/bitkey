package bitkey.sample.functional

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.seconds

interface AccountDao {
  fun activeAccount(): Flow<Account?>

  fun setActiveAccount(account: Account): Result<Unit, Error>

  fun removeActiveAccount(): Result<Unit, Error>
}

class AccountDaoImpl : AccountDao {
  // TODO: implement using SqlDelight database
  private val accountState = MutableStateFlow<Account?>(null)

  override fun activeAccount(): Flow<Account?> {
    return accountState.asStateFlow().onStart {
      delay(0.5.seconds)
    }
  }

  override fun setActiveAccount(account: Account): Result<Unit, Error> {
    accountState.value = account
    return Ok(Unit)
  }

  override fun removeActiveAccount(): Result<Unit, Error> {
    // Simulate occasional failure to remove the account from the database
    val shouldFail = setOf(false, true).random()
    return if (shouldFail) {
      Err(Error("Fake error removing account from db"))
    } else {
      accountState.value = null
      Ok(Unit)
    }
  }
}
