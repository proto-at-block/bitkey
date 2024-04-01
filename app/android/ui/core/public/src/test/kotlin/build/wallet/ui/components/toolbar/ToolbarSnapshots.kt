package build.wallet.ui.components.toolbar

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class ToolbarSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("empty toolbar") {
    paparazzi.snapshot {
      EmptyToolbar()
    }
  }

  test("toolbar with all contents") {
    paparazzi.snapshot {
      ToolbarWithAllContentsPreview()
    }
  }

  test("toolbar with leading only content") {
    paparazzi.snapshot {
      ToolbarWithLeadingContentOnlyPreview()
    }
  }

  test("toolbar with middle only content") {
    paparazzi.snapshot {
      ToolbarWithMiddleContentOnlyPreview()
    }
  }

  test("toolbar with trailing only content label") {
    paparazzi.snapshot {
      ToolbarWithTrailingContentOnlyLabelPreview()
    }
  }

  test("toolbar with trailing only content button") {
    paparazzi.snapshot {
      ToolbarWithTrailingContentOnlyButtonPreview()
    }
  }

  test("toolbar with leading and middle content") {
    paparazzi.snapshot {
      ToolbarWithLeadingAndMiddleContentPreview()
    }
  }

  test("toolbar with leading and trailing content") {
    paparazzi.snapshot {
      ToolbarWithLeadingAndTrailingContentPreview()
    }
  }

  test("toolbar with middle and trailing content") {
    paparazzi.snapshot {
      ToolbarWithMiddleAndTrailingContentPreview()
    }
  }
})
