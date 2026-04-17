#include "power.h"

#include "attributes.h"
#include "battery.h"
#include "bitlog.h"
#include "exti.h"
#include "ipc.h"
#include "log.h"
#include "max17262.h"
#include "max77734.h"
#include "power_charger_ui.h"
#include "rtos.h"
#include "sleep.h"
#include "sysevent.h"
#include "ui_messaging.h"

static const uint32_t FIVE_V_BOOST_DELAY_MS = 5u;
static const uint32_t CHARGER_IRQ_TIMEOUT_MS = 100u;
static const uint32_t CHARGER_DEBOUNCE_MAX_MS = 45u;
// static const uint32_t USB_DETECT_IRQ_TIMEOUT_MS = 100u;
static const uint32_t FG_INIT_RETRY_MS = 1000u;

extern power_config_t power_config;

static const uint32_t CHARGER_BOOST_TIMEOUT_MS = 250u;
static const uint32_t CHARGER_BOOST_CUTOFF = 100 * 1000u;  // 100.000%
static const uint32_t FG_IRQ_TIMEOUT_MS = (5 * 60 * 1000u);
static const uint8_t SOC_DELTA_PERCENT = 1u;  // SOC threshold delta (1%)

// Extra time added to the base power timeout while USB is connected,
// giving the user slightly longer before the screen turns off while charging.
static const uint32_t CHARGER_SLEEP_EXTENSION_MS = 30000u;

static volatile bool charging_enabled_requested = true;

// Fuel gauge may fail to init when battery voltage is too low.
// This flag gates external requests for battery status information in that case.
static volatile max17262_status_t fuel_gauge_status = MAX17262_STATUS_UNINITIALISED;
static volatile bool cached_soc_valid = false;
static volatile uint32_t cached_soc_millipercent = 0;
static bool retain_charged_indicator = false;
static uint8_t last_reported_battery_percent = 0xFF;  // Invalid initial value

typedef struct {
  bool charging;
  bool plugged;
  bool chgin_irq_received;
  power_charger_mode_t mode;
} charger_snapshot_t;

typedef struct {
  bool active;
  bool boot_pending;
  bool replug_pending;
  bool boot_checked;
  uint32_t start_ms;
  uint8_t consecutive_charging_polls;
} charger_boost_state_t;

typedef struct {
  bool reapply_requested_charging_state;
  bool start_boost;
  bool stop_boost;
} charger_boost_actions_t;

static void charger_thread(void* args);
static void fuel_gauge_thread(void* args);
static void update_soc_thresholds(uint8_t current_soc);
static void apply_requested_charging_state(void);
static bool refresh_cached_soc(uint32_t* soc_millipercent_out);
static void show_charging_complete_event(void);
static void read_charger_snapshot(charger_snapshot_t* snapshot);
static bool charger_snapshot_matches(const charger_snapshot_t* lhs, const charger_snapshot_t* rhs);
static void debounce_charger_snapshot(charger_snapshot_t* snapshot);
static bool charger_boost_allows_ui_complete(const charger_boost_state_t* boost_state);
static charger_boost_actions_t update_charger_boost_state(const charger_snapshot_t* snapshot,
                                                          bool previous_charger_plugged,
                                                          charger_boost_state_t* boost_state);

void power_init(void) {
  // Note: power retain is configured and asserted by the bootloader
  mcu_gpio_configure(&power_config.five_volt_boost, false);
  mcu_gpio_configure(&power_config.cap_touch_detect.gpio, true);  // Enable pull-up

  rtos_thread_create(charger_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
  rtos_thread_create(fuel_gauge_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
}

static bool refresh_cached_soc(uint32_t* soc_millipercent_out) {
  uint32_t soc_millipercent = 0;
  if (!max17262_get_soc_millipercent(&soc_millipercent)) {
    cached_soc_valid = false;
    return false;
  }

  cached_soc_millipercent = soc_millipercent;
  cached_soc_valid = true;

  if (soc_millipercent_out != NULL) {
    *soc_millipercent_out = soc_millipercent;
  }

  return true;
}

static void show_charging_complete_event(void) {
  if (retain_charged_indicator) {
    UI_SHOW_EVENT(UI_EVENT_CHARGING_FINISHED_PERSISTENT);
  } else {
    UI_SHOW_EVENT(UI_EVENT_CHARGING_FINISHED);
  }
}

static void read_charger_snapshot(charger_snapshot_t* snapshot) {
  max77734_charging_status(&snapshot->charging, &snapshot->plugged, &snapshot->mode);
}

static bool charger_snapshot_matches(const charger_snapshot_t* lhs, const charger_snapshot_t* rhs) {
  return (lhs->charging == rhs->charging) && (lhs->plugged == rhs->plugged) &&
         (lhs->mode == rhs->mode);
}

static void debounce_charger_snapshot(charger_snapshot_t* snapshot) {
  const uint32_t start = rtos_thread_systime();
  charger_snapshot_t s1 = *snapshot;
  charger_snapshot_t s2 = *snapshot;
  charger_snapshot_t s3 = *snapshot;

  while (!RTOS_DEADLINE(start, CHARGER_DEBOUNCE_MAX_MS)) {
    do {
      read_charger_snapshot(&s1);
      rtos_thread_sleep(1);
      read_charger_snapshot(&s2);
      rtos_thread_sleep(1);
      read_charger_snapshot(&s3);
      rtos_thread_sleep(1);
      // Wait until the full hardware charger state stabilizes, but never
      // exceed the debounce deadline (avoids blocking the boost timeout
      // check if the status keeps flapping on a noisy adapter).
    } while ((!charger_snapshot_matches(&s1, &s2) || !charger_snapshot_matches(&s2, &s3)) &&
             !RTOS_DEADLINE(start, CHARGER_DEBOUNCE_MAX_MS));

    if (charger_snapshot_matches(&s1, &s2) && charger_snapshot_matches(&s2, &s3)) {
      snapshot->charging = s3.charging;
      snapshot->plugged = s3.plugged;
      snapshot->mode = s3.mode;
      break;
    }
  }
}

static bool charger_boost_allows_ui_complete(const charger_boost_state_t* boost_state) {
  return !boost_state->active && !boost_state->boot_pending && !boost_state->replug_pending;
}

static charger_boost_actions_t update_charger_boost_state(const charger_snapshot_t* snapshot,
                                                          bool previous_charger_plugged,
                                                          charger_boost_state_t* boost_state) {
  charger_boost_actions_t actions = {0};
  const bool charger_idle = snapshot->plugged && !snapshot->charging;

  if (snapshot->charging) {
    if (boost_state->consecutive_charging_polls < UINT8_MAX) {
      boost_state->consecutive_charging_polls++;
    }
  } else {
    boost_state->consecutive_charging_polls = 0;
  }
  const bool charging_session_established = (boost_state->consecutive_charging_polls >= 2);

  if (!boost_state->boot_checked && fuel_gauge_status == MAX17262_STATUS_OK) {
    boost_state->boot_checked = true;
    boost_state->boot_pending = snapshot->plugged;
  }

  if (snapshot->plugged && (snapshot->chgin_irq_received || !previous_charger_plugged)) {
    actions.reapply_requested_charging_state = true;
    if (!boost_state->active) {
      boost_state->replug_pending = true;
    }
  }

  const bool boost_pending = boost_state->boot_pending || boost_state->replug_pending;
  if (boost_pending) {
    bool boost_evaluated = false;
    if (charger_idle && fuel_gauge_status == MAX17262_STATUS_OK) {
      uint32_t soc_millipercent = 0;
      // Plug/replug decisions need a fresh REPSOC sample rather than the last
      // 1% alert / timeout snapshot from the fuel-gauge thread.
      if (refresh_cached_soc(&soc_millipercent)) {
        actions.start_boost = (soc_millipercent < CHARGER_BOOST_CUTOFF);
        boost_evaluated = true;
      }
    }

    // Consume the pending flags once the idle/SOC decision has been made,
    // once charging is sustained
    // (a single CHG pulse can still fall back to DONE on near-full packs),
    // or once the charger is unplugged.
    if (boost_evaluated || charging_session_established || !snapshot->plugged) {
      boost_state->boot_pending = false;
      boost_state->replug_pending = false;
    }
  }

  if (actions.start_boost) {
    boost_state->active = true;
    boost_state->start_ms = rtos_thread_systime();
  }

  if (boost_state->active &&
      (!snapshot->plugged || RTOS_DEADLINE(boost_state->start_ms, CHARGER_BOOST_TIMEOUT_MS))) {
    actions.stop_boost = true;
    boost_state->active = false;
  }

  return actions;
}

static void charger_thread(void* UNUSED(args)) {
  // Charger init, with charging disabled
  max77734_init(&power_config.ldo);
  max77734_validate();
  max77734_irq_enable(&power_config.charger_irq);
  max77734_enable_thermal_interrupts();

  // 5V Boost Enable
  mcu_gpio_output_set(&power_config.five_volt_boost, true);
  rtos_thread_sleep(FIVE_V_BOOST_DELAY_MS);

  // Enable charging
  power_enable_charging();

  sysevent_set(SYSEVENT_POWER_READY);

  charger_snapshot_t charger_snapshot = {.mode = POWER_CHARGER_MODE_INVALID};
  read_charger_snapshot(&charger_snapshot);

  bool prev_charge_input_valid = false;  // Previous CHGIN state for edge detection
  bool previous_charger_plugged = charger_snapshot.plugged;
  sysevent_wait(SYSEVENT_SLEEP_TIMER_READY, true);
  if (previous_charger_plugged) {
    sleep_set_charger_extension(CHARGER_SLEEP_EXTENSION_MS);
  }

  power_charger_ui_state_t ui_state = {0};
  charger_boost_state_t boost_state = {0};

#if 0
  // TODO: figure out why VBUS_DETECT doesn't work
  // Setup USB detect interrupt
  exti_enable(&power_config.usb_detect_irq);
  bool usb_connected = (bool)mcu_gpio_read(&power_config.usb_detect_irq.gpio);
#endif

  for (;;) {
    // Waits for a charger interrupt, then updates the local charging status
    bool irq_received = max77734_irq_wait(&power_config.charger_irq, CHARGER_IRQ_TIMEOUT_MS);
    charger_snapshot.chgin_irq_received = false;

    if (irq_received) {
      // Check for thermal events
      bool tjal1 = false, tjal2 = false, tj_reg = false;
      if (max77734_check_thermal_status(&tjal1, &tjal2, &tj_reg,
                                        &charger_snapshot.chgin_irq_received)) {
        // Only notify thermal task for actual thermal faults (TJAL1/TJAL2 @ 80°C/100°C)
        // TJ_REG @ 60°C is just normal thermal regulation (IC automatically throttling)
        if (tjal1 || tjal2) {
          sysevent_set(SYSEVENT_USB_THERMAL_FAULT);
        } else if (tj_reg) {
          LOGW("USB IC thermal reg");
        }
      }

      debounce_charger_snapshot(&charger_snapshot);
    } else {
      // Timeout - poll the current status without debouncing
      read_charger_snapshot(&charger_snapshot);
    }

#if 0
    // Wait for a USB detect event
    if (exti_wait(&power_config.usb_detect_irq, USB_DETECT_IRQ_TIMEOUT_MS, true)) {
      bool usb_connected = (bool)mcu_gpio_read(&power_config.usb_detect_irq.gpio);
      LOGI("USB_EVENT %u", usb_connected);
    }
#endif

#if 0
    // TODO(W-3755)
    static bool stopped_once = false;
    if (!hw_charge_input_valid) {
      // Charge input not valid
      // ui_charging_active = false;
      // static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_CHARGING_FINISHED,
      // .immediate = true}; ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
      if (!stopped_once) {
        UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
        stopped_once = true;
      }
      continue;
    }
#endif

    const bool charger_plugged = charger_snapshot.plugged;
    const charger_boost_actions_t boost_actions =
      update_charger_boost_state(&charger_snapshot, previous_charger_plugged, &boost_state);
    if (boost_actions.reapply_requested_charging_state) {
      // The IC resets ICHGIN_LIM to its POR default (95mA) when CHGIN is removed.
      // Reapply the requested CHG_EN state while restoring the full CNFG_CHG_B
      // register (including the 475mA input current limit) on every plug-in event.
      apply_requested_charging_state();
    }
    if (boost_actions.start_boost) {
      max77734_set_max_charge_cv(true);
    }
    if (boost_actions.stop_boost) {
      max77734_set_max_charge_cv(false);
    }

    // detect rising edge of charger plugged in
    if (charger_plugged && !prev_charge_input_valid) {
      power_enable_charging();
    }
    prev_charge_input_valid = charger_plugged;

    const bool allow_ui_complete = charger_boost_allows_ui_complete(&boost_state);
    const power_charger_ui_event_t ui_event = power_charger_ui_update_state(
      charger_snapshot.plugged, charger_snapshot.charging, charger_snapshot.mode, allow_ui_complete,
      cached_soc_valid, cached_soc_millipercent, &ui_state);
    switch (ui_event) {
      case POWER_CHARGER_UI_EVENT_CHARGING:
        UI_SHOW_EVENT(UI_EVENT_CHARGING);
        break;
      case POWER_CHARGER_UI_EVENT_COMPLETE:
        show_charging_complete_event();
        break;
      case POWER_CHARGER_UI_EVENT_UNPLUGGED:
        UI_SHOW_EVENT(UI_EVENT_CHARGING_UNPLUGGED);
        UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
        break;
      case POWER_CHARGER_UI_EVENT_NONE:
      default:
        break;
    }

    if (charger_plugged && !previous_charger_plugged) {
      // USB just plugged in — extend power timeout while charging
      sleep_set_charger_extension(CHARGER_SLEEP_EXTENSION_MS);
    } else if (!charger_plugged && previous_charger_plugged) {
      // USB just unplugged — restore base timeout
      sleep_set_charger_extension(0);
    }

    previous_charger_plugged = charger_plugged;
  }
}

static void fuel_gauge_thread(void* UNUSED(args)) {
  // Initialise fuel gauge and bitlog the initial power-on status
  fuel_gauge_status = max17262_init();
  BITLOG_EVENT(fuel_gauge_init, fuel_gauge_status);

  for (;;) {
    switch (fuel_gauge_status) {
      case MAX17262_STATUS_ERR:    /* falls-through */
      case MAX17262_STATUS_FAILED: /* falls-through */
      case MAX17262_STATUS_UNINITIALISED:
        cached_soc_valid = false;
        rtos_thread_sleep(FG_INIT_RETRY_MS);
        fuel_gauge_status = max17262_init();
        break;

      case MAX17262_STATUS_POWER_ON_RESET: /* falls-through */
      case MAX17262_STATUS_MODELGAUGE_UNINITIALISED:
        cached_soc_valid = false;
        fuel_gauge_status =
          max17262_por_initialise() ? MAX17262_STATUS_OK : MAX17262_STATUS_POWER_ON_RESET;
        break;

      case MAX17262_STATUS_OK: {
        // Configure fuel gauge interrupts
        max17262_enable_alerts(true);
        max17262_configure_soc_alerts(true);  // Enable 1% change alerts
        max17262_clear_alerts();

        // Enable fuel gauge interrupt
        exti_enable(&power_config.fuel_gauge_irq);

        // Get initial SOC and set thresholds
        uint32_t soc_millipercent = 0;
        if (!refresh_cached_soc(&soc_millipercent)) {
          LOGW("FG: init SOC err");
          rtos_thread_sleep(FG_INIT_RETRY_MS);
          break;
        }
        uint8_t battery_percent = (uint8_t)(soc_millipercent / 1000);
        last_reported_battery_percent = battery_percent;

        // Set initial thresholds
        update_soc_thresholds(battery_percent);

        // Send initial battery SOC event
        battery_soc_data_t battery_data = {.battery_percent = battery_percent};
        UI_SHOW_EVENT_WITH_DATA(UI_EVENT_BATTERY_SOC, &battery_data, sizeof(battery_data));

        // Monitor battery using interrupts
        for (;;) {
          // Wait for fuel gauge interrupt
          bool irq_received = exti_wait(&power_config.fuel_gauge_irq, FG_IRQ_TIMEOUT_MS, true);

          if (irq_received) {
            // Handle fuel gauge interrupt
            max17262_soc_alert_t soc_alert = MAX17262_SOC_ALERT_NONE;
            if (!max17262_get_soc_alert(&soc_alert)) {
              LOGW("FG: alert err");
              continue;
            }

            if (soc_alert != MAX17262_SOC_ALERT_NONE) {
              // SOC changed - read new value
              if (!refresh_cached_soc(&soc_millipercent)) {
                LOGW("FG: SOC rd err");
                max17262_clear_alerts();
                continue;
              }
              battery_percent = (uint8_t)(soc_millipercent / 1000);

              // Send update if battery changed
              if (battery_percent != last_reported_battery_percent) {
                uint8_t old_soc = last_reported_battery_percent;
                last_reported_battery_percent = battery_percent;

                LOGI("FG SOC Update: %u%% -> %u%%", old_soc, battery_percent);

                // Send battery SOC event
                battery_data.battery_percent = battery_percent;
                UI_SHOW_EVENT_WITH_DATA(UI_EVENT_BATTERY_SOC, &battery_data, sizeof(battery_data));

                // Update thresholds
                update_soc_thresholds(battery_percent);
              }
            }

            // Clear the alerts
            max17262_clear_alerts();
          } else {
            // Read SOC as backup in case we missed interrupts
            if (!refresh_cached_soc(&soc_millipercent)) {
              LOGW("FG: poll err");
              continue;
            }
            battery_percent = (uint8_t)(soc_millipercent / 1000);

            if (battery_percent != last_reported_battery_percent) {
              LOGE("FG no IRQ: %u->%u%%", last_reported_battery_percent, battery_percent);
              last_reported_battery_percent = battery_percent;
              battery_data.battery_percent = battery_percent;
              UI_SHOW_EVENT_WITH_DATA(UI_EVENT_BATTERY_SOC, &battery_data, sizeof(battery_data));

              // Update thresholds
              update_soc_thresholds(battery_percent);
            }
          }
        }
        break;
      }
      default: {
        break;
      }
    }
  }
}

void power_set_retain(const bool enabled) {
  mcu_gpio_output_set(&power_config.power_retain, enabled);
}

bool power_validate_fuel_gauge(void) {
  if (fuel_gauge_status == MAX17262_STATUS_OK) {
    return max17262_validate();
  }
  return false;
}

void power_get_battery(uint32_t* soc_millipercent, uint32_t* vcell_mv, int32_t* avg_current_ma,
                       uint32_t* cycles) {
  max17262_regdump_t regs = {0};
  if (fuel_gauge_status == MAX17262_STATUS_OK) {
    max17262_get_regdump(&regs);
  }
  *soc_millipercent = regs.soc;
  *vcell_mv = regs.vcell;
  *avg_current_ma = regs.avg_current;
  *cycles = regs.cycles;
}

void power_fast_charge(void) {
  max77734_fast_charge();
}

bool power_set_battery_variant(const uint32_t variant) {
  return battery_set_variant(variant);
}

void power_retain_charged_indicator(void) {
  retain_charged_indicator = true;
}

void power_enable_charging(void) {
  charging_enabled_requested = true;
  apply_requested_charging_state();
}

void power_disable_charging(void) {
  charging_enabled_requested = false;
  apply_requested_charging_state();
}

void power_usb_suspend(bool enabled) {
  max77734_usb_suspend(enabled);
}

bool power_is_charging(void) {
  bool charging;
  bool valid;
  (void)valid;

  // No cache, direct register read.
  max77734_charging_status(&charging, &valid, NULL);
  return charging;
}

bool power_is_plugged_in(void) {
  bool charging;
  bool valid;
  (void)charging;

  max77734_charging_status(&charging, &valid, NULL);
  return valid;
}

power_charger_id power_get_charger_id(void) {
  return POWER_CHARGER_MAX77734;
}

power_charger_mode_t power_get_charger_mode(void) {
  return max77734_get_mode();
}

uint8_t power_get_charger_register_count(void) {
  return max77734_get_register_count();
}

void power_read_charger_register(uint8_t index, uint8_t* offset_out, uint8_t* value_out) {
  max77734_read_register(index, offset_out, value_out);
}

static void apply_requested_charging_state(void) {
  max77734_charge_enable(charging_enabled_requested);
}

void power_set_ldo_low_power_mode(void) {
  max77734_set_ldo_low_power_mode();
}

void power_disable_ldo(void) {
  max77734_disable_ldo();
}

static void update_soc_thresholds(uint8_t current_soc) {
  uint8_t min_threshold = (current_soc >= SOC_DELTA_PERCENT) ? current_soc - SOC_DELTA_PERCENT : 0;
  uint8_t max_threshold = (current_soc < 100) ? current_soc + SOC_DELTA_PERCENT : 100;

  if (!max17262_set_soc_thresholds(min_threshold, max_threshold)) {
    LOGW("SOC thresh err");
  }
}
