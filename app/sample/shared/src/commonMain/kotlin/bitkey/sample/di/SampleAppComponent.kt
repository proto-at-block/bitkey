package bitkey.sample.di

import bitkey.sample.functional.AccountDaoImpl
import bitkey.sample.functional.AccountRepositoryImpl
import bitkey.sample.functional.CreateAccountServiceImpl
import bitkey.sample.ui.SampleAppUiStateMachine
import bitkey.sample.ui.SampleAppUiStateMachineImpl
import bitkey.sample.ui.home.AccountHomeUiStateMachineImpl
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl
import bitkey.sample.ui.settings.SettingsUiStateMachineImpl
import bitkey.sample.ui.settings.account.AccountSettingsUiStateMachineImpl

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

  private val accountSettingsUiStateMachine = AccountSettingsUiStateMachineImpl()

  private val settingsUiStateMachine = SettingsUiStateMachineImpl(
    accountSettingsUiStateMachine = accountSettingsUiStateMachine
  )

  private val accountHomeStateMachine = AccountHomeUiStateMachineImpl(
    settingsUiStateMachine = settingsUiStateMachine
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
