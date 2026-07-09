package build.wallet.ui.app.core.form

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList

class FormScreenModelResolutionTests : FunSpec({
  test("supports explicit header to main content spacing") {
    resolveHeaderToMainContentSpacing(
      model = TestFormBodyModel(
        header = FormHeaderModel(headline = "Headline"),
        headerToMainContentSpacing = 8
      ),
      headerModel = FormHeaderModel(headline = "Headline")
    ).shouldBe(8)
  }

  test("defaults to 16 spacing when there is no header") {
    resolveHeaderToMainContentSpacing(
      model = TestFormBodyModel(header = null),
      headerModel = null
    ).shouldBe(16)
  }

  test("defaults to 24 spacing when header has no subline") {
    val header = FormHeaderModel(headline = "Headline")

    resolveHeaderToMainContentSpacing(
      model = TestFormBodyModel(header = header),
      headerModel = header
    ).shouldBe(24)
  }

  test("defaults to 16 spacing when header includes subline") {
    val header = FormHeaderModel(
      headline = "Headline",
      subline = "Subline"
    )

    resolveHeaderToMainContentSpacing(
      model = TestFormBodyModel(header = header),
      headerModel = header
    ).shouldBe(16)
  }
})

private data class TestFormBodyModel(
  override val header: FormHeaderModel?,
  override val toolbar: ToolbarModel? = null,
  override val mainContentList: ImmutableList<FormMainContentModel> = immutableListOf(),
  override val primaryButton: ButtonModel? = null,
  override val secondaryButton: ButtonModel? = null,
  override val formScreenTitle: FormScreenTitleModel? = null,
  override val formScreenLayout: FormScreenLayoutModel = FormScreenLayoutModel.Legacy,
  override val headerToMainContentSpacing: Int? = null,
  override val footerRevealDelayMillis: Int = 0,
  override val preFooterContentList: ImmutableList<FormMainContentModel> = immutableListOf(),
) : FormBodyModel(
    id = null,
    onBack = {},
    toolbar = toolbar,
    header = header,
    mainContentList = mainContentList,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton,
    formScreenTitle = formScreenTitle,
    formScreenLayout = formScreenLayout,
    headerToMainContentSpacing = headerToMainContentSpacing,
    footerRevealDelayMillis = footerRevealDelayMillis,
    preFooterContentList = preFooterContentList
  )
