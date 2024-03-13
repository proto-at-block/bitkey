@file:OptIn(ExperimentalSettingsApi::class)

package build.wallet.component.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock2
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock2
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock2
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.testing.launchNewApp
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OnboardingAppKeyKeystoreComponentTests : FunSpec({
  lateinit var keystore: OnboardingAppKeyKeystore
  lateinit var secureStore: SuspendSettings

  beforeTest {
    launchNewApp().app.run {
      keystore = appComponent.onboardingAppKeyKeystore
      secureStore = appComponent.secureStoreFactory.getOrCreate("onboarding-app-keys")
    }
  }

  test("already has keys") {
    secureStore.putString(key = "spending-key", value = AppSpendingPublicKeyMock.key.dpub)
    secureStore.putString(key = "auth-key", value = AppGlobalAuthPublicKeyMock.pubKey.value)
    secureStore.putString(
      key = "app-recovery-auth-key",
      value = AppRecoveryAuthPublicKeyMock.pubKey.value
    )
    secureStore.putString(key = "network-key", value = "BITCOIN")

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN)
      .shouldBe(
        AppKeyBundle(
          localId = "foo",
          spendingKey = AppSpendingPublicKeyMock,
          authKey = AppGlobalAuthPublicKeyMock,
          recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
          networkType = BITCOIN
        )
      )
  }

  test("missing recovery auth key") {
    secureStore.putString(key = "spending-key", value = AppSpendingPublicKeyMock.key.dpub)
    secureStore.putString(key = "auth-key", value = AppGlobalAuthPublicKeyMock.pubKey.value)
    secureStore.putString(key = "network-key", value = "BITCOIN")

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN).shouldBeNull()
  }

  test("missing spending key") {
    secureStore.putString(key = "auth-key", value = AppGlobalAuthPublicKeyMock.pubKey.value)
    secureStore.putString(
      key = "app-recovery-auth-key",
      value = AppRecoveryAuthPublicKeyMock.pubKey.value
    )
    secureStore.putString(key = "network-key", value = "BITCOIN")

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN).shouldBeNull()
  }

  test("missing global auth key") {
    secureStore.putString(key = "spending-key", value = AppSpendingPublicKeyMock.key.dpub)
    secureStore.putString(
      key = "app-recovery-auth-key",
      value = AppRecoveryAuthPublicKeyMock.pubKey.value
    )
    secureStore.putString(key = "network-key", value = "BITCOIN")

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN).shouldBeNull()
  }

  test("network type mismatch") {
    secureStore.putString(key = "spending-key", value = AppSpendingPublicKeyMock.key.dpub)
    secureStore.putString(key = "auth-key", value = AppGlobalAuthPublicKeyMock.pubKey.value)
    secureStore.putString(
      key = "app-recovery-auth-key",
      value = AppRecoveryAuthPublicKeyMock.pubKey.value
    )
    secureStore.putString(key = "network-key", value = "SIGNET")

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN).shouldBeNull()
  }

  test("persist keys") {
    keystore.persistAppKeys(
      spendingKey = AppSpendingPublicKeyMock,
      globalAuthKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      bitcoinNetworkType = BITCOIN
    )

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN)
      .shouldBe(
        AppKeyBundle(
          localId = "foo",
          spendingKey = AppSpendingPublicKeyMock,
          authKey = AppGlobalAuthPublicKeyMock,
          recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
          networkType = BITCOIN
        )
      )
  }

  test("override keys") {
    keystore.persistAppKeys(
      spendingKey = AppSpendingPublicKeyMock,
      globalAuthKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      bitcoinNetworkType = BITCOIN
    )

    keystore.persistAppKeys(
      spendingKey = AppSpendingPublicKeyMock2,
      globalAuthKey = AppGlobalAuthPublicKeyMock2,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock2,
      bitcoinNetworkType = BITCOIN
    )

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN)
      .shouldBe(
        AppKeyBundle(
          localId = "foo",
          spendingKey = AppSpendingPublicKeyMock2,
          authKey = AppGlobalAuthPublicKeyMock2,
          recoveryAuthKey = AppRecoveryAuthPublicKeyMock2,
          networkType = BITCOIN
        )
      )
  }

  test("clear keystore") {
    keystore.persistAppKeys(
      spendingKey = AppSpendingPublicKeyMock,
      globalAuthKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      bitcoinNetworkType = BITCOIN
    )

    keystore.clear()

    keystore.getAppKeyBundle(localId = "foo", network = BITCOIN).shouldBeNull()
  }
})
