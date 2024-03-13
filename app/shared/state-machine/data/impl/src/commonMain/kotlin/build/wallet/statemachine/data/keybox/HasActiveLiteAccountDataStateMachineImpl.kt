package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.keybox.Keybox
import build.wallet.keybox.KeyboxDao
import build.wallet.statemachine.data.account.create.CreateFullAccountContext
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataState.UpgradingLiteAccount
import build.wallet.statemachine.data.keybox.HasActiveLiteAccountDataState.UsingLiteAccount
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.map

class HasActiveLiteAccountDataStateMachineImpl(
  private val createFullAccountDataStateMachine: CreateFullAccountDataStateMachine,
  private val keyboxDao: KeyboxDao,
) : HasActiveLiteAccountDataStateMachine {
  @Composable
  override fun model(props: HasActiveLiteAccountDataProps): AccountData {
    /*
    When we have an active Lite Account, we either want to emit [HasActiveLiteAccountData] or
    [NoActiveAccountData.CreatingFullAccountData] in the case that we are starting the upgrade
    process to a Full Account.

    The reason [NoActiveAccountData] would be emitted when we still technically have an active Lite
    Account is because the customer is still in a state where they can cancel the upgrade (i.e. they
    haven't completed HW pairing yet). Once they complete HW pairing, we call
    [KeyboxDao.saveKeyboxAndBeginOnboarding] which clears the active Lite Account and saves a Full
    Account in an onboarding state. At that point, this code path will no longer be hit and instead
    the FullAccount? case above will flow through to [accountDataBasedOnAccount] where
    [NoActiveAccountData] will continue to be emitted.

    TODO (BKR-643): If we remove the reactive-ness of these state machines a bit,
     we could separately reuse the data state machines for the upgrade path
     instead of needing to handle it here.
     */

    val onboardingKeybox = rememberOnboardingKeybox()
    var state: HasActiveLiteAccountDataState by remember(onboardingKeybox) {
      mutableStateOf(
        // Force the state into [UpgradingLiteAccount] if there's an onboarding keybox
        if (onboardingKeybox != null) UpgradingLiteAccount else UsingLiteAccount
      )
    }

    return when (state) {
      is UsingLiteAccount ->
        AccountData.HasActiveLiteAccountData(
          account = props.account,
          accountUpgradeOnboardConfigData = props.accountUpgradeOnboardConfigData,
          accountUpgradeTemplateFullAccountConfigData = props.accountUpgradeTemplateFullAccountConfigData,
          onUpgradeAccount = { state = UpgradingLiteAccount }
        )

      is UpgradingLiteAccount ->
        AccountData.NoActiveAccountData.CreatingFullAccountData(
          templateFullAccountConfig = props.accountUpgradeTemplateFullAccountConfigData.config,
          createFullAccountData =
            createFullAccountDataStateMachine.model(
              props =
                CreateFullAccountDataProps(
                  templateFullAccountConfig = props.accountUpgradeTemplateFullAccountConfigData.config,
                  onboardConfig = props.accountUpgradeOnboardConfigData.config,
                  onboardingKeybox = onboardingKeybox,
                  currencyPreferenceData = props.currencyPreferenceData,
                  context = CreateFullAccountContext.LiteToFullAccountUpgrade(props.account),
                  rollback = { state = UsingLiteAccount }
                )
            )
        )
    }
  }

  @Composable
  private fun rememberOnboardingKeybox(): Keybox? {
    return remember {
      keyboxDao.onboardingKeybox().unwrapLoadedValue().map {
        // Treat DbError as null Keybox value
        it.get()
      }
    }.collectAsState(null).value
  }
}

private sealed interface HasActiveLiteAccountDataState {
  /** Customer is using the Lite Account as normal */
  data object UsingLiteAccount : HasActiveLiteAccountDataState

  /** Customer is upgrading the Lite Account to a Full Account */
  data object UpgradingLiteAccount : HasActiveLiteAccountDataState
}
