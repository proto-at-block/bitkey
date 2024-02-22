package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlin.reflect.KClass

class SocRecKeysDaoFake : SocRecKeysDao {
  val keys = mutableMapOf<SocRecKeyPurpose, AppKeyImpl>()

  override suspend fun <T : SocRecKey> getKey(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError> {
    val purpose = SocRecKeyPurpose.fromKeyType(keyClass)
    return getKey(purpose)
      .map { it.copy(privateKey = null) }
      .map(keyFactory)
  }

  override suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError> {
    val purpose = SocRecKeyPurpose.fromKeyType(keyClass)
    return getKey(purpose)
      .map(keyFactory)
  }

  private fun getKey(purpose: SocRecKeyPurpose): Result<AppKeyImpl, SocRecKeyError> {
    return keys[purpose]?.let { Ok(it) }
      ?: Err(SocRecKeyError.NoKeyAvailable())
  }

  override suspend fun saveKey(key: SocRecKey): Result<Unit, SocRecKeyError> {
    val appKey = key.key as AppKeyImpl
    keys[SocRecKeyPurpose.fromKeyType(key::class)] = appKey
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbTransactionError> {
    keys.clear()
    return Ok(Unit)
  }
}
