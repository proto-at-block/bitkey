package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.titleString
import io.kotest.assertions.asClue

/**
 * Verify that [CardListModel] has a card with the given [title]. Returns the card.
 */
fun CardListModel.shouldHaveCard(title: String): CardModel {
  return asClue {
    cards.single {
      it.titleString == title
    }
  }
}
