package build.wallet.keybox.config

import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment.Development
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [TemplateFullAccountConfigDao] implementation baked by in memory storage.
 */
class TemplateFullAccountConfigDaoFake(
  private val defaultFullAccountConfig: FullAccountConfig =
    FullAccountConfig(
      bitcoinNetworkType = TESTNET,
      isHardwareFake = true,
      f8eEnvironment = Development,
      isUsingSocRecFakes = false,
      isTestAccount = false
    ),
) : TemplateFullAccountConfigDao {
  private val fullAccountConfig = MutableStateFlow(defaultFullAccountConfig)

  override fun config(): Flow<Result<FullAccountConfig, DbError>> = fullAccountConfig.map(::Ok)

  override suspend fun set(config: FullAccountConfig): Result<Unit, DbError> {
    fullAccountConfig.value = config
    return Ok(Unit)
  }

  fun reset() {
    fullAccountConfig.value = defaultFullAccountConfig
  }
}
