package build.wallet.ui.components.icon

import build.wallet.statemachine.core.Icon
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IconStyleTests : FunSpec({
  test("dot icons still support tint") {
    Icon.DotSecurity.canApplyTint() shouldBe true
    Icon.DotRecoveryContact.canApplyTint() shouldBe true
    Icon.DotRecoveryContact2.canApplyTint() shouldBe true
    Icon.DotCriticalAlerts2.canApplyTint() shouldBe true
    Icon.DotIconsSearch.canApplyTint() shouldBe true
  }

  test("standard local icons still support tint") {
    Icon.SmallIconShield.canApplyTint() shouldBe true
    Icon.SmallIconSettings.canApplyTint() shouldBe true
  }
})
