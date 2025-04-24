package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.CardModel
import io.kotest.assertions.asClue

/**
 * Verify that [CardListModel] has a card with the given [title]. Returns the card.
 */
fun CardListModel.shouldHaveCard(title: String): CardModel {
  return asClue {
    cards.single {
      it.title?.string == title
    }
  }
}
