package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleIdentifierProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleIdentifierProvider {
  actual override fun localeIdentifier(): String = "en-US"
}
