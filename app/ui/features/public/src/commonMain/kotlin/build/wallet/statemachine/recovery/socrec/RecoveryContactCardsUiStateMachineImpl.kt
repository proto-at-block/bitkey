package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.LoadableValue
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.FAILED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactCardModel
import build.wallet.ui.model.button.ButtonModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock

@BitkeyInject(ActivityScope::class)
class RecoveryContactCardsUiStateMachineImpl(
  private val clock: Clock,
  private val relationshipsService: RelationshipsService,
) : RecoveryContactCardsUiStateMachine {
  @Composable
  override fun model(props: RecoveryContactCardsUiProps): LoadableValue<ImmutableList<CardModel>> {
    val relationships = remember { relationshipsService.relationships }
      .collectAsState().value

    if (relationships == null) {
      return InitialLoading
    }

    return LoadedValue(
      listOf(
        relationships.invitations
          .filter { it.roles.singleOrNull() == TrustedContactRole.SocialRecoveryContact }
          .map {
            val isExpired = it.isExpired(clock)
            TrustedContactCardModel(
              contact = it,
              buttonText = if (isExpired) {
                "Expired"
              } else {
                "Pending"
              },
              backgroundColor = if (isExpired) {
                CardModel.CardStyle.Gradient.BackgroundColor.Default
              } else {
                CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground
              },
              onClick = { props.onClick(it) }
            )
          },
        relationships.unendorsedTrustedContacts
          .filter { it.authenticationState in setOf(FAILED, PAKE_DATA_UNAVAILABLE) }
          .map {
            TrustedContactCardModel(
              contact = it,
              buttonText = "Failed",
              buttonTreatment = ButtonModel.Treatment.Warning,
              onClick = { props.onClick(it) }
            )
          },
        relationships.unendorsedTrustedContacts
          .filter { it.authenticationState == TrustedContactAuthenticationState.TAMPERED }
          .map {
            TrustedContactCardModel(
              contact = it,
              buttonText = "Invalid",
              buttonTreatment = ButtonModel.Treatment.Warning,
              onClick = { props.onClick(it) }
            )
          }
      ).flatten().toImmutableList()
    )
  }
}
