package bitkey.sample.di

import bitkey.sample.functional.AccountDaoImpl
import bitkey.sample.functional.AccountRepositoryImpl
import bitkey.sample.functional.CreateAccountF8eClientImpl
import bitkey.sample.ui.SampleAppUiStateMachine
import bitkey.sample.ui.SampleAppUiStateMachineImpl
import bitkey.sample.ui.SampleScreenPresenterRegistry
import bitkey.sample.ui.home.AccountHomeUiStateMachineImpl
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl
import bitkey.sample.ui.settings.account.AccountSettingsScreenPresenter
import bitkey.ui.framework.NavigatorPresenterImpl
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DeviceInfoProviderImpl

interface SampleAppComponent {
  val sampleAppUiStateMachine: SampleAppUiStateMachine
  val deviceInfoProvider: DeviceInfoProvider

  companion object {
    fun create(): SampleAppComponent = SampleAppComponentImpl()
  }
}

internal class SampleAppComponentImpl : SampleAppComponent {
  private val accountDao = AccountDaoImpl()
  private val createAccountF8eClient = CreateAccountF8eClientImpl()

  private val accountRepository = AccountRepositoryImpl(
    accountDao = accountDao,
    createAccountF8eClient = createAccountF8eClient
  )

  private val accountSettingsScreenPresenter = AccountSettingsScreenPresenter(accountRepository)

  private val screenPresenterRegistry = SampleScreenPresenterRegistry(
    accountSettingsScreenPresenter = accountSettingsScreenPresenter
  )

  private val navigatorPresenter = NavigatorPresenterImpl(
    screenPresenterRegistry = screenPresenterRegistry
  )

  private val accountHomeStateMachine = AccountHomeUiStateMachineImpl(
    navigatorPresenter = navigatorPresenter
  )

  private val createAccountStateMachine = CreateAccountUiStateMachineImpl(
    createAccountRepository = accountRepository
  )

  override val sampleAppUiStateMachine = SampleAppUiStateMachineImpl(
    accountRepository = accountRepository,
    accountHomeUiStateMachine = accountHomeStateMachine,
    createAccountUiStateMachine = createAccountStateMachine
  )

  override val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProviderImpl()
}
