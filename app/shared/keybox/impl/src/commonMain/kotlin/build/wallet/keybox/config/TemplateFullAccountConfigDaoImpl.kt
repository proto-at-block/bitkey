package build.wallet.keybox.config

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.TemplateFullAccountConfigEntity
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

class TemplateFullAccountConfigDaoImpl(
  appVariant: AppVariant,
  private val bitkeyDatabaseProvider: BitkeyDatabaseProvider,
) : TemplateFullAccountConfigDao {
  private val database by lazy {
    bitkeyDatabaseProvider.database()
  }

  override fun config(): Flow<Result<FullAccountConfig, DbError>> {
    return database.templateFullAccountConfigQueries.config()
      .asFlowOfOneOrNull()
      .map { result ->
        result.flatMap { value ->
          when (value) {
            null -> {
              // If no config found in db,
              // emit and set default configuration based on app variant.
              set(defaultConfig).map { defaultConfig }
            }

            else -> Ok(value.toFullAccountConfig())
          }
        }
      }
      .distinctUntilChanged()
  }

  override suspend fun set(config: FullAccountConfig): Result<Unit, DbError> {
    return database.awaitTransactionWithResult {
      templateFullAccountConfigQueries.setConfig(
        bitcoinNetworkType = config.bitcoinNetworkType,
        fakeHardware = config.isHardwareFake,
        f8eEnvironment = config.f8eEnvironment,
        isTestAccount = config.isTestAccount,
        isUsingSocRecFakes = config.isUsingSocRecFakes,
        delayNotifyDuration = config.delayNotifyDuration
      )
    }
  }

  private val defaultConfig =
    when (appVariant) {
      Development ->
        FullAccountConfig(
          bitcoinNetworkType = SIGNET,
          isHardwareFake = true,
          f8eEnvironment = Staging,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds
        )

      Team ->
        FullAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds
        )

      Beta ->
        FullAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )

      Customer ->
        FullAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )

      Emergency ->
        FullAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.ForceOffline,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
    }

  private fun TemplateFullAccountConfigEntity.toFullAccountConfig() =
    FullAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      isHardwareFake = fakeHardware,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes,
      delayNotifyDuration = delayNotifyDuration
    )
}
