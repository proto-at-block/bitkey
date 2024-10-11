package build.wallet.relationships

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.crypto.PublicKey
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlin.reflect.KClass

class RelationshipsKeysDaoFake : RelationshipsKeysDao {
  val keys = mutableMapOf<SocRecKeyPurpose, AppKey<*>>()

  override suspend fun <T : SocRecKey> getPublicKey(
    keyClass: KClass<T>,
  ): Result<PublicKey<T>, RelationshipsKeyError> {
    val purpose = SocRecKeyPurpose.fromKeyType(keyClass)
    return getKey<T>(purpose)
      .map { it.publicKey }
  }

  override suspend fun <T : SocRecKey> getKeyWithPrivateMaterial(
    keyClass: KClass<T>,
  ): Result<AppKey<T>, RelationshipsKeyError> {
    val purpose = SocRecKeyPurpose.fromKeyType(keyClass)
    return getKey(purpose)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : SocRecKey> getKey(
    purpose: SocRecKeyPurpose,
  ): Result<AppKey<T>, RelationshipsKeyError> {
    return keys[purpose]?.let { Ok(it as AppKey<T>) }
      ?: Err(RelationshipsKeyError.NoKeyAvailable())
  }

  override suspend fun <T : SocRecKey> saveKey(
    key: AppKey<T>,
    keyClass: KClass<T>,
  ): Result<Unit, RelationshipsKeyError> {
    keys[SocRecKeyPurpose.fromKeyType(keyClass)] = key
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbTransactionError> {
    keys.clear()
    return Ok(Unit)
  }
}
