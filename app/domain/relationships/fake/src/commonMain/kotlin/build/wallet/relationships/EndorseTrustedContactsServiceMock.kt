package build.wallet.relationships

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class EndorseTrustedContactsServiceMock(
  turbine: (String) -> Turbine<FullAccount>,
) : EndorseTrustedContactsService {
  val backgroundAuthenticateAndEndorseCalls = turbine("background authenticate and endorse calls")

  var lastRegenerateAndEndorseArgs: RegenerateAndEndorseArgs? = null

  data class RegenerateAndEndorseArgs(
    val accountId: FullAccountId,
    val oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    val oldHwAuthKey: HwAuthPublicKey,
    val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    val newHwAuthKey: HwAuthPublicKey,
  )

  override suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    contacts: List<EndorsedTrustedContact>,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    newHwAuthKey: HwAuthPublicKey,
  ): Result<Unit, Error> {
    lastRegenerateAndEndorseArgs = RegenerateAndEndorseArgs(
      accountId = accountId,
      oldAppGlobalAuthKey = oldAppGlobalAuthKey,
      oldHwAuthKey = oldHwAuthKey,
      newAppGlobalAuthKey = newAppGlobalAuthKey,
      newAppGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature,
      newHwAuthKey = newHwAuthKey
    )
    return Ok(Unit)
  }

  fun reset() {
    lastRegenerateAndEndorseArgs = null
  }
}
