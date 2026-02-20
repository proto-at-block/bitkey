package build.wallet.ui.components.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeDatePickerDialog(
  initialDate: LocalDate?,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  // Validate date constraints
  require(minDate == null || maxDate == null || minDate <= maxDate) {
    "minDate ($minDate) must be less than or equal to maxDate ($maxDate)"
  }

  // Create the date picker
  val datePicker = remember(initialDate, minDate, maxDate) {
    UIDatePicker().apply {
      datePickerMode = UIDatePickerMode.UIDatePickerModeDate
      preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
      // Set min date if provided
      minDate?.toNSDate()?.let { nsDate ->
        minimumDate = nsDate
      }

      // Set max date if provided
      maxDate?.toNSDate()?.let { nsDate ->
        maximumDate = nsDate
      }

      // Set initial date if provided, ensuring it's within bounds
      val dateToSet = initialDate?.let { date ->
        when {
          minDate != null && date < minDate -> minDate
          maxDate != null && date > maxDate -> maxDate
          else -> date
        }
      }

      dateToSet?.toNSDate()?.let { nsDate ->
        date = nsDate
      }
    }
  }

  // Create and remember the picker view controller
  val pickerViewController = remember(datePicker) {
    DatePickerViewController(
      datePicker = datePicker,
      onConfirm = {
        val selectedNSDate = datePicker.date
        val timeInterval = selectedNSDate.timeIntervalSince1970
        val instant = Instant.fromEpochSeconds(timeInterval.toLong(), 0)
        val selectedDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        onDateSelected(selectedDate)
        onDismiss()
      },
      onCancel = onDismiss
    ).apply {
      modalPresentationStyle = UIModalPresentationPageSheet
      setPreferredContentSize(
        platform.CoreGraphics.CGSizeMake(
          0.0, // 0 means full width
          460.0 // Header (44) + picker space (240) + footer (124) + separators/padding (~52)
        )
      )
      sheetPresentationController?.apply {
        setDetents(
          listOf(
            UISheetPresentationControllerDetent.mediumDetent()
          )
        )
        setPrefersGrabberVisible(true) // Show the drag indicator
        setPrefersScrollingExpandsWhenScrolledToEdge(false)
        setPrefersEdgeAttachedInCompactHeight(true)
      }
    }
  }

  DisposableEffect(datePicker, pickerViewController) {
    // Present the sheet over the current view controller
    dispatch_async(dispatch_get_main_queue()) {
      getCurrentViewController()?.presentViewController(pickerViewController, animated = true, completion = null)
    }

    onDispose {
      // Dismiss the picker if still presented when composable leaves composition
      if (pickerViewController.presentingViewController != null) {
        pickerViewController.dismissViewControllerAnimated(false, completion = null)
      }
    }
  }
}

/**
 * Custom view controller for the date picker bottom sheet.
 * Automatically inherits the theme from the root view controller.
 */
@OptIn(ExperimentalForeignApi::class)
private class DatePickerViewController(
  private val datePicker: UIDatePicker,
  private val onConfirm: () -> Unit,
  private val onCancel: () -> Unit,
) : UIViewController(nibName = null, bundle = null), UIAdaptivePresentationControllerDelegateProtocol {
  private var hasConfirmed = false

  override fun viewDidLoad() {
    super.viewDidLoad()

    // Set this as the presentation controller delegate to handle dismissal
    presentationController?.delegate = this

    val view = this.view
    view.backgroundColor = UIColor.systemBackgroundColor

    // Create header container with just title
    val headerContainer = UIView()
    headerContainer.translatesAutoresizingMaskIntoConstraints = false

    // Title label
    val titleLabel = UILabel().apply {
      text = "Select Date"
      font = UIFont.boldSystemFontOfSize(17.0)
      textAlignment = NSTextAlignmentCenter
      translatesAutoresizingMaskIntoConstraints = false
    }

    headerContainer.addSubview(titleLabel)

    // Separator line below header
    val topSeparator = UIView().apply {
      backgroundColor = UIColor.separatorColor
      translatesAutoresizingMaskIntoConstraints = false
    }

    // Footer container with buttons (no background - transparent)
    val footerContainer = UIView()
    footerContainer.translatesAutoresizingMaskIntoConstraints = false
    // No background color - inherits from main view

    // Cancel button - full width, transparent background with red text
    val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
      setTitle("Cancel", forState = UIControlStateNormal)
      titleLabel.font = UIFont.systemFontOfSize(17.0)
      setTitleColor(UIColor.systemRedColor, forState = UIControlStateNormal)
      backgroundColor = UIColor.clearColor // No background
      layer.cornerRadius = 12.0
      layer.borderWidth = 0.0
      clipsToBounds = true
      addTarget(
        target = this@DatePickerViewController,
        action = platform.darwin.sel_registerName("cancelTapped"),
        forControlEvents = UIControlEventTouchUpInside
      )
      translatesAutoresizingMaskIntoConstraints = false
    }

    // Confirm button - full width with blue background
    val confirmButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
      setTitle("Confirm", forState = UIControlStateNormal)
      titleLabel.font = UIFont.boldSystemFontOfSize(17.0)
      backgroundColor = UIColor.systemBlueColor
      setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
      layer.cornerRadius = 12.0
      clipsToBounds = true
      addTarget(
        target = this@DatePickerViewController,
        action = platform.darwin.sel_registerName("confirmTapped"),
        forControlEvents = UIControlEventTouchUpInside
      )
      translatesAutoresizingMaskIntoConstraints = false
    }

    footerContainer.addSubview(cancelButton)
    footerContainer.addSubview(confirmButton)

    // Add all views to main view
    view.addSubview(headerContainer)
    view.addSubview(topSeparator)
    view.addSubview(datePicker)
    view.addSubview(footerContainer)

    // Layout
    datePicker.translatesAutoresizingMaskIntoConstraints = false

    NSLayoutConstraint.activateConstraints(
      listOf(
        // Header at top (with spacing below drag handle)
        headerContainer.topAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.topAnchor, constant = 8.0),
        headerContainer.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
        headerContainer.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
        headerContainer.heightAnchor.constraintEqualToConstant(44.0),
        // Title centered in header
        titleLabel.centerXAnchor.constraintEqualToAnchor(headerContainer.centerXAnchor),
        titleLabel.centerYAnchor.constraintEqualToAnchor(headerContainer.centerYAnchor),
        // Top separator line
        topSeparator.topAnchor.constraintEqualToAnchor(headerContainer.bottomAnchor),
        topSeparator.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
        topSeparator.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
        topSeparator.heightAnchor.constraintEqualToConstant(0.5),
        // Footer at bottom
        footerContainer.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
        footerContainer.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
        footerContainer.bottomAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.bottomAnchor),
        footerContainer.heightAnchor.constraintEqualToConstant(124.0), // Space for two buttons + padding: 16 + 50 + 8 + 50 = 124
        // Confirm button at top of footer
        confirmButton.topAnchor.constraintEqualToAnchor(footerContainer.topAnchor, constant = 16.0),
        confirmButton.leadingAnchor.constraintEqualToAnchor(footerContainer.leadingAnchor, constant = 16.0),
        confirmButton.trailingAnchor.constraintEqualToAnchor(footerContainer.trailingAnchor, constant = -16.0),
        confirmButton.heightAnchor.constraintEqualToConstant(50.0),
        // Cancel button below confirm button
        cancelButton.topAnchor.constraintEqualToAnchor(confirmButton.bottomAnchor, constant = 8.0),
        cancelButton.leadingAnchor.constraintEqualToAnchor(footerContainer.leadingAnchor, constant = 16.0),
        cancelButton.trailingAnchor.constraintEqualToAnchor(footerContainer.trailingAnchor, constant = -16.0),
        cancelButton.heightAnchor.constraintEqualToConstant(50.0),
        // Date picker fills space between header and footer
        datePicker.topAnchor.constraintEqualToAnchor(topSeparator.bottomAnchor, constant = 20.0),
        datePicker.bottomAnchor.constraintEqualToAnchor(footerContainer.topAnchor, constant = -20.0),
        datePicker.centerXAnchor.constraintEqualToAnchor(view.centerXAnchor),
        datePicker.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor, constant = 0.0),
        datePicker.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, constant = 0.0)
      )
    )
  }

  @kotlinx.cinterop.ObjCAction
  fun cancelTapped() {
    hasConfirmed = true // Mark as handled to prevent double-calling onCancel
    dismissViewControllerAnimated(true) {
      onCancel()
    }
  }

  @kotlinx.cinterop.ObjCAction
  fun confirmTapped() {
    hasConfirmed = true // Mark as confirmed
    dismissViewControllerAnimated(true) {
      onConfirm()
    }
  }

  // UIAdaptivePresentationControllerDelegate method
  // Called when the sheet is dismissed by tapping outside or swiping down
  override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
    // Only call onCancel if the user didn't explicitly tap Cancel or Confirm
    if (!hasConfirmed) {
      onCancel()
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentViewController(): UIViewController? {
  val keyWindow = UIApplication.sharedApplication.windows.firstOrNull {
    (it as? UIWindow)?.isKeyWindow() == true
  } as? UIWindow

  return keyWindow?.rootViewController
}

private fun LocalDate.toNSDate(): NSDate? {
  val components = NSDateComponents().apply {
    year = this@toNSDate.year.toLong()
    month = this@toNSDate.monthNumber.toLong()
    day = this@toNSDate.dayOfMonth.toLong()
  }
  return NSCalendar.currentCalendar.dateFromComponents(components)
}
