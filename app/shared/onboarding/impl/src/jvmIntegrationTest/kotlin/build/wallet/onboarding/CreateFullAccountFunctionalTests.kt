package build.wallet.onboarding

import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId
import build.wallet.home.GettingStartedTask.TaskState
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import build.wallet.testing.ext.startAndCompleteFingerprintEnrolment
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first

class CreateFullAccountFunctionalTests : FunSpec({
  lateinit var app: AppTester

  beforeTest {
    app = launchNewApp()
  }

  // if we have previously persisted app keys we restore those and return them (to keep account
  // creation idempotent). Otherwise, we create a new app key bundle for account creation.
  test("createAppKeys is idempotent - same keys are returned when called multiple times") {
    val keys1 = app.createFullAccountService.createAppKeys().shouldBeOk()
    val keys2 = app.createFullAccountService.createAppKeys().shouldBeOk()

    // localId is a random local UUID, so it will be different each time
    keys1.appKeyBundle.copy(localId = "").shouldBe(keys2.appKeyBundle.copy(localId = ""))

    keys1.config.shouldBe(keys2.config)
    val expectedConfig = app.debugOptionsService.options().first().toFullAccountConfig()
    keys1.config.shouldBe(expectedConfig)
  }

  test("createAppKeys returns new keys if onboarding state is cleared") {
    val keys1 = app.createFullAccountService.createAppKeys().shouldBeOk()
    app.onboardingAppKeyKeystore.clear().shouldBeOk()
    val keys2 = app.createFullAccountService.createAppKeys().shouldBeOk()

    // localId is a random local UUID, so it will be different each time
    keys1.appKeyBundle.copy(localId = "").shouldNotBe(keys2.appKeyBundle.copy(localId = ""))

    keys1.config.shouldBe(keys2.config)
    val expectedConfig = app.debugOptionsService.options().first().toFullAccountConfig()
    keys1.config.shouldBe(expectedConfig)
  }

  test("create and activate brand new full account using fake hardware keys successfully") {
    // Create app keys
    val appKeys = app.createFullAccountService.createAppKeys().shouldBeOk()
    // Pair hardware
    val hwActivation =
      app.startAndCompleteFingerprintEnrolment(appAuthKey = appKeys.appKeyBundle.authKey)

    // Create new Full account
    val account = app.createFullAccountService.createAccount(
      context = CreateFullAccountContext.NewFullAccount,
      appKeys = appKeys,
      hwActivation = hwActivation
    ).shouldBeOk()

    account.config.shouldBe(appKeys.config)
    account.keybox.activeAppKeyBundle.shouldBe(appKeys.appKeyBundle)
    account.keybox.activeHwKeyBundle.shouldBe(hwActivation.keyBundle)

    // Account is not activated yet
    app.accountService.activeAccount().first().shouldBeNull()

    // No getting started tasks before account activation
    app.gettingStartedTaskDao.getTasks().shouldBeEmpty()

    // Activate account
    app.createFullAccountService.activateAccount(account.keybox).shouldBeOk()

    // Account is active
    app.accountService.activeAccount().first().shouldBe(account)

    // Getting started tasks are populated
    app.gettingStartedTaskDao.getTasks().shouldContainExactly(
      GettingStartedTask(TaskId.AddBitcoin, TaskState.Incomplete),
      GettingStartedTask(TaskId.InviteTrustedContact, TaskState.Incomplete),
      GettingStartedTask(TaskId.EnableSpendingLimit, TaskState.Incomplete),
      GettingStartedTask(TaskId.AddAdditionalFingerprint, TaskState.Incomplete)
    )
  }

  test("create and activate full account for migrating lite account to full account") {
    // Set up a Lite account
    val protectedCustomerApp = launchNewApp()
    protectedCustomerApp.onboardFullAccountWithFakeHardware()
    val (inviteCode, _) = protectedCustomerApp.createTcInvite(tcName = "bob")
    val liteAccount =
      app.onboardLiteAccountFromInvitation(inviteCode, protectedCustomerName = "alice")

    // Create app keys
    val appKeys = app.createFullAccountService.createAppKeys().shouldBeOk()

    // Pair hardware
    val hwActivation =
      app.startAndCompleteFingerprintEnrolment(appAuthKey = appKeys.appKeyBundle.authKey)

    // Create full account by "upgrading" lite account
    val fullAccount = app.createFullAccountService.createAccount(
      context = CreateFullAccountContext.LiteToFullAccountUpgrade(liteAccount),
      appKeys = appKeys,
      hwActivation = hwActivation
    ).shouldBeOk()

    // Account ID is different.
    fullAccount.accountId.shouldNotBe(liteAccount.accountId)
    fullAccount.config.shouldBe(appKeys.config)
    fullAccount.keybox.activeAppKeyBundle.shouldBe(
      // Recovery key is reused from lite account
      appKeys.appKeyBundle.copy(
        recoveryAuthKey = liteAccount.recoveryAuthKey
      )
    )
    fullAccount.keybox.activeHwKeyBundle.shouldBe(hwActivation.keyBundle)

    // Account is not active yet
    app.accountService.activeAccount().first().shouldBeNull()

    // No getting started tasks before account activation
    app.gettingStartedTaskDao.getTasks().shouldBeEmpty()

    // Activate account
    app.createFullAccountService.activateAccount(fullAccount.keybox).shouldBeOk()

    // Account is active
    app.accountService.activeAccount().first().shouldBe(fullAccount)

    // Getting started tasks are populated
    app.gettingStartedTaskDao.getTasks().shouldContainExactly(
      GettingStartedTask(TaskId.AddBitcoin, TaskState.Incomplete),
      GettingStartedTask(TaskId.InviteTrustedContact, TaskState.Incomplete),
      GettingStartedTask(TaskId.EnableSpendingLimit, TaskState.Incomplete),
      GettingStartedTask(TaskId.AddAdditionalFingerprint, TaskState.Incomplete)
    )
  }

  test("cannot pair hardware that is already in use") {
    // Pair hardware with one account
    app.onboardFullAccountWithFakeHardware()

    val appKeys = app.createFullAccountService.createAppKeys().shouldBeOk()
    val hwActivation =
      app.startAndCompleteFingerprintEnrolment(appAuthKey = appKeys.appKeyBundle.authKey)

    // Cannot pair hardware that is already in use
    app.createFullAccountService.createAccount(
      context = CreateFullAccountContext.NewFullAccount,
      appKeys = appKeys,
      hwActivation = hwActivation
    ).shouldBeErrOfType<HardwareKeyAlreadyInUseError>()
  }

  test("createAccount - cannot create account using app key that is already associated with an account") {
    // Pair hardware with one account
    val account = app.onboardFullAccountWithFakeHardware()
    // Setup quirk: reset the keybox database so that we can create a new account.
    app.keyboxDao.clear().shouldBeOk()
    // Reset fake hardware so that we use new hw keys. Using the same hw key will just return an
    // existing account.
    app.fakeHardwareKeyStore.clear()

    val hwActivation =
      app.startAndCompleteFingerprintEnrolment(appAuthKey = account.keybox.activeAppKeyBundle.authKey)

    // Cannot pair hardware that is already in use
    app.createFullAccountService.createAccount(
      context = CreateFullAccountContext.NewFullAccount,
      appKeys = KeyCrossDraft.WithAppKeys(
        config = account.config,
        appKeyBundle = account.keybox.activeAppKeyBundle
      ),
      hwActivation = hwActivation
    ).shouldBeErr(AppKeyAlreadyInUseError)
  }

  test("createAccount - when reusing same app and hardware keys, we get the same account") {
    // Pair hardware with one account
    val account1 = app.onboardFullAccountWithFakeHardware()
    // Setup quirk: reset the keybox database so that we can create a new account.
    app.keyboxDao.clear().shouldBeOk()

    val hwActivation =
      app.startAndCompleteFingerprintEnrolment(appAuthKey = account1.keybox.activeAppKeyBundle.authKey)

    // Cannot pair hardware that is already in use
    val account2 = app.createFullAccountService.createAccount(
      context = CreateFullAccountContext.NewFullAccount,
      appKeys = KeyCrossDraft.WithAppKeys(
        config = account1.config,
        appKeyBundle = account1.keybox.activeAppKeyBundle
      ),
      hwActivation = hwActivation
    ).shouldBeOk()

    // We just get the same account
    // localId is a random local UUID, so we are ignoring it.
    account1.copy(
      keybox = account1.keybox.copy(
        localId = "",
        activeSpendingKeyset = account1.keybox.activeSpendingKeyset.copy(localId = ""),
        activeHwKeyBundle = account1.keybox.activeHwKeyBundle.copy(localId = "")
      )
    ).shouldBe(
      account2.copy(
        keybox = account2.keybox.copy(
          localId = "",
          activeSpendingKeyset = account2.keybox.activeSpendingKeyset.copy(localId = ""),
          activeHwKeyBundle = account2.keybox.activeHwKeyBundle.copy(localId = "")
        )
      )
    )
  }
})
