package build.wallet.statemachine.data.keybox.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.keybox.config.TemplateKeyboxConfigDao
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadingTemplateKeyboxConfigData
import com.github.michaelbull.result.get
import kotlinx.coroutines.launch

class TemplateKeyboxConfigDataStateMachineImpl(
  private val templateKeyboxConfigDao: TemplateKeyboxConfigDao,
) : TemplateKeyboxConfigDataStateMachine {
  @Composable
  override fun model(props: Unit): TemplateKeyboxConfigData {
    val templateKeyboxConfig = rememberTemplateKeyboxConfig()
    val scope = rememberStableCoroutineScope()

    return when (templateKeyboxConfig) {
      null -> LoadingTemplateKeyboxConfigData
      else ->
        LoadedTemplateKeyboxConfigData(
          config = templateKeyboxConfig,
          updateConfig = { updatedConfig ->
            scope.launch {
              templateKeyboxConfigDao.set(updatedConfig(templateKeyboxConfig))
            }
          }
        )
    }
  }

  @Composable
  private fun rememberTemplateKeyboxConfig(): KeyboxConfig? {
    return remember {
      templateKeyboxConfigDao.config()
    }.collectAsState(null).value?.get()
  }
}
