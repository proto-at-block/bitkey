package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.core.form.FormScreenAllContentsNoFooterPreview
import build.wallet.ui.app.core.form.FormScreenAllContentsNoMainAndFooterContentPreview
import build.wallet.ui.app.core.form.FormScreenAllContentsNoMainContentPreview
import build.wallet.ui.app.core.form.FormScreenAllContentsNoToolbarPreview
import build.wallet.ui.app.core.form.FormScreenAllContentsPreview
import build.wallet.ui.app.core.form.FormScreenNotFullHeightPreview
import io.kotest.core.spec.style.FunSpec

class FormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("form screen - all contents") {
    paparazzi.snapshot {
      FormScreenAllContentsPreview()
    }
  }

  test("form screen - all contents, no toolbar") {
    paparazzi.snapshot {
      FormScreenAllContentsNoToolbarPreview()
    }
  }
  test("form screen - all contents, no footer") {
    paparazzi.snapshot {
      FormScreenAllContentsNoFooterPreview()
    }
  }

  test("form screen - all contents, no main content") {
    paparazzi.snapshot {
      FormScreenAllContentsNoMainContentPreview()
    }
  }

  test("form screen - all contents, no main content and footer") {
    paparazzi.snapshot {
      FormScreenAllContentsNoMainAndFooterContentPreview()
    }
  }

  test("form screen - all contents,not full height") {
    paparazzi.snapshot {
      FormScreenNotFullHeightPreview()
    }
  }
})
