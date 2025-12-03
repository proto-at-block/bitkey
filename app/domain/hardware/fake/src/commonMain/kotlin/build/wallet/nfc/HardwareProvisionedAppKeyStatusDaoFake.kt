package build.wallet.nfc

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class HardwareProvisionedAppKeyStatusDaoFake : HardwareProvisionedAppKeyStatusDao {
  private val provisionedKeys =
    mutableMapOf<Pair<HwAuthPublicKey, PublicKey<AppGlobalAuthKey>>, Unit>()
  var activeAccountKeys: Pair<HwAuthPublicKey, PublicKey<AppGlobalAuthKey>>? = null
  private val provisionedKeysFlow = MutableStateFlow(0)

  override suspend fun recordProvisionedKey(
    hwAuthPubKey: HwAuthPublicKey,
    appAuthPubKey: PublicKey<AppGlobalAuthKey>,
  ): Result<Unit, DbError> {
    provisionedKeys[hwAuthPubKey to appAuthPubKey] = Unit
    provisionedKeysFlow.value += 1
    return Ok(Unit)
  }

  override suspend fun isKeyProvisionedForActiveAccount(): Result<Boolean, DbError> {
    return Ok(
      activeAccountKeys?.let { (hwKey, appKey) ->
        provisionedKeys.containsKey(hwKey to appKey)
      } ?: false
    )
  }

  override fun isKeyProvisionedForActiveAccountFlow(): Flow<Boolean> {
    return provisionedKeysFlow.map {
      activeAccountKeys?.let { (hwKey, appKey) ->
        provisionedKeys.containsKey(hwKey to appKey)
      } ?: false
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    provisionedKeys.clear()
    provisionedKeysFlow.value += 1
    return Ok(Unit)
  }

  fun reset() {
    provisionedKeys.clear()
    activeAccountKeys = null
    provisionedKeysFlow.value = 0
  }
}
