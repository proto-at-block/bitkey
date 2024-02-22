package build.wallet.statemachine.data.keybox.config

import build.wallet.bitkey.keybox.KeyboxConfig

/**
 * Represents state of [KeyboxConfig] template, allows to update the template config.
 *
 * "Template keybox config" is a [KeyboxConfig] instance that will be used for keybox creation or
 * recovery.
 */
sealed interface TemplateKeyboxConfigData {
  /**
   * Indicates that template [KeyboxConfig] is being loaded.
   */
  data object LoadingTemplateKeyboxConfigData : TemplateKeyboxConfigData

  /**
   * Indicates that current template [KeyboxConfig] is loaded.
   *
   * @property config current template [KeyboxConfig].
   * @property updateConfig allows to update current template [KeyboxConfig]. Calling this with an
   * updated config, will result in updated [config] value.
   */
  data class LoadedTemplateKeyboxConfigData(
    val config: KeyboxConfig,
    val updateConfig: ((currentConfig: KeyboxConfig) -> KeyboxConfig) -> Unit,
  ) : TemplateKeyboxConfigData
}
