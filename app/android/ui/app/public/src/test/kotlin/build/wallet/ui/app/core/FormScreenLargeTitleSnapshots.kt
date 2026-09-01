package build.wallet.ui.app.core

import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FooterRevealAware
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormPreFooterContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.toolbar.ToolbarTitleModel
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Layout contract snapshots for the [FormScreen] large-title layouts
 * ([FormScreenLayoutModel.LargeTitle]) and their interactions with footer reveal, pre-footer
 * content, sheet rendering, and the legacy layout inference.
 *
 * These exist as a safety net for refactoring FormScreen.kt — each test pins one layout
 * variant so regressions surface as snapshot diffs rather than production layout bugs.
 */
class FormScreenLargeTitleSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("large title - scrollable (default)") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle()
        )
      )
    }
  }

  test("large title - scrollable with eyebrow") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(),
          eyebrow = "STEP 1 OF 2"
        )
      )
    }
  }

  test("large title - fixed, top alignment") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(
            scrollable = false,
            mainContentVerticalAlignment = FormMainContentVerticalAlignment.TOP
          )
        )
      )
    }
  }

  test("large title - fixed, center alignment") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(
            scrollable = false,
            mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
          )
        )
      )
    }
  }

  test("large title - fixed, bottom alignment") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(
            scrollable = false,
            mainContentVerticalAlignment = FormMainContentVerticalAlignment.BOTTOM
          )
        )
      )
    }
  }

  test("large title - fixed, no footer") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(scrollable = false),
          hasFooter = false
        )
      )
    }
  }

  test("large title - footer reveal hidden") {
    paparazzi.snapshot {
      FormScreen(
        model = FooterRevealFixtureBodyModel(footerRevealed = false)
      )
    }
  }

  test("large title - footer reveal shown") {
    paparazzi.snapshot {
      FormScreen(
        model = FooterRevealFixtureBodyModel(footerRevealed = true)
      )
    }
  }

  test("large title - pre-footer collapsible address") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(
            scrollable = false,
            mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
          ),
          preFooterContentList = immutableListOf(
            FormMainContentModel.CollapsibleAddress(
              address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc",
              label = "DESTINATION ADDRESS"
            )
          )
        )
      )
    }
  }

  test("large title - pre-footer header block") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.LargeTitle(
            scrollable = false,
            mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
          ),
          preFooterContentList = immutableListOf(
            FormMainContentModel.HeaderBlock(
              header = FormHeaderModel(
                headline = "Pre-footer headline",
                subline = "Supporting text shown above the footer buttons.",
                iconModel = null,
                alignment = FormHeaderModel.Alignment.CENTER,
                sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
              )
            )
          )
        )
      )
    }
  }

  test("large title - sheet render context") {
    paparazzi.snapshotSheet(
      model = LargeTitleFixtureBodyModel(
        layout = FormScreenLayoutModel.LargeTitle(),
        renderContext = RenderContext.Sheet,
        hasToolbarAccessory = false
      )
    )
  }

  // Pins that a Legacy layout is never silently upgraded to LargeTitle when the toolbar has a
  // large title: layout selection is explicit via FormScreenLayoutModel only. A large toolbar
  // title on a Legacy screen renders the legacy layout (the large title block is not drawn).
  test("legacy layout - large toolbar title does not upgrade layout") {
    paparazzi.snapshot {
      FormScreen(
        model = LargeTitleFixtureBodyModel(
          layout = FormScreenLayoutModel.Legacy
        )
      )
    }
  }
})

/**
 * Minimal large-title form fixture: large toolbar title, header-block main content, and a
 * primary/secondary footer.
 */
private data class LargeTitleFixtureBodyModel(
  val layout: FormScreenLayoutModel,
  val eyebrow: String? = null,
  val hasFooter: Boolean = true,
  val hasToolbarAccessory: Boolean = true,
  override val preFooterContentList: ImmutableList<FormPreFooterContentModel> =
    persistentListOf(),
  override val renderContext: RenderContext = RenderContext.Screen,
) : FormBodyModel(
    id = null,
    onBack = {},
    toolbar = ToolbarModel(
      leadingAccessory = if (hasToolbarAccessory) BackAccessory(onClick = {}) else null,
      title = ToolbarTitleModel.Large(
        title = "Confirm on your Bitkey",
        eyebrow = eyebrow
      )
    ),
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = "Main content headline",
          subline = "Body copy explaining what the customer should do on this screen.",
          iconModel = null,
          alignment = FormHeaderModel.Alignment.CENTER,
          sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
        )
      )
    ),
    primaryButton = if (hasFooter) {
      ButtonModel(
        text = "Confirm",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {}
      )
    } else {
      null
    },
    secondaryButton = if (hasFooter) {
      ButtonModel(
        text = "Cancel",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {}
      )
    } else {
      null
    },
    renderContext = renderContext,
    formScreenLayout = layout,
    preFooterContentList = preFooterContentList
  )

/**
 * Fixture opting into the [FooterRevealAware] capability so snapshots pin both the hidden
 * (space reserved, buttons not yet visible) and revealed footer states.
 */
private data class FooterRevealFixtureBodyModel(
  override val footerRevealed: Boolean,
) : FooterRevealAware, FormBodyModel(
    id = null,
    onBack = {},
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = {}),
      title = ToolbarTitleModel.Large(title = "Confirm on your Bitkey")
    ),
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = "Waiting for hardware",
          subline = "The footer buttons fade in once the hardware interaction completes.",
          iconModel = null,
          alignment = FormHeaderModel.Alignment.CENTER,
          sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Confirm",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {}
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {}
    ),
    formScreenLayout = FormScreenLayoutModel.LargeTitle(
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
    )
  )
