package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.mobilepay.MobilePayFiatConfigF8eClient
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class MobilePayFiatConfigRepositoryImpl(
  private val mobilePayFiatConfigDao: MobilePayFiatConfigDao,
  private val mobilePayFiatConfigF8eClient: MobilePayFiatConfigF8eClient,
) : MobilePayFiatConfigRepository {
  override val configs = mobilePayFiatConfigDao.allConfigurations()

  override suspend fun fetchAndUpdateConfigs(f8eEnvironment: F8eEnvironment): Result<Unit, Error> =
    coroutineBinding {
      // Make a server call to update the database values
      val serverConfigs = mobilePayFiatConfigF8eClient.getConfigs(f8eEnvironment).bind()
      mobilePayFiatConfigDao.storeConfigurations(serverConfigs).bind()
    }.logFailure { "Error fetching and storing Mobile Pay configs for $f8eEnvironment" }
}
