package build.wallet.statemachine.recovery.socrec.list

import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.bitkey.relationships.UnendorsedProtectedCustomerFake
import build.wallet.ui.model.list.ListItemSideTextTint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProtectedCustomerListItemModelTests : FunSpec({
  test("endorsed protected customer shows active") {
    val model = ProtectedCustomerFake.listItemModel {}

    model.secondaryText.shouldBe("Active")
    model.secondaryTextTint.shouldBe(ListItemSideTextTint.GREEN)
  }

  test("unendorsed protected customer shows pending") {
    val model = UnendorsedProtectedCustomerFake.listItemModel {}

    model.secondaryText.shouldBe("Pending")
    model.secondaryTextTint.shouldBe(ListItemSideTextTint.SECONDARY)
  }
})
