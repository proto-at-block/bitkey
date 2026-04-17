package build.wallet.time

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toNSLocale
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSTimeZoneNameStyle.NSTimeZoneNameStyleShortDaylightSaving
import platform.Foundation.NSTimeZoneNameStyle.NSTimeZoneNameStyleShortStandard
import platform.Foundation.localizedName

@BitkeyInject(AppScope::class)
class TimeZoneFormatterImpl(
  private val localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    clock: Clock,
  ): String {
    val nsTimeZone = timeZone.toNSTimeZone()
    val locale = localeProvider.currentLocale()
    val isDaylightSaving = nsTimeZone.isDaylightSavingTimeForDate(clock.now().toNSDate())
    val style =
      if (isDaylightSaving) {
        NSTimeZoneNameStyleShortDaylightSaving
      } else {
        NSTimeZoneNameStyleShortStandard
      }
    return checkNotNull(
      nsTimeZone
        .localizedName(
          style = style,
          locale = locale.toNSLocale()
        )
    ) {
      "Could not get short name for timezone $nsTimeZone and locale $locale"
    }
  }
}
