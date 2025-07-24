package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import build.wallet.balance.utils.DataQuality
import build.wallet.balance.utils.MockConfiguration
import build.wallet.balance.utils.MockPriceScenario
import build.wallet.balance.utils.MockScenarioService
import build.wallet.balance.utils.MockTransactionScenario
import build.wallet.compose.collections.buildImmutableList
import build.wallet.pricechart.BalanceHistoryService
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.coroutines.launch

/**
 * Debug menu group for selecting mock price scenarios.
 */
@Composable
fun ProvideMockPriceScenariosGroup(
  mockScenarioService: MockScenarioService,
  onConfigurationChanged: () -> Unit,
  refreshTrigger: Int,
): ListGroupModel? {
  val coroutineScope = rememberCoroutineScope()
  var currentPriceScenario by remember { mutableStateOf<MockPriceScenario?>(null) }

  LaunchedEffect(refreshTrigger) {
    currentPriceScenario = mockScenarioService.currentPriceScenario()
  }

  return ListGroupModel(
    header = "Price Data Scenarios",
    items = buildImmutableList {
      add(
        ListItemModel(
          title = "Live Price Data (Default)",
          trailingAccessory = if (currentPriceScenario == null) ListItemAccessory.checkIcon() else null,
          onClick = {
            coroutineScope.launch {
              mockScenarioService.clearScenarios(clearPrice = true, clearTransaction = false)
              currentPriceScenario = mockScenarioService.currentPriceScenario()
              onConfigurationChanged()
            }
          }
        )
      )

      // Seed-based scenarios
      MockPriceScenario.entries.forEach { scenario ->
        add(
          ListItemModel(
            title = scenario.displayName,
            trailingAccessory = if (currentPriceScenario == scenario) ListItemAccessory.checkIcon() else null,
            onClick = {
              coroutineScope.launch {
                mockScenarioService.setPriceScenario(scenario)
                currentPriceScenario = mockScenarioService.currentPriceScenario()
                onConfigurationChanged()
              }
            }
          )
        )
      }
    },
    style = ListGroupStyle.DIVIDER
  )
}

/**
 * Debug menu group for selecting mock transaction scenarios.
 */
@Composable
fun ProvideMockTransactionScenariosGroup(
  mockScenarioService: MockScenarioService,
  onConfigurationChanged: () -> Unit,
  refreshTrigger: Int,
): ListGroupModel? {
  val coroutineScope = rememberCoroutineScope()
  var currentTransactionScenario by remember { mutableStateOf<MockTransactionScenario?>(null) }

  LaunchedEffect(refreshTrigger) {
    currentTransactionScenario = mockScenarioService.currentTransactionScenario()
  }

  return ListGroupModel(
    header = "Transaction Data Scenarios",
    items = buildImmutableList {
      add(
        ListItemModel(
          title = "Live Transaction Data (Default)",
          trailingAccessory = if (currentTransactionScenario == null) ListItemAccessory.checkIcon() else null,
          onClick = {
            coroutineScope.launch {
              mockScenarioService.clearScenarios(clearPrice = false, clearTransaction = true)
              currentTransactionScenario = mockScenarioService.currentTransactionScenario()
              onConfigurationChanged()
            }
          }
        )
      )

      // Seed-based scenarios
      MockTransactionScenario.entries.forEach { scenario ->
        add(
          ListItemModel(
            title = scenario.displayName,
            trailingAccessory = if (currentTransactionScenario == scenario) ListItemAccessory.checkIcon() else null,
            onClick = {
              coroutineScope.launch {
                mockScenarioService.setTransactionScenario(scenario)
                currentTransactionScenario = mockScenarioService.currentTransactionScenario()
                onConfigurationChanged()
              }
            }
          )
        )
      }
    },
    style = ListGroupStyle.DIVIDER
  )
}

/**
 * Debug menu group for mock chart data controls and information.
 */
@Composable
fun ProvideMockChartDataControlsGroup(
  mockScenarioService: MockScenarioService,
  onShowSeedInput: () -> Unit,
  onSeedCopied: (String) -> Unit,
  refreshTrigger: Int,
): ListGroupModel? {
  val coroutineScope = rememberCoroutineScope()
  var currentConfig by remember { mutableStateOf<MockConfiguration?>(null) }

  LaunchedEffect(refreshTrigger) {
    currentConfig = mockScenarioService.currentMockConfiguration()
  }

  return ListGroupModel(
    header = "Mock Data Controls",
    items = buildImmutableList {
      // Show current seed if mock data is active
      currentConfig?.let { config ->
        add(
          ListItemModel(
            title = "Current Seed: ${config.seed}",
            secondaryText = "Tap to copy • Generated at ${config.generatedAt}",
            onClick = {
              onSeedCopied(config.seed.toString())
            }
          )
        )
      }

      add(
        ListItemModel(
          title = "Seed Options",
          trailingAccessory = ListItemAccessory.drillIcon(),
          onClick = onShowSeedInput
        )
      )
    },
    style = ListGroupStyle.DIVIDER
  )
}

/**
 * Debug menu group for selecting price data quality - only shown when mock price data is active.
 */
@Composable
fun ProvideMockDataQualityGroup(
  mockScenarioService: MockScenarioService,
  onConfigurationChanged: () -> Unit,
  refreshTrigger: Int,
): ListGroupModel? {
  val coroutineScope = rememberCoroutineScope()
  var currentPriceScenario by remember { mutableStateOf<MockPriceScenario?>(null) }
  var currentDataQuality by remember { mutableStateOf<DataQuality?>(null) }

  LaunchedEffect(refreshTrigger) {
    currentPriceScenario = mockScenarioService.currentPriceScenario()
    currentDataQuality = mockScenarioService.currentDataQuality()
  }

  // Only show this group if mock price data is active (data quality affects price data, not transactions)
  if (currentPriceScenario == null) {
    return null
  }

  return ListGroupModel(
    header = "Price Data Quality",
    items = buildImmutableList {
      DataQuality.entries.forEach { quality ->
        add(
          ListItemModel(
            title = quality.displayName,
            trailingAccessory = if (currentDataQuality == quality) ListItemAccessory.checkIcon() else null,
            onClick = {
              coroutineScope.launch {
                mockScenarioService.setDataQuality(quality)
                currentDataQuality = mockScenarioService.currentDataQuality()
                onConfigurationChanged()
              }
            }
          )
        )
      }
    },
    style = ListGroupStyle.DIVIDER
  )
}
