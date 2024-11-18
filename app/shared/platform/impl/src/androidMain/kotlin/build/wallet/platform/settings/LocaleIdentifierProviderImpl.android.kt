package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleIdentifierProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleIdentifierProvider {
  private val locale by lazy {
    platformContext.appContext.resources.configuration.locales.get(0)
  }

  actual override fun localeIdentifier(): String {
    return locale.toLanguageTag()
  }
}
