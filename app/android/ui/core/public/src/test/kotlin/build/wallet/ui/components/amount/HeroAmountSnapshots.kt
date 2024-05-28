package build.wallet.ui.components.amount

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class HeroAmountSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("hero amount full ghosted") {
    paparazzi.snapshot {
      HeroAmountWithFullGhosted()
    }
  }

  test("hero amount some ghosted") {
    paparazzi.snapshot {
      HeroAmountWithSomeGhosted()
    }
  }

  test("hero amount none ghosted") {
    paparazzi.snapshot {
      HeroAmountWithNoGhosted()
    }
  }

  test("hero amount with swap button") {
    paparazzi.snapshot {
      HeroAmountSwappable()
    }
  }

  test("hero amount disabled") {
    paparazzi.snapshot {
      HeroAmountDisabled()
    }
  }

  test("hero amount with no secondary amount") {
    paparazzi.snapshot {
      HeroAmountNoSecondary()
    }
  }

  test("hero amount with hidden amount") {
    paparazzi.snapshot {
      HeroAmountHideAmount()
    }
  }
})
