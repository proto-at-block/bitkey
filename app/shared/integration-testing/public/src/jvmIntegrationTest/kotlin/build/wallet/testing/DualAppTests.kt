package build.wallet.testing

import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class DualAppTests : FunSpec({
  suspend fun runApp(appRef: AtomicReference<AppTester>): AppTester {
    val appTester = launchNewApp()
    appTester.onboardFullAccountWithFakeHardware()
    appRef.set(appTester)
    return appTester
  }

  test("onboarding 2 apps in parallel") {
    val appRef1: AtomicReference<AppTester> = AtomicReference(null)
    val appRef2: AtomicReference<AppTester> = AtomicReference(null)
    val job1 = launch { runApp(appRef1) }
    val job2 = launch { runApp(appRef2) }
    job1.join()
    job2.join()

    val app1 = appRef1.get().shouldNotBeNull()
    val app2 = appRef2.get().shouldNotBeNull()

    val keybox1 = app1.getActiveFullAccount().keybox
    val keybox2 = app2.getActiveFullAccount().keybox

    keybox1.activeSpendingKeyset.appKey.key
      .shouldNotBeEqual(keybox2.activeSpendingKeyset.appKey.key)
    keybox1.fullAccountId.serverId
      .shouldNotBeEqual(keybox2.fullAccountId.serverId)

    val reloadedKeybox1 = app1.relaunchApp().getActiveFullAccount().keybox
    val reloadedKeybox2 = app2.relaunchApp().getActiveFullAccount().keybox

    reloadedKeybox1.shouldBeEqual(keybox1)
    reloadedKeybox2.shouldBeEqual(keybox2)
  }
})
