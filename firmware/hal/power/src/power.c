#include "power.h"

#include "animation.h"
#include "attributes.h"
#include "battery.h"
#include "bitlog.h"
#include "exti.h"
#include "ipc.h"
#include "log.h"
#include "max17262.h"
#include "max77734.h"
#include "rtos.h"
#include "sysevent.h"

static const uint32_t FIVE_V_BOOST_DELAY_MS = 5u;
static const uint32_t CHARGER_IRQ_TIMEOUT_MS = 100u;
static const uint32_t CHARGER_DEBOUNCE_MAX_MS = 45u;
// static const uint32_t USB_DETECT_IRQ_TIMEOUT_MS = 100u;
static const uint32_t FG_INIT_RETRY_MS = 1000u;

extern power_config_t power_config;

static rtos_timer_t charger_boost_timer = {0};
static const uint32_t CHARGER_BOOST_TIMEOUT_MS = 250u;
static const uint32_t CHARGER_BOOST_CUTOFF = 100 * 1000u;  // 100.000%

// Fuel gauge may fail to init when battery voltate is too low.
// This flag gates external requests for battery status information in that case.
static max17262_status_t fuel_gauge_status = MAX17262_STATUS_UNINITIALISED;
static bool retain_charged_indicator = false;

static void charger_thread(void* args);
static void fuel_gauge_thread(void* args);
static void charger_boost_callback(rtos_timer_handle_t timer);

void power_init(void) {
  // Note: power retain is configured and asserted by the bootloader
  mcu_gpio_configure(&power_config.five_volt_boost, false);
  exti_enable(&power_config.cap_touch_detect);
  mcu_gpio_configure(&power_config.cap_touch_detect.gpio, true);  // Enable pull-up

  rtos_thread_create(charger_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
  rtos_thread_create(fuel_gauge_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 1024);
}

static void charger_thread(void* UNUSED(args)) {
  // Charger init, with charging disabled
  max77734_init();
  max77734_validate();
  max77734_irq_enable(&power_config.charger_irq);

  // 5V Boost Enable
  mcu_gpio_output_set(&power_config.five_volt_boost, true);
  rtos_thread_sleep(FIVE_V_BOOST_DELAY_MS);

  // Enable charging
  max77734_charge_enable(true);

  sysevent_set(SYSEVENT_POWER_READY);

  // Charging state variables
  static bool charging = false;
  static bool charge_input = false;
  static bool is_charging = false;
  static bool charging_done = false;

  // Load initial values
  max77734_charging_status(&charging, &charge_input);

#if 0
  // TODO: figure out why VBUS_DETECT doesn't work
  // Setup USB detect interrupt
  exti_enable(&power_config.usb_detect_irq);
  bool usb_connected = (bool)mcu_gpio_read(&power_config.usb_detect_irq.gpio);
#endif

  for (;;) {
    // Waits for a charger interrupt, then updates the local charging status
    if (max77734_irq_wait(&power_config.charger_irq, CHARGER_IRQ_TIMEOUT_MS)) {
      const uint32_t start = rtos_thread_systime();
      while (!RTOS_DEADLINE(start, CHARGER_DEBOUNCE_MAX_MS)) {
        bool c1;
        bool c2;
        bool c3;

        do {
          max77734_charging_status(&c1, &charge_input);
          rtos_thread_sleep(1);
          max77734_charging_status(&c2, &charge_input);
          rtos_thread_sleep(1);
          max77734_charging_status(&c3, &charge_input);
          rtos_thread_sleep(1);
          // Wait until `charging` stabilizes.
        } while (!((c1 == c2) && (c2 == c3)));
        charging = c1;
      }
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
    if (!charge_input) {
      // Charge input not valid
      // is_charging = false;
      // static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_CHARGING_FINISHED,
      // .immediate = true}; ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
      if (!stopped_once) {
        ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
        stopped_once = true;
      }
      continue;
    }
#endif

    if (charging && !is_charging) {
      // Charging started
      is_charging = true;
      static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_CHARGING,
                                                        .immediate = true};
      ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    } else if (!charging && is_charging) {
      // Charging ended
      is_charging = false;
      ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
    } else if (!charging && charge_input && !charging_done) {
      // Charging ended, USB connected
      charging_done = true;
      if (retain_charged_indicator) {
        static led_start_animation_t LED_TASK_DATA msg = {
          .animation = (uint32_t)ANI_CHARGING_FINISHED_PERSISTENT, .immediate = true};
        ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
      } else {
        static led_start_animation_t LED_TASK_DATA msg = {
          .animation = (uint32_t)ANI_CHARGING_FINISHED, .immediate = true};
        ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
      }
    }
#if 0
    // TODO(W-3755)
    else if (!charge_input) {
      // Charge input not valid
      ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
    }
#endif
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

        rtos_thread_delete(NULL);
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

NO_OPTIMIZE static void charger_boost_callback(rtos_timer_handle_t UNUSED(timer)) {
  max77734_set_max_charge_cv(false);
}
