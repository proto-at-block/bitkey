package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import io.kotest.assertions.asClue

/**
 * Verify that [MoneyHomeCardsModel] has a card with the given [title]. Returns the card.
 */
fun MoneyHomeCardsModel.shouldHaveCard(title: String): CardModel {
  return asClue {
    cards.single {
      it.title?.string == title
    }
  }
}
