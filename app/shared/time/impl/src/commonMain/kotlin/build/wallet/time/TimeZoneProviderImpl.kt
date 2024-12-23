package build.wallet.time

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.datetime.TimeZone

@BitkeyInject(AppScope::class)
class TimeZoneProviderImpl : TimeZoneProvider {
  override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
