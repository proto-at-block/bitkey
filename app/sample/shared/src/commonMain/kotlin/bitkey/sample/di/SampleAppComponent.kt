package bitkey.sample.di

import bitkey.sample.functional.AccountDaoImpl
import bitkey.sample.functional.AccountRepositoryImpl
import bitkey.sample.functional.CreateAccountServiceImpl
import bitkey.sample.ui.SampleAppUiStateMachine
import bitkey.sample.ui.SampleAppUiStateMachineImpl
import bitkey.sample.ui.SampleScreenPresenterRegistry
import bitkey.sample.ui.home.AccountHomeUiStateMachineImpl
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl
import bitkey.sample.ui.settings.account.AccountSettingsScreenPresenter
import build.wallet.ui.framework.NavigatorPresenterImpl

interface SampleAppComponent {
  val sampleAppUiStateMachine: SampleAppUiStateMachine

  companion object {
    fun create(): SampleAppComponent = SampleAppComponentImpl()
  }
}

internal class SampleAppComponentImpl : SampleAppComponent {
  private val accountDao = AccountDaoImpl()
  private val createAccountService = CreateAccountServiceImpl()

  private val accountRepository = AccountRepositoryImpl(
    accountDao = accountDao,
    createAccountService = createAccountService
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
}
