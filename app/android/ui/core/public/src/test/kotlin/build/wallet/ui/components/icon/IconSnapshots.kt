package build.wallet.ui.components.icon

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class IconSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("small icons") {
    paparazzi.snapshot {
      IconsSmallPreview()
    }
  }

  test("regular icons") {
    paparazzi.snapshot {
      IconsRegularPreview()
    }
  }

  test("large icons") {
    paparazzi.snapshot {
      IconsLargePreview()
    }
  }

  test("avatar icons") {
    paparazzi.snapshot {
      IconsAvatarPreview()
    }
  }

  test("tinted icons") {
    paparazzi.snapshot {
      IconsTintedPreview()
    }
  }
})
