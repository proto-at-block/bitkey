package build.wallet.statemachine.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.notifications.NotificationTouchpointServiceMock
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationChannel
import build.wallet.notifications.NotificationPreferences
import build.wallet.store.KeyValueStoreFactoryFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.single

class NotificationsPreferencesCachedProviderTests : FunSpec({

  val keyValueStoreFactory = KeyValueStoreFactoryFake()

  val notificationTouchpointService = NotificationTouchpointServiceMock(turbine = turbines::create)

  val notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderImpl(
    notificationTouchpointService = notificationTouchpointService,
    keyValueStoreFactory = keyValueStoreFactory
  )

  fun makePrefs(productMarketing: Set<NotificationChannel> = emptySet()): NotificationPreferences =
    NotificationPreferences(
      accountSecurity = setOf(NotificationChannel.Email, NotificationChannel.Push),
      moneyMovement = emptySet(),
      productMarketing = productMarketing
    )

  suspend fun cacheNotificationPreferences(notificationPreferences: NotificationPreferences) {
    val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
    NotificationsPreferencesCachedProviderImpl.cacheNotificationPreferences(
      prefsCache,
      notificationPreferences
    )
  }

  suspend fun loadCachedPreferences(): NotificationPreferences? {
    val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
    return NotificationsPreferencesCachedProviderImpl.loadCachedPreferences(prefsCache)
  }

  beforeTest {
    keyValueStoreFactory.clear()
    notificationTouchpointService.reset()
  }

  test("prefs serialization sanity check") {
    val prefs = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Email),
      accountSecurity = setOf(NotificationChannel.Push),
      productMarketing = setOf(NotificationChannel.Sms, NotificationChannel.Push)
    )

    val prefsCache = keyValueStoreFactory.getOrCreate(NOTIFICATIONS_PREFERENCES_CACHE)
    prefsCache.getBoolean(PREFS_INITIALIZED, false).shouldBeFalse()
    cacheNotificationPreferences(prefs)
    prefsCache.getBoolean(PREFS_INITIALIZED, false).shouldBeTrue()

    loadCachedPreferences().shouldBe(prefs)

    cacheNotificationPreferences(prefs.copy(moneyMovement = emptySet()))
    loadCachedPreferences().shouldNotBe(prefs)
    loadCachedPreferences().shouldBe(prefs.copy(moneyMovement = emptySet()))
  }

  test("getNotificationsPreferences no cache server error") {
    val err = Err(HttpError.NetworkError(Exception("Hello")))
    notificationTouchpointService.getNotificationsPreferencesResult = err

    notificationsPreferencesCachedProvider.getNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello")
    ).single().shouldBe(err)
  }

  test("getNotificationsPreferences no cache server ok") {
    notificationTouchpointService.getNotificationsPreferencesResult = Ok(makePrefs())

    notificationsPreferencesCachedProvider.getNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello")
    ).single().shouldBe(Ok(makePrefs()))
  }

  test("getNotificationsPreferences with cache server ok no change") {
    cacheNotificationPreferences(makePrefs())
    notificationTouchpointService.getNotificationsPreferencesResult = Ok(makePrefs())

    notificationsPreferencesCachedProvider.getNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello")
    ).single().shouldBe(Ok(makePrefs()))
  }

  test("getNotificationsPreferences with cache server ok changed prefs") {
    cacheNotificationPreferences(makePrefs())
    notificationTouchpointService.getNotificationsPreferencesResult =
      Ok(makePrefs(setOf(NotificationChannel.Email)))

    val resultFlow =
      notificationsPreferencesCachedProvider.getNotificationsPreferences(
        f8eEnvironment = F8eEnvironment.Development,
        fullAccountId = FullAccountId("Hello")
      )

    resultFlow.first().shouldBe(Ok(makePrefs()))
    resultFlow.last().shouldBe(Ok(makePrefs(setOf(NotificationChannel.Email))))
  }

  test("getNotificationsPreferences with cache server err") {
    val err = Err(HttpError.NetworkError(Exception("Hello")))
    val prefs = makePrefs()
    cacheNotificationPreferences(prefs)
    notificationTouchpointService.getNotificationsPreferencesResult = err

    notificationsPreferencesCachedProvider.getNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello")
    ).single().shouldBe(Ok(prefs))
  }

  test("updateNotificationsPreferences with cache server err") {
    val err = Err(HttpError.NetworkError(Exception("Hello")))
    cacheNotificationPreferences(makePrefs())
    notificationTouchpointService.updateNotificationsPreferencesResult = err

    notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello"),
      preferences = makePrefs(setOf(NotificationChannel.Email)),
      null
    ).shouldBe(err)

    loadCachedPreferences().shouldBe(makePrefs())
  }

  test("updateNotificationsPreferences with cache server ok") {
    cacheNotificationPreferences(makePrefs())
    notificationTouchpointService.updateNotificationsPreferencesResult = Ok(Unit)

    notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello"),
      preferences = makePrefs(setOf(NotificationChannel.Email)),
      null
    ).shouldBe(Ok(Unit))

    loadCachedPreferences().shouldBe(makePrefs(setOf(NotificationChannel.Email)))
  }

  test("updateNotificationsPreferences no cache server err") {
    val err = Err(HttpError.NetworkError(Exception("Hello")))
    notificationTouchpointService.updateNotificationsPreferencesResult = err

    notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello"),
      preferences = makePrefs(setOf(NotificationChannel.Email)),
      null
    ).shouldBe(err)

    loadCachedPreferences().shouldBe(null)
  }

  test("updateNotificationsPreferences no cache server ok") {
    notificationTouchpointService.updateNotificationsPreferencesResult = Ok(Unit)

    notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("Hello"),
      preferences = makePrefs(setOf(NotificationChannel.Email)),
      null
    ).shouldBe(Ok(Unit))

    loadCachedPreferences().shouldBe(makePrefs(setOf(NotificationChannel.Email)))
  }
})
