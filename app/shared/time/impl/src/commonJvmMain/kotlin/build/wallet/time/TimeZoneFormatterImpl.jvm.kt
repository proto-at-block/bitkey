package build.wallet.time

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toJavaLocale
import kotlinx.datetime.TimeZone
import java.util.*
import java.util.TimeZone as JavaTimeZone

@BitkeyInject(AppScope::class)
class TimeZoneFormatterImpl(
  private val localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  override fun timeZoneShortName(timeZone: TimeZone): String {
    return JavaTimeZone
      .getTimeZone(timeZone.id)
      .getDisplayName(
        true, // daylight time
        0, // style = SHORT
        localeProvider.currentLocale().toJavaLocale()
      )
  }
}
