package build.wallet.recovery.socrec

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope

class TrustedContactKeyAuthenticatorMock(
  turbine: (String) -> Turbine<FullAccount>,
) : TrustedContactKeyAuthenticator {
  val backgroundAuthenticateAndEndorseCalls = turbine("background authenticate and endorse calls")

  override suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    contacts: List<EndorsedTrustedContact>,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, Error> {
    return Ok(Unit)
  }

  override fun backgroundAuthenticateAndEndorse(
    scope: CoroutineScope,
    account: FullAccount,
  ) {
    backgroundAuthenticateAndEndorseCalls += account
  }
}
