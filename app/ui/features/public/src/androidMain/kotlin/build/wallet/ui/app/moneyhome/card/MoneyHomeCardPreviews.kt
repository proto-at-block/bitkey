@file:Suppress("detekt:TooManyFunctions")

package build.wallet.ui.app.moneyhome.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.*
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.pricechart.DataPoint
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedTaskRowModel
import build.wallet.statemachine.moneyhome.lite.card.BuyOwnBitkeyMoneyHomeCardModel
import build.wallet.statemachine.moneyhome.lite.card.InheritanceMoneyHomeCard
import build.wallet.statemachine.moneyhome.lite.card.WalletsProtectingMoneyHomeCardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryCardModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactCardModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE
import kotlinx.datetime.Instant.Companion.DISTANT_PAST
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Preview
@Composable
fun PreviewMoneyHomePriceCard(
  isLoading: Boolean = false,
  price: String = "$90,000.00",
) {
  MoneyHomeCard(
    model =
      CardModel(
        title = null,
        content = BitcoinPrice(
          isLoading = isLoading,
          priceChange = "10.00% today",
          priceDirection = PriceDirection.UP,
          lastUpdated = "Updated 12:00am",
          price = price,
          data = generateChartData(150)
            .takeUnless { isLoading }
            ?: immutableListOf()
        ),
        style = Outline
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomePriceCardLoading() {
  PreviewMoneyHomePriceCard(isLoading = true)
}

@Preview(
  fontScale = 1.5f
)
@Composable
fun PreviewMoneyHomePriceCardLargeFont() {
  PreviewMoneyHomePriceCard(
    isLoading = false,
    price = "$100,000.00"
  )
}

@Preview(
  fontScale = 2f
)
@Composable
fun PreviewMoneyHomePriceCardHugeFont() {
  PreviewMoneyHomePriceCard(
    isLoading = false,
    price = "$100,000.00"
  )
}

@Preview
@Composable
fun PreviewMoneyHomeGettingStarted() {
  MoneyHomeCard(
    model =
      GettingStartedCardModel(
        animations = null,
        taskModels =
          immutableListOf(
            GettingStartedTaskRowModel(
              task = GettingStartedTask(AddBitcoin, Incomplete),
              isEnabled = true,
              onClick = {}
            ),
            GettingStartedTaskRowModel(
              task = GettingStartedTask(EnableSpendingLimit, Incomplete),
              isEnabled = false,
              onClick = {}
            ),
            GettingStartedTaskRowModel(
              task = GettingStartedTask(InviteTrustedContact, Complete),
              isEnabled = true,
              onClick = {}
            ),
            GettingStartedTaskRowModel(
              task = GettingStartedTask(AddAdditionalFingerprint, Incomplete),
              isEnabled = true,
              onClick = {}
            )
          )
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardInvitationPending() {
  MoneyHomeCard(
    model =
      TrustedContactCardModel(
        contact =
          Invitation(
            relationshipId = "foo",
            trustedContactAlias = TrustedContactAlias("Bela"),
            code = "token",
            codeBitLength = 20,
            expiresAt = DISTANT_FUTURE,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
        buttonText = "Pending",
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardInvitationExpired() {
  MoneyHomeCard(
    model =
      TrustedContactCardModel(
        contact =
          Invitation(
            relationshipId = "foo",
            trustedContactAlias = TrustedContactAlias("Bela"),
            code = "token",
            codeBitLength = 20,
            expiresAt = DISTANT_PAST,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
        buttonText = "Expired",
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardReplacementPending() {
  MoneyHomeCard(
    model =
      HardwareRecoveryCardModel(
        title = "Replacement pending...",
        subtitle = "2 days remaining",
        delayPeriodProgress = Progress.Half,
        delayPeriodRemainingSeconds = 0,
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardReplacementReady() {
  MoneyHomeCard(
    model =
      HardwareRecoveryCardModel(
        title = "Replacement Ready",
        delayPeriodProgress = Progress.Full,
        delayPeriodRemainingSeconds = 0,
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardWalletsProtecting() {
  MoneyHomeCard(
    model =
      WalletsProtectingMoneyHomeCardModel(
        protectedCustomers =
          immutableListOf(
            ProtectedCustomer(
              relationshipId = "",
              alias = ProtectedCustomerAlias("Alice"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            ),
            ProtectedCustomer(
              relationshipId = "",
              alias = ProtectedCustomerAlias("Bob"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
        onProtectedCustomerClick = {},
        onAcceptInviteClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBuyOwnBitkey() {
  MoneyHomeCard(
    model = BuyOwnBitkeyMoneyHomeCardModel(onClick = {})
  )
}

@Preview
@Composable
fun PreviewInheritanceMoneyHomeCard() {
  MoneyHomeCard(
    model = InheritanceMoneyHomeCard(
      onIHaveABitkey = {},
      onGetABitkey = {}
    )
  )
}

private fun generateChartData(pointCount: Int): ImmutableList<DataPoint> {
  return buildImmutableList {
    for (i in 0 until pointCount) {
      val y = abs(sin(i * PI / 30) * 20 + cos(i * PI / 15) * 10)
      add(DataPoint(i.toLong(), y))
    }
  }
}
