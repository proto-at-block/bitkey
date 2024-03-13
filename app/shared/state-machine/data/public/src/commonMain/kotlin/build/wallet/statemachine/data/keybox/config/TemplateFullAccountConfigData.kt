package build.wallet.statemachine.data.keybox.config

import build.wallet.bitkey.account.FullAccountConfig

/**
 * Represents state of [FullAccountConfig] template, allows to update the template config.
 *
 * "Template keybox config" is a [FullAccountConfig] instance that will be used for keybox creation or
 * recovery.
 */
sealed interface TemplateFullAccountConfigData {
  /**
   * Indicates that template [FullAccountConfig] is being loaded.
   */
  data object LoadingTemplateFullAccountConfigData : TemplateFullAccountConfigData

  /**
   * Indicates that current template [FullAccountConfig] is loaded.
   *
   * @property config current template [FullAccountConfig].
   * @property updateConfig allows to update current template [FullAccountConfig]. Calling this with an
   * updated config, will result in updated [config] value.
   */
  data class LoadedTemplateFullAccountConfigData(
    val config: FullAccountConfig,
    val updateConfig: ((currentConfig: FullAccountConfig) -> FullAccountConfig) -> Unit,
  ) : TemplateFullAccountConfigData
}
