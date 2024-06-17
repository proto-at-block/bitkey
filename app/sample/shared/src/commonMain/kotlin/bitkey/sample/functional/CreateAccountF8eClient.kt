package bitkey.sample.functional

import build.wallet.platform.random.uuid
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

interface CreateAccountF8eClient {
  suspend fun createAccount(name: String): Result<Account, Error>
}

class CreateAccountF8eClientImpl : CreateAccountF8eClient {
  override suspend fun createAccount(name: String): Result<Account, Error> {
    return Ok(Account(id = uuid(), name = name))
  }
}
