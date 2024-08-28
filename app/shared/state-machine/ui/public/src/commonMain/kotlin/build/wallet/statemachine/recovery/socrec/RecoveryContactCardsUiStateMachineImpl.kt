package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.FAILED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
import build.wallet.compose.collections.immutableListOf
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.button.ButtonModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock

class RecoveryContactCardsUiStateMachineImpl(
  private val clock: Clock,
  private val socRecService: SocRecService,
) : RecoveryContactCardsUiStateMachine {
  @Composable
  override fun model(props: RecoveryContactCardsUiProps): ImmutableList<CardModel> {
    val relationships = remember { socRecService.relationships }
      .collectAsState().value ?: return immutableListOf()

    return listOf(
      relationships.invitations
        .map {
          RecoveryContactCardModel(
            contact = it,
            buttonText = if (it.isExpired(clock)) {
              "Expired"
            } else {
              "Pending"
            },
            onClick = { props.onClick(it) }
          )
        },
      relationships.unendorsedTrustedContacts
        .filter { it.authenticationState in setOf(FAILED, PAKE_DATA_UNAVAILABLE) }
        .map {
          RecoveryContactCardModel(
            contact = it,
            buttonText = "Failed",
            buttonTreatment = ButtonModel.Treatment.Warning,
            onClick = { props.onClick(it) }
          )
        },
      relationships.unendorsedTrustedContacts
        .filter { it.authenticationState == TrustedContactAuthenticationState.TAMPERED }
        .map {
          RecoveryContactCardModel(
            contact = it,
            buttonText = "Invalid",
            buttonTreatment = ButtonModel.Treatment.Warning,
            onClick = { props.onClick(it) }
          )
        }
    ).flatten().toImmutableList()
  }
}
