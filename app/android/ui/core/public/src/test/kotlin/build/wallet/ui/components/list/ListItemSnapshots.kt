package build.wallet.ui.components.list

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class ListItemSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("list item with leading icon") {
    paparazzi.snapshot {
      ListItemWithLeadingIconPreview()
    }
  }

  test("list item with single line text") {
    paparazzi.snapshot {
      SingleLineListItemPreview()
    }
  }

  test("list item with trailing switch") {
    paparazzi.snapshot {
      ListItemWithTrailingSwitch()
    }
  }

  test("list item with trailing switch - design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      ListItemWithTrailingSwitch()
    }
  }

  test("list item with trailing icon") {
    paparazzi.snapshot {
      ListItemWithTrailingIcon()
    }
  }

  test("list item with trailing button") {
    paparazzi.snapshot {
      ListItemWithTrailingButton()
    }
  }
})
