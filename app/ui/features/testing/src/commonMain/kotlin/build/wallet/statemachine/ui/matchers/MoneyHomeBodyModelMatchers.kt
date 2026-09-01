package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.subtitleString
import build.wallet.statemachine.moneyhome.card.titleString
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.ui.robots.protectedCustomersCard
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

fun LiteMoneyHomeBodyModel.hasProtectedCustomers(): Boolean {
  val card = protectedCustomersCard() as? CardModel.DrillList ?: return false
  return card.items.size > 0
}

fun CardModel.shouldHaveTitle(title: String) =
  apply {
    this.titleString.shouldNotBeNull().shouldBe(title)
  }

fun CardModel.shouldHaveSubtitle(subtitle: String) =
  apply {
    this.subtitleString.shouldBe(subtitle)
  }

fun CardModel.shouldNotHaveSubtitle() =
  apply {
    this.subtitleString.shouldBeNull()
  }
