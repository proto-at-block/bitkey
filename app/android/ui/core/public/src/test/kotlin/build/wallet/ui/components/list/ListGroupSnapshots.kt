package build.wallet.ui.components.list

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.model.list.ListGroupStyle
import io.kotest.core.spec.style.FunSpec

class ListGroupSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("list group with header") {
    paparazzi.snapshot {
      ListSectionForPreview(showHeader = true)
    }
  }

  test("list group without header") {
    paparazzi.snapshot {
      ListSectionForPreview(showHeader = false)
    }
  }

  test("list group action style") {
    paparazzi.snapshot {
      ListSectionForPreview(showHeader = false, style = ListGroupStyle.CARD_GROUP_DIVIDER)
    }
  }

  test("list group card style") {
    paparazzi.snapshot {
      ListSectionForPreview(showHeader = false, style = ListGroupStyle.CARD_ITEM)
    }
  }
})
