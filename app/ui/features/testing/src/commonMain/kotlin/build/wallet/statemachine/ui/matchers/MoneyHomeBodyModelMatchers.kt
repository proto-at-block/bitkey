package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.ui.robots.protectedCustomersCard
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

fun LiteMoneyHomeBodyModel.hasProtectedCustomers(): Boolean {
  return protectedCustomersCard()
    ?.content
    ?.shouldBeTypeOf<CardModel.CardContent.DrillList>()
    ?.let { it.items.size > 0 }
    ?: false
}

fun CardModel.shouldHaveTitle(title: String) =
  apply {
    this.title.shouldNotBeNull().string.shouldBe(title)
  }

fun CardModel.shouldHaveSubtitle(subtitle: String) =
  apply {
    this.subtitle.shouldBe(subtitle)
  }

fun CardModel.shouldNotHaveSubtitle() =
  apply {
    this.subtitle.shouldBeNull()
  }
