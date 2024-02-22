package build.wallet.keybox.config

import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment.Development
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [TemplateKeyboxConfigDao] implementation baked by in memory storage.
 */
class TemplateKeyboxConfigDaoFake(
  private val defaultKeyboxConfig: KeyboxConfig =
    KeyboxConfig(
      networkType = TESTNET,
      isHardwareFake = true,
      f8eEnvironment = Development,
      isUsingSocRecFakes = false,
      isTestAccount = false
    ),
) : TemplateKeyboxConfigDao {
  private val keyboxConfig = MutableStateFlow(defaultKeyboxConfig)

  override fun config(): Flow<Result<KeyboxConfig, DbError>> = keyboxConfig.map(::Ok)

  override suspend fun set(config: KeyboxConfig): Result<Unit, DbError> {
    keyboxConfig.value = config
    return Ok(Unit)
  }

  fun reset() {
    keyboxConfig.value = defaultKeyboxConfig
  }
}
