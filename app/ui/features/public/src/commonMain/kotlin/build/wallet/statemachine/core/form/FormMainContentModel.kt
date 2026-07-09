package build.wallet.statemachine.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import bitkey.account.HardwareType
import build.wallet.Progress
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.CaretRight
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.app.core.form.UpsellContainer
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.datetime.DatePickerModel
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.picker.ItemPickerModel
import build.wallet.ui.model.tab.CircularTabRowModel
import build.wallet.ui.tokens.LabelType
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface FormMainContentModel {
  /**
   * A content object used to add space between elements as needed.
   */
  data class Spacer(
    /** The amount of space, or null if it should try to fill as much space as possible. */
    val height: Float? = null,
  ) : FormMainContentModel

  /**
   * A basic horizontal divider line.
   */
  data object Divider : FormMainContentModel

  /**
   * A container that allows the bitkey image/video and callout to live together
   */
  data class DeviceStatusCard(
    val deviceImage: IconModel? = null,
    val deviceVideo: VideoContent? = null,
    val deviceSerialNumber: String? = null,
    val deviceBatteryPercentage: Int? = null,
    val hardwareType: HardwareType = HardwareType.W3,
    val statusCallout: CalloutModel,
  ) : FormMainContentModel {
    init {
      require((deviceImage != null) || (deviceVideo != null)) {
        "DeviceStatusCard must have either deviceImage or deviceVideo, but not both"
      }
    }

    enum class VideoContent {
      BITKEY_ROTATE,
    }
  }

  /**
   * Allows device page to have same list styling as settings screen
   */
  data class SettingsList(
    val header: String,
    val items: ImmutableList<SettingsListItem>,
  ) : FormMainContentModel {
    data class SettingsListItem(
      val title: String,
      val icon: IconModel,
      val isEnabled: Boolean = true,
      val treatment: ListItemTreatment = ListItemTreatment.PRIMARY,
      val onClick: (() -> Unit)?,
    ) {
      constructor(
        title: String,
        icon: Icon,
        isEnabled: Boolean = true,
        treatment: ListItemTreatment = ListItemTreatment.PRIMARY,
        onClick: (() -> Unit)?,
      ) : this(
          title = title,
          icon = IconModel(icon = icon, iconSize = IconSize.Small),
          isEnabled = isEnabled,
          treatment = treatment,
          onClick = onClick
        )

    }
  }

  /**
   * A display list of text items with a title and subtext with a leading icon aligned to the
   * top-left of the title and text.
   */
  data class Explainer(
    val items: ImmutableList<Statement>,
  ) : FormMainContentModel {
    data class Statement(
      val leadingIcon: Icon? = null,
      val leadingIconSize: IconSize = IconSize.Small,
      val leadingContentTopPaddingDp: Int = 0,
      val leadingContentSpacingDp: Int = 16,
      val leadingText: String? = null,
      val leadingTextType: LabelType = LabelType.Body2MonoCaps,
      val leadingTextLabelTreatment: LabelTreatment? = null,
      val title: String?,
      val body: LabelModel,
      val titleLabelType: LabelType = LabelType.Body2Bold,
      val treatment: Treatment = Treatment.PRIMARY,
      val titleLabelTreatment: LabelTreatment? = null,
      val bodyType: LabelType = LabelType.Body2Regular,
      val bodyLabelTreatment: LabelTreatment? = null,
    ) {
      enum class Treatment {
        PRIMARY,
        WARNING,
      }

      constructor(
        leadingIcon: Icon? = null,
        title: String?,
        body: String,
        titleLabelType: LabelType = LabelType.Body2Bold,
        treatment: Treatment = Treatment.PRIMARY,
        leadingIconSize: IconSize = IconSize.Small,
      ) :
        this(
          leadingIcon = leadingIcon,
          leadingIconSize = leadingIconSize,
          title = title,
          body = StringModel(body),
          titleLabelType = titleLabelType,
          treatment = treatment
        )
    }
  }

  /**
   * A large image above a title and body message, fully center aligned.
   */
  data class Showcase(
    val content: Content,
    val title: String? = null,
    val body: LabelModel? = null,
    val fillAvailableSpace: Boolean = true,
  ) : FormMainContentModel {
    sealed interface Content {
      data class IconContent(
        val icon: Icon,
        val widthDp: Int? = null,
        val heightDp: Int? = null,
      ) : Content {
        init {
          require((widthDp == null) == (heightDp == null)) {
            "IconContent widthDp and heightDp must both be null or both be set."
          }
        }
      }

      data class VideoContent(
        val video: Video,
        val hardwareType: HardwareType = HardwareType.W3,
      ) : Content {
        enum class Video {
          BITKEY_ROTATE,
          ;

          open val looping: Boolean = false

          open val scalingMode: VideoScalingMode = VideoScalingMode.FIT
        }
      }

      data class ImageContent(
        val image: Image,
        val scale: Float = 1f,
      ) : Content {
        init {
          require(scale > 0f) {
            "ImageContent scale must be greater than 0."
          }
        }

        enum class Image {
          BITKEY_TILT,
          UPGRADE_W3,
          UPGRADE_W3_UP_DOWN,
        }
      }
    }

  }

  /**
   * A centered header-style text block that can be placed in form main content.
   */
  data class HeaderBlock(
    val header: FormHeaderModel,
  ) : FormMainContentModel

  /**
   * A display list of data with a left-aligned label and a right-aligned primary and secondary
   * data and an optional "total" row that will be displayed at the bottom.
   */
  @Redacted
  data class DataList(
    val hero: DataHero? = null,
    val items: ImmutableList<Data>,
    val total: Data? = null,
    val buttons: ImmutableList<ButtonModel> = emptyImmutableList(),
    val containerStyle: ContainerStyle = ContainerStyle.DEFAULT,
  ) : FormMainContentModel {
    init {
      require(items.isNotEmpty())
    }

    /**
     * A section of a data list that is shown at the top of the data set
     *
     * @property image - the image shown in the hero
     * @property title - optional title of the hero, null when there is none
     * @property subtitle - optional subtitle of the hero, null when there is none
     * @property button - an optional action button in the hero
     */
    data class DataHero(
      val image: IconModel?,
      val title: String?,
      val subtitle: String?,
      val button: ButtonModel?,
    )

    data class Data(
      val title: String,
      val titleIcon: IconModel? = null,
      val onTitle: (() -> Unit)? = null,
      val titleTextType: TitleTextType = TitleTextType.REGULAR,
      val secondaryTitle: String? = null,
      val sideText: String,
      val sideTextType: SideTextType = SideTextType.MEDIUM,
      val sideTextTreatment: SideTextTreatment = SideTextTreatment.PRIMARY,
      val secondarySideText: String? = null,
      val secondarySideTextType: SideTextType = SideTextType.REGULAR,
      val secondarySideTextTreatment: SideTextTreatment = SideTextTreatment.SECONDARY,
      val showBottomDivider: Boolean = false,
      val explainer: Explainer? = null,
      val onClick: (() -> Unit)? = null,
      // only displayed if onClick is not null
      val endIcon: Icon = CaretRight,
      val endIconTint: IconTint = IconTint.On30,
    ) {
      enum class TitleTextType { REGULAR, BODY2REGULAR, BODY1REGULAR, BOLD }

      enum class SideTextType { REGULAR, MEDIUM, BOLD, BODY2BOLD, BODY2REGULAR, BODY1REGULAR }

      enum class SideTextTreatment { PRIMARY, SECONDARY, WARNING, STRIKETHROUGH }

      data class Explainer(
        val title: String,
        val subtitle: String,
        val iconButton: IconButtonModel? = null,
        val showTopDivider: Boolean = false,
      )
    }

    enum class ContainerStyle {
      DEFAULT,
      BORDERLESS,
    }
  }

  /**
   * A selectable list of fee options.
   * Only one option can be selected at a time, like a radio button.
   */
  @Redacted
  data class FeeOptionList(
    val options: ImmutableList<FeeOption>,
  ) : FormMainContentModel {
    init {
      require(options.isNotEmpty())
    }

    /**
     * UI Model for showing a fee option in a [FeeOptionList]
     *
     * @property optionName - the title text of the option
     * @property transactionTime - the estimated settle time of the option
     * @property transactionFee - the cost of the selected option
     * @property selected - whether the option is currently selected or not
     * @property enabled - whether the option is enabled and able to be selected
     * @property infoText - the text to be shown in the info box of an option, null when none available
     * @property onClick - click handler for an option, null when there is none
     */
    data class FeeOption(
      val optionName: String,
      val transactionTime: String,
      val transactionFee: String,
      val selected: Boolean,
      val enabled: Boolean,
      val infoText: String? = null,
      val onClick: (() -> Unit)?,
    )
  }

  /**
   * An input field specifically for verification codes
   * This is distinct from [TextInput] because verification code inputs display text beneath
   * the input.
   * TODO (W-2828): Enable "text" as a main content to remove this custom main content type
   */
  data class VerificationCodeInput(
    val fieldModel: TextFieldModel,
    val expectedCodeLength: Int,
    val resendCodeContent: ResendCodeContent,
  ) : FormMainContentModel {
    sealed interface ResendCodeContent {
      data class Text(val value: String) : ResendCodeContent

      data class Button(val value: ButtonModel) : ResendCodeContent {
        constructor(onSendCodeAgain: () -> Unit, isLoading: Boolean) : this(
          value =
            ButtonModel(
              text = "Send code again",
              isLoading = isLoading,
              treatment = ButtonModel.Treatment.Tertiary,
              size = ButtonModel.Size.Compact,
              onClick = StandardClick(onSendCodeAgain)
            )
        )
      }
    }
  }

  /**
   * A text input field.
   * @property title - Optional text shown above the input field to describe what it is for
   */
  @Redacted
  data class TextInput(
    val title: String? = null,
    val fieldModel: TextFieldModel,
  ) : FormMainContentModel

  /**
   * A multiline input field.
   *
   * @property title - Optional text shown above the text area to describe what it is for
   */
  @Redacted
  data class TextArea(
    val title: String? = null,
    val fieldModel: TextFieldModel,
  ) : FormMainContentModel

  /**
   * An input field with an optional trailing button contained inside
   * for pasting clipboard contents
   */
  @Redacted
  data class AddressInput(
    val fieldModel: TextFieldModel,
    val trailingButtonModel: ButtonModel?,
  ) : FormMainContentModel

  /**
   * A field allowing user to pick a date
   */
  @Redacted
  data class DatePicker(
    val title: String? = null,
    val fieldModel: DatePickerModel,
  ) : FormMainContentModel

  data class Picker(
    val title: String? = null,
    val fieldModel: ItemPickerModel<*>,
  ) : FormMainContentModel

  /**
   * A circular progress indicator to display a countdown by showing progress
   * along the circle. The title and subtitle are displayed inside the circle.
   * @param timerProgress: The progress as a percentage
   * @param timerRemainingSeconds: The progress as the remaining time in seconds.
   * @param direction: The direction of the timer (clockwise, filling - counter-clockwise, emptying)
   * iOS uses this to create an animation for the progress, rather that 1 second
   * delayed model updates.
   */
  data class Timer(
    val title: String,
    val subtitle: String,
    val timerProgress: Progress,
    val direction: TimerDirection,
    val timerRemainingSeconds: Long,
    val display: Display = Display.Text(title = title, subtitle = subtitle),
    val style: Style = Style.PRIMARY,
  ) : FormMainContentModel {
    sealed interface Display {
      data class Text(
        val title: String,
        val subtitle: String,
      ) : Display

      data class RemainingDuration(
        val duration: Duration,
        val enableLocalSecondsTick: Boolean,
        val showSecondsBelow: Duration = 60.seconds,
        val subtitle: String = "remaining",
      ) : Display
    }

    enum class Style {
      PRIMARY,
      FOREGROUND,
    }
  }

  /**
   * Will display a list using the [ListGroupModel]
   */
  data class ListGroup(
    val listGroupModel: ListGroupModel,
  ) : FormMainContentModel

  data class AnnotatedText(
    val text: AnnotatedString,
    val type: LabelType = LabelType.Body2Regular,
    val treatment: LabelTreatment = LabelTreatment.Primary,
    val alignment: TextAlign = TextAlign.Start,
    val onClick: ((Int) -> Unit)? = null,
  ) : FormMainContentModel

  /**
   * A linear progress indicator with labeled icons as "steps".
   */
  data class StepperIndicator(
    val steps: ImmutableList<Step>,
  ) : FormMainContentModel {
    /**
     * A step on the progress indicator. Each step is represented by an icon enclosed
     * within a circle on the line, with a label underneath the circle.
     */
    data class Step(
      val style: StepStyle,
      val icon: IconImage?,
      val label: String,
    )

    /**
     * The style to be attributed to the step
     */
    enum class StepStyle {
      /**
       * The step is in progress; uses the highlighted stepper color for the circle and next line.
       */
      PENDING,

      /**
       * The step is completed; uses the highlighted stepper color for the circle and next line.
       */
      COMPLETED,

      /**
       * The step is still upcoming; uses the inactive stepper color for the circle and next line.
       */
      UPCOMING,
    }
  }

  data object Loader : FormMainContentModel

  /**
   * A loading treatment using the dots loader artwork.
   */
  data object DotLoader : FormMainContentModel

  /**
   * Allows a [CalloutModel] to be rendered in the [FormMainContentModel] list
   * @property item - the [CalloutModel] to be rendered
   */
  data class Callout(
    val item: CalloutModel,
  ) : FormMainContentModel

  /**
   * Allows a [CardModel] to be rendered in the [FormMainContentModel] list
   * @property item - the [CalloutModel] to be rendered
   */
  data class CalloutCard(
    val item: CardModel,
  ) : FormMainContentModel

  /**
   * A circular tab row that allows the user to select between different tabs.
   */
  data class CircularTabRow(
    val item: CircularTabRowModel,
  ) : FormMainContentModel

  /**
   * A collapsible address section with a chevron toggle and label.
   * Used to display a destination address that can be expanded/collapsed.
   *
   * @property address The address text to display when expanded.
   * @property label The label shown next to the chevron (e.g. "DESTINATION ADDRESS").
   */
  @Redacted
  data class CollapsibleAddress(
    val address: String,
    val label: String,
  ) : FormMainContentModel

  /**
   * An information container with two action buttons and hero icon image.
   */
  data class Upsell(
    val iconModel: IconModel,
    val title: String,
    val body: String,
    val primaryButton: ButtonModel,
    val secondaryButton: ButtonModel,
  ) : FormMainContentModel, ComposeModel {
    override val key: String = "upsell"

    @Composable
    override fun render(modifier: Modifier) {
      UpsellContainer(
        modifier = modifier,
        model = this
      )
    }
  }

  /**
   * Allows a bespoke compose model to be embedded in form main content.
   */
  data class CustomContent(
    val item: ComposeModel,
  ) : FormMainContentModel
}
