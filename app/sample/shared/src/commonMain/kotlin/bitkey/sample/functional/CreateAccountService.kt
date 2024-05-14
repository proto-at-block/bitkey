package bitkey.sample.functional

import build.wallet.platform.random.uuid
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

interface CreateAccountService {
  suspend fun createAccount(name: String): Result<Account, Error>
}

class CreateAccountServiceImpl : CreateAccountService {
  override suspend fun createAccount(name: String): Result<Account, Error> {
    return Ok(Account(id = uuid(), name = name))
  }
}
