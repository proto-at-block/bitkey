package build.wallet.statemachine.data.keybox.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadingTemplateFullAccountConfigData
import com.github.michaelbull.result.get
import kotlinx.coroutines.launch

class TemplateFullAccountConfigDataStateMachineImpl(
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
) : TemplateFullAccountConfigDataStateMachine {
  @Composable
  override fun model(props: Unit): TemplateFullAccountConfigData {
    val templateFullAccountConfig = rememberTemplateFullAccountConfig()
    val scope = rememberStableCoroutineScope()

    return when (templateFullAccountConfig) {
      null -> LoadingTemplateFullAccountConfigData
      else ->
        LoadedTemplateFullAccountConfigData(
          config = templateFullAccountConfig,
          updateConfig = { updatedConfig ->
            scope.launch {
              templateFullAccountConfigDao.set(updatedConfig(templateFullAccountConfig))
            }
          }
        )
    }
  }

  @Composable
  private fun rememberTemplateFullAccountConfig(): FullAccountConfig? {
    return remember {
      templateFullAccountConfigDao.config()
    }.collectAsState(null).value?.get()
  }
}
