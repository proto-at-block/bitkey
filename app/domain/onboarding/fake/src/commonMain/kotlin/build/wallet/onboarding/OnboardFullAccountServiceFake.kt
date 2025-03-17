package build.wallet.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.WithAppKeysMock
import build.wallet.nfc.transaction.PairingTransactionResponse
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardFullAccountServiceFake : OnboardFullAccountService {
  var createAppKeysResult: Result<WithAppKeys, Throwable> = Ok(WithAppKeysMock)

  override suspend fun createAppKeys(): Result<WithAppKeys, Throwable> {
    return createAppKeysResult
  }

  var createAccountResult: Result<FullAccount, Throwable> = Ok(FullAccountMock)

  override suspend fun createAccount(
    context: CreateFullAccountContext,
    appKeys: WithAppKeys,
    hwActivation: PairingTransactionResponse.FingerprintEnrolled,
  ): Result<FullAccount, Throwable> {
    return createAccountResult
  }

  var activateAccountResult: Result<Unit, Throwable> = Ok(Unit)

  override suspend fun activateAccount(keybox: Keybox): Result<Unit, Throwable> {
    return activateAccountResult
  }

  override suspend fun cancelAccountCreation(): Result<Unit, Throwable> {
    return Ok(Unit)
  }

  fun reset() {
    createAppKeysResult = Ok(WithAppKeysMock)
    createAccountResult = Ok(FullAccountMock)
    activateAccountResult = Ok(Unit)
  }
}
