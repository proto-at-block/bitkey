package build.wallet.time

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toJavaLocale
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.util.*
import java.util.TimeZone as JavaTimeZone

@BitkeyInject(AppScope::class)
class TimeZoneFormatterImpl(
  private val localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    clock: Clock,
  ): String {
    val javaTimeZone = JavaTimeZone.getTimeZone(timeZone.id)
    return javaTimeZone.getDisplayName(
      javaTimeZone.inDaylightTime(Date(clock.now().toEpochMilliseconds())),
      JavaTimeZone.SHORT,
      localeProvider.currentLocale().toJavaLocale()
    )
  }
}
