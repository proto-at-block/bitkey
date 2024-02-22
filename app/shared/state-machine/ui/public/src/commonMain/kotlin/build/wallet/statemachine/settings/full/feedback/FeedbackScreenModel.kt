package build.wallet.statemachine.settings.full.feedback

import build.wallet.compose.collections.immutableListOf
import build.wallet.email.Email
import build.wallet.ui.model.input.TextFieldModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class Feedback(
  val email: Email,
  val location: Country?,
  val category: FeedbackCategory?,
  val subcategory: FeedbackSubcategory?,
  val customFields: Map<FeedbackCustomField, *>,
  val subject: String,
  val description: String,
  val sendDebugData: Boolean,
)

/**
 * Creates a [FeedbackScreenModel] to display the Feedback screen
 *
 * @param onBack - Invoked once the back action is called
 * @param onContactUsClick - Invoked once the contact us link is clicked
 */
data class Country(
  val isoCode: String,
  val displayName: String,
)

enum class FeedbackCategory(
  val title: String,
  val subcategories: ImmutableList<FeedbackSubcategory>? = null,
) {
  FeedbackAndSuggestions("Feedback & Suggestions"),
  OrdersAndShipping(
    "Orders & Shipping",
    FeedbackSubcategory.OrdersAndShipping.entries.toImmutableList()
  ),
  SettingUpWallet("Setting up my wallet"),
  HardwareProblems("Problems with my hardware"),
  MobileApp("Mobile App"),
  TransactionsAndBalance("Transactions & wallet balance"),
  WalletRecovery("Wallet recovery"),
  Partners("Exchange / Custodial Partners"),
  Privacy("Privacy Request or Query"),
  Other("Other"),
  ;

  fun isValid(subcategory: FeedbackSubcategory?) =
    subcategories == null || subcategory in subcategories
}

sealed interface FeedbackSubcategory {
  val title: String
  val customFields: ImmutableList<FeedbackCustomField>?

  fun isValid(customFieldValues: Map<FeedbackCustomField, Any>): Boolean {
    return customFields?.all { it.isValid(customFieldValues[it]) } ?: true
  }

  enum class OrdersAndShipping(
    override val title: String,
    override val customFields: ImmutableList<FeedbackCustomField>? = null,
  ) : FeedbackSubcategory {
    CancelOrder(
      title = "Cancel Order",
      customFields =
        immutableListOf(
          FeedbackCustomField.OrderNumber,
          FeedbackCustomField.ReasonForCancellation
        )
    ),
    ResentEmail("Resend confirmation/shipping email"),
    BulkOrders("Bulk Orders"),
    RefundStatus("Refund status"),
    RequestCountry("Request Bitkey in my country"),
    LostShipment("Lost or stuck shipment"),
    Misdelivery("Package misdelivered"),
    Import("Import receipt request or customs hold"),
    ShippingDamage("Device arrived damaged"),
    Other("Other"),
  }
}

sealed interface FeedbackCustomField {
  val id: String
  val title: String

  fun isValid(value: Any?): Boolean

  sealed interface TextField : FeedbackCustomField {
    val placeholder: String
    val keyboardType: TextFieldModel.KeyboardType
      get() = TextFieldModel.KeyboardType.Default

    override fun isValid(value: Any?): Boolean =
      value != null && value is String && value.isNotBlank()
  }

  sealed interface Picker<Option : Picker.Item> : FeedbackCustomField {
    val options: ImmutableList<Option>

    override fun isValid(value: Any?): Boolean = value in options

    interface Item {
      val title: String
    }
  }

  data object OrderNumber : TextField {
    override val id = "order_number"
    override val title = "Order number"
    override val placeholder = "0118 999 881 999 119 725 3"
    override val keyboardType = TextFieldModel.KeyboardType.Number
  }

  data object ReasonForCancellation : Picker<ReasonForCancellation.Reason> {
    override val id = "cancellation_reason"
    override val title = "Reason for cancellation"
    override val options: ImmutableList<Reason> = Reason.entries.toImmutableList()

    enum class Reason(
      override val title: String,
    ) : Picker.Item {
      Other("Other"),
    }
  }
}
