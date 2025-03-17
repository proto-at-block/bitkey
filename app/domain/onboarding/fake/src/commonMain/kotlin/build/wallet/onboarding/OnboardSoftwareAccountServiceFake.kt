package build.wallet.onboarding

import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.keybox.SoftwareAccountMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardSoftwareAccountServiceFake : OnboardSoftwareAccountService {
  override suspend fun createAccount(): Result<SoftwareAccount, Throwable> {
    return Ok(SoftwareAccountMock)
  }
}
