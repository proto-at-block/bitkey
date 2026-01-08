#include "power.h"

#include "attributes.h"
#include "battery.h"
#include "bitlog.h"
#include "exti.h"
#include "ipc.h"
#include "log.h"
#include "max17262.h"
#include "max77734.h"
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

static rtos_timer_t charger_boost_timer = {0};
static const uint32_t CHARGER_BOOST_TIMEOUT_MS = 250u;
static const uint32_t CHARGER_BOOST_CUTOFF = 100 * 1000u;  // 100.000%
static const uint32_t FG_IRQ_TIMEOUT_MS = (5 * 60 * 1000u);
static const uint8_t SOC_DELTA_PERCENT = 1u;  // SOC threshold delta (1%)

// Fuel gauge may fail to init when battery voltage is too low.
// This flag gates external requests for battery status information in that case.
static max17262_status_t fuel_gauge_status = MAX17262_STATUS_UNINITIALISED;
static bool retain_charged_indicator = false;
static uint8_t last_reported_battery_percent = 0xFF;  // Invalid initial value

static void charger_thread(void* args);
static void fuel_gauge_thread(void* args);
static void charger_boost_callback(rtos_timer_handle_t timer);
static void update_soc_thresholds(uint8_t current_soc);

void power_init(void) {
  // Note: power retain is configured and asserted by the bootloader
  mcu_gpio_configure(&power_config.five_volt_boost, false);
  mcu_gpio_configure(&power_config.cap_touch_detect.gpio, true);  // Enable pull-up

  rtos_thread_create(charger_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
  rtos_thread_create(fuel_gauge_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
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

  // Charging state variables
  static bool hw_charging_active = false;     // Hardware: battery is actively charging
  static bool hw_charge_input_valid = false;  // Hardware: valid power input connected
  static bool ui_charging_active = false;     // UI state: currently showing charging indication
  static bool charging_complete = false;      // Charging finished but still plugged in

  // Load initial values
  max77734_charging_status(&hw_charging_active, &hw_charge_input_valid);

#if 0
  // TODO: figure out why VBUS_DETECT doesn't work
  // Setup USB detect interrupt
  exti_enable(&power_config.usb_detect_irq);
  bool usb_connected = (bool)mcu_gpio_read(&power_config.usb_detect_irq.gpio);
#endif

  for (;;) {
    // Waits for a charger interrupt, then updates the local charging status
    bool irq_received = max77734_irq_wait(&power_config.charger_irq, CHARGER_IRQ_TIMEOUT_MS);

    if (irq_received) {
      // Check for thermal events
      bool tjal1 = false, tjal2 = false, tj_reg = false;
      if (max77734_check_thermal_status(&tjal1, &tjal2, &tj_reg)) {
        // Only notify thermal task for actual thermal faults (TJAL1/TJAL2 @ 80°C/100°C)
        // TJ_REG @ 60°C is just normal thermal regulation (IC automatically throttling)
        if (tjal1 || tjal2) {
          sysevent_set(SYSEVENT_USB_THERMAL_FAULT);
        } else if (tj_reg) {
          LOGW("USB IC thermal regulation active (60°C) - charging current reduced");
        }
      }

      // Debounce on IRQ
      const uint32_t start = rtos_thread_systime();
      while (!RTOS_DEADLINE(start, CHARGER_DEBOUNCE_MAX_MS)) {
        bool c1;
        bool c2;
        bool c3;

        do {
          max77734_charging_status(&c1, &hw_charge_input_valid);
          rtos_thread_sleep(1);
          max77734_charging_status(&c2, &hw_charge_input_valid);
          rtos_thread_sleep(1);
          max77734_charging_status(&c3, &hw_charge_input_valid);
          rtos_thread_sleep(1);
          // Wait until hardware charging status stabilizes.
        } while (!((c1 == c2) && (c2 == c3)));
        hw_charging_active = c1;
      }
    } else {
      // Timeout - poll the current status without debouncing
      max77734_charging_status(&hw_charging_active, &hw_charge_input_valid);
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

    const bool charger_plugged = hw_charge_input_valid;
    const bool battery_is_charging = hw_charging_active;
    const bool battery_is_full = charger_plugged && !battery_is_charging;

    if (charger_plugged) {
      // Charger is connected
      if (battery_is_full) {
        // Battery full, charger still plugged
        if (ui_charging_active) {
          // Transition from charging to complete
          ui_charging_active = false;
          charging_complete = true;
          // Send charging finished event
          if (retain_charged_indicator) {
            UI_SHOW_EVENT(UI_EVENT_CHARGING_FINISHED_PERSISTENT);
          } else {
            UI_SHOW_EVENT(UI_EVENT_CHARGING_FINISHED);
          }
        } else if (!charging_complete && !ui_charging_active) {
          // Plugged in with full battery (wasn't charging before)
          charging_complete = true;
          // Send charging event to show charger is connected
          UI_SHOW_EVENT(UI_EVENT_CHARGING);
        }
      } else if (battery_is_charging) {
        // Actively charging
        if (!ui_charging_active) {
          // Charging just started
          ui_charging_active = true;
          UI_SHOW_EVENT(UI_EVENT_CHARGING);
        }
      }
      sleep_refresh_power_timer();
    } else {
      // Charger disconnected
      if (ui_charging_active || charging_complete) {
        // Was charging or showing complete - now unplugged
        ui_charging_active = false;
        charging_complete = false;
        // Send unplugged event for W3 display
        UI_SHOW_EVENT(UI_EVENT_CHARGING_UNPLUGGED);
        // Also clear LED for W1
        UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
      }
    }
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
        rtos_thread_sleep(FG_INIT_RETRY_MS);
        fuel_gauge_status = max17262_init();
        break;

      case MAX17262_STATUS_POWER_ON_RESET: /* falls-through */
      case MAX17262_STATUS_MODELGAUGE_UNINITIALISED:
        fuel_gauge_status =
          max17262_por_initialise() ? MAX17262_STATUS_OK : MAX17262_STATUS_POWER_ON_RESET;
        break;

      case MAX17262_STATUS_OK: {
        // Trick charger into top-off when battery is close to full
        if (fuel_gauge_status == MAX17262_STATUS_OK &&
            max17262_soc_millipercent() < CHARGER_BOOST_CUTOFF) {
          // Wait for charger to be initialised
          sysevent_wait(SYSEVENT_POWER_READY, true);
          max77734_set_max_charge_cv(true);
          rtos_timer_create_static(&charger_boost_timer, charger_boost_callback);
          rtos_timer_start(&charger_boost_timer, CHARGER_BOOST_TIMEOUT_MS);
        }

        // Configure fuel gauge interrupts
        max17262_enable_alerts(true);
        max17262_configure_soc_alerts(true);  // Enable 1% change alerts
        max17262_clear_alerts();

        // Enable fuel gauge interrupt
        exti_enable(&power_config.fuel_gauge_irq);

        // Get initial SOC and set thresholds
        uint8_t battery_percent = (uint8_t)(max17262_soc_millipercent() / 1000);
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
              LOGW("FG: Failed to get SOC alert status");
              continue;
            }

            if (soc_alert != MAX17262_SOC_ALERT_NONE) {
              // SOC changed - read new value
              battery_percent = (uint8_t)(max17262_soc_millipercent() / 1000);

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
            battery_percent = (uint8_t)(max17262_soc_millipercent() / 1000);

            if (battery_percent != last_reported_battery_percent) {
              LOGE("FG: SOC changed without interrupt (timeout polling): %u%% -> %u%%",
                   last_reported_battery_percent, battery_percent);
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
  max77734_charge_enable(true);
}

void power_disable_charging(void) {
  max77734_charge_enable(false);
}

void power_usb_suspend(bool enabled) {
  max77734_usb_suspend(enabled);
}

bool power_is_charging(void) {
  bool charging;
  bool valid;
  (void)valid;

  // No cache, direct register read.
  max77734_charging_status(&charging, &valid);
  return charging;
}

bool power_is_plugged_in(void) {
  bool charging;
  bool valid;
  (void)charging;

  max77734_charging_status(&charging, &valid);
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

void power_set_ldo_low_power_mode(void) {
  max77734_set_ldo_low_power_mode();
}

NO_OPTIMIZE static void charger_boost_callback(rtos_timer_handle_t UNUSED(timer)) {
  max77734_set_max_charge_cv(false);
}

static void update_soc_thresholds(uint8_t current_soc) {
  uint8_t min_threshold = (current_soc > 0) ? current_soc - SOC_DELTA_PERCENT : 0;
  uint8_t max_threshold = (current_soc < 100) ? current_soc + SOC_DELTA_PERCENT : 100;

  if (!max17262_set_soc_thresholds(min_threshold, max_threshold)) {
    LOGW("Failed to update SOC thresholds");
  }
}
