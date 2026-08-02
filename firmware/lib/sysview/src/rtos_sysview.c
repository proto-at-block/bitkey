#include "rtos_sysview.h"

#include "SEGGER_SYSVIEW.h"
#include "rtos.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#if defined(__GNUC__) && defined(EMBEDDED_BUILD)
#define RTOS_SHARED_BSS __attribute__((section(".shared_task_bss")))
#define RTOS_SRAM4_BSS  __attribute__((section(".sram4")))
#else
#define RTOS_SHARED_BSS
#define RTOS_SRAM4_BSS
#endif

extern const SEGGER_SYSVIEW_OS_API SYSVIEW_X_OS_TraceAPI;

// Provided per-MCU (e.g. lib/sysview/src/stm32/mcu_sysview.c) when sysview is
// enabled. No weak fallback: we want a link error if no platform supplies it,
// and we want the linker to pull the strong impl out of the MCU static lib.
extern void SYSVIEW_SendInterruptList(void);
extern void SYSVIEW_RegisterModules(void);

/*
 * Resource registry.
 *
 * Many wallet resources (mutexes, semaphores, queues) are created BEFORE
 * SEGGER_SYSVIEW_Conf() runs in main(), e.g. inside mcu_init() and the early
 * HAL inits. SEGGER_SYSVIEW_NameResource() is a no-op until SystemView is
 * initialised, so calls made during early init are silently dropped and the
 * resources show up as bare addresses in the host trace.
 *
 * To survive that *and* every subsequent host reconnect (SystemView resets
 * its internal name table every time the host sends START), we keep our own
 * tiny array of (handle, name) pairs and re-emit them from the
 * sysview_send_system_desc() callback, which the SEGGER core invokes on
 * every session start.
 */
#define RTOS_SYSVIEW_MAX_RESOURCES            32
#define RTOS_SYSVIEW_MAX_MARKERS              8
#define RTOS_SYSVIEW_RESOURCE_NAME_LEN        40
#define RTOS_SYSVIEW_IRQ_DISABLE_WARNING_US   100u
#define RTOS_SYSVIEW_IRQ_DISABLE_ERROR_US     500u
#define RTOS_SYSVIEW_TASK_RUNTIME_WARNING_US  1500u
#define RTOS_SYSVIEW_TASK_RUNTIME_ERROR_US    2000u
#define RTOS_SYSVIEW_IDLE_WARNING_US          2500u
#define RTOS_SYSVIEW_IDLE_ERROR_US            3000u
#define RTOS_SYSVIEW_LOCK_STATE_TASK_CRITICAL 0xffffffffu

typedef struct {
  uint32_t id;
  char name[RTOS_SYSVIEW_RESOURCE_NAME_LEN];
} rtos_sysview_resource_t;

typedef struct {
  unsigned int id;
  const char* name;  // Callers must pass string literals or static storage.
} rtos_sysview_marker_t;

// These must stay in .shared_task_bss (not SRAM4) because resources are
// registered during early init (mcu_init, serial_init, etc.) before main()
// memsets SRAM4 and calls SEGGER_SYSVIEW_Conf(). Moving them to SRAM4
// would wipe all early registrations.
static RTOS_SHARED_BSS rtos_sysview_resource_t s_resources[RTOS_SYSVIEW_MAX_RESOURCES];
static RTOS_SHARED_BSS rtos_sysview_marker_t s_markers[RTOS_SYSVIEW_MAX_MARKERS];
static RTOS_SHARED_BSS unsigned s_resource_count;
static RTOS_SHARED_BSS unsigned s_marker_count;
static RTOS_SHARED_BSS unsigned int s_next_marker_id;
static RTOS_SHARED_BSS bool s_sysview_initialised;
static RTOS_SHARED_BSS bool s_monitor_initialised;
static RTOS_SHARED_BSS uint32_t s_irq_disable_start_ticks;
static RTOS_SHARED_BSS uint32_t s_irq_disable_warning_ticks;
static RTOS_SHARED_BSS uint32_t s_irq_disable_error_ticks;
static RTOS_SHARED_BSS uint16_t s_irq_disable_nest;
static RTOS_SHARED_BSS const char* s_irq_disable_start_caller;
static RTOS_SHARED_BSS uint32_t s_task_runtime_warning_ticks;
static RTOS_SHARED_BSS uint32_t s_task_runtime_error_ticks;
static RTOS_SHARED_BSS uint32_t s_idle_warning_ticks;
static RTOS_SHARED_BSS uint32_t s_idle_error_ticks;
static RTOS_SHARED_BSS uint32_t s_current_task_start_ticks;
static RTOS_SHARED_BSS uint32_t s_last_idle_switch_out_ticks;
static RTOS_SHARED_BSS uint8_t s_idle_starvation_level;

uint32_t rtos_sysview_lock(void) {
  if (rtos_in_isr() || rtos_thread_is_privileged()) {
    return portSET_INTERRUPT_MASK_FROM_ISR();
  }

  portENTER_CRITICAL();
  return RTOS_SYSVIEW_LOCK_STATE_TASK_CRITICAL;
}

void rtos_sysview_unlock(uint32_t lock_state) {
  if (lock_state == RTOS_SYSVIEW_LOCK_STATE_TASK_CRITICAL) {
    portEXIT_CRITICAL();
    return;
  }

  portCLEAR_INTERRUPT_MASK_FROM_ISR(lock_state);
}

static void sysview_resource_name_copy(char* dst, size_t dst_size, const char* src) {
  if ((dst == NULL) || (dst_size == 0u)) {
    return;
  }

  if (src == NULL) {
    dst[0] = '\0';
    return;
  }

  (void)snprintf(dst, dst_size, "%s", src);
}

static uint32_t sysview_us_to_ticks(uint32_t threshold_us, uint32_t timestamp_freq_hz) {
  uint64_t ticks;

  if ((threshold_us == 0u) || (timestamp_freq_hz == 0u)) {
    return 0u;
  }

  ticks = (((uint64_t)timestamp_freq_hz * threshold_us) + 999999u) / 1000000u;
  if (ticks == 0u) {
    ticks = 1u;
  }

  return (uint32_t)ticks;
}

static const char* sysview_task_name_or_unknown(const char* name) {
  if ((name == NULL) || (name[0] == '\0')) {
    return "unknown";
  }
  return name;
}

static void sysview_report_named_threshold(bool is_error, const char* prefix, const char* name) {
  char msg[64];

  (void)snprintf(msg, sizeof(msg), "%s:%s", prefix, sysview_task_name_or_unknown(name));
  if (is_error) {
    SEGGER_SYSVIEW_Error(msg);
  } else {
    SEGGER_SYSVIEW_Warn(msg);
  }
}

static void sysview_report_irq_disable(bool is_error, const char* caller_name) {
  const char* prefix = is_error ? "irq_disable_500us" : "irq_disable_100us";
  char msg[80];

  if ((caller_name == NULL) || (caller_name[0] == '\0')) {
    if (is_error) {
      SEGGER_SYSVIEW_Error(prefix);
    } else {
      SEGGER_SYSVIEW_Warn(prefix);
    }
    return;
  }

  (void)snprintf(msg, sizeof(msg), "%s in %s", prefix, caller_name);
  if (is_error) {
    SEGGER_SYSVIEW_Error(msg);
  } else {
    SEGGER_SYSVIEW_Warn(msg);
  }
}

void rtos_sysview_register_resource(uint32_t handle, const char* name) {
  if (handle == 0u || name == NULL) {
    return;
  }
  uint32_t lock = rtos_sysview_lock();
  // Dedupe so re-creates after destroy do not fill the table.
  for (unsigned i = 0; i < s_resource_count; ++i) {
    if (s_resources[i].id == handle) {
      sysview_resource_name_copy(s_resources[i].name, sizeof(s_resources[i].name), name);
      if (s_sysview_initialised) {
        SEGGER_SYSVIEW_NameResource(handle, s_resources[i].name);
      }
      rtos_sysview_unlock(lock);
      return;
    }
  }
  if (s_resource_count >= RTOS_SYSVIEW_MAX_RESOURCES) {
    SEGGER_SYSVIEW_Warn("rtos_sysview: resource registry full");
    rtos_sysview_unlock(lock);
    return;
  }
  s_resources[s_resource_count].id = handle;
  sysview_resource_name_copy(s_resources[s_resource_count].name,
                             sizeof(s_resources[s_resource_count].name), name);
  ++s_resource_count;
  if (s_sysview_initialised) {
    SEGGER_SYSVIEW_NameResource(handle, s_resources[s_resource_count - 1u].name);
  }
  rtos_sysview_unlock(lock);
}

unsigned int rtos_sysview_register_marker(const char* name) {
  if (name == NULL || name[0] == '\0') {
    return 0u;
  }

  uint32_t lock = rtos_sysview_lock();

  for (unsigned i = 0; i < s_marker_count; ++i) {
    if (strcmp(s_markers[i].name, name) == 0) {
      if (s_sysview_initialised) {
        SEGGER_SYSVIEW_NameMarker(s_markers[i].id, name);
      }
      rtos_sysview_unlock(lock);
      return s_markers[i].id;
    }
  }

  if (s_marker_count >= RTOS_SYSVIEW_MAX_MARKERS) {
    SEGGER_SYSVIEW_Warn("rtos_sysview: marker registry full");
    rtos_sysview_unlock(lock);
    return 0u;
  }

  const unsigned int marker_id = s_next_marker_id++;
  s_markers[s_marker_count].id = marker_id;
  s_markers[s_marker_count].name = name;
  ++s_marker_count;

  if (s_sysview_initialised) {
    SEGGER_SYSVIEW_NameMarker(marker_id, name);
  }

  rtos_sysview_unlock(lock);
  return marker_id;
}

void rtos_sysview_monitor_init(uint32_t timestamp_freq_hz) {
  s_irq_disable_start_ticks = 0u;
  s_irq_disable_warning_ticks =
    sysview_us_to_ticks(RTOS_SYSVIEW_IRQ_DISABLE_WARNING_US, timestamp_freq_hz);
  s_irq_disable_error_ticks =
    sysview_us_to_ticks(RTOS_SYSVIEW_IRQ_DISABLE_ERROR_US, timestamp_freq_hz);
  s_irq_disable_nest = 0u;
  s_irq_disable_start_caller = NULL;

  s_task_runtime_warning_ticks =
    sysview_us_to_ticks(RTOS_SYSVIEW_TASK_RUNTIME_WARNING_US, timestamp_freq_hz);
  s_task_runtime_error_ticks =
    sysview_us_to_ticks(RTOS_SYSVIEW_TASK_RUNTIME_ERROR_US, timestamp_freq_hz);
  s_idle_warning_ticks = sysview_us_to_ticks(RTOS_SYSVIEW_IDLE_WARNING_US, timestamp_freq_hz);
  s_idle_error_ticks = sysview_us_to_ticks(RTOS_SYSVIEW_IDLE_ERROR_US, timestamp_freq_hz);
  s_current_task_start_ticks = 0u;
  s_last_idle_switch_out_ticks = portGET_RUN_TIME_COUNTER_VALUE();
  s_idle_starvation_level = 0u;

  s_monitor_initialised =
    (s_irq_disable_warning_ticks != 0u) && (s_irq_disable_error_ticks != 0u) &&
    (s_task_runtime_warning_ticks != 0u) && (s_task_runtime_error_ticks != 0u) &&
    (s_idle_warning_ticks != 0u) && (s_idle_error_ticks != 0u);
}

void rtos_sysview_task_switched_in(uint32_t handle, const char* name, bool is_idle) {
  (void)handle;
  (void)name;

  if (!s_monitor_initialised) {
    return;
  }

  if (is_idle) {
    s_idle_starvation_level = 0u;
    s_current_task_start_ticks = 0u;
    return;
  }

  s_current_task_start_ticks = portGET_RUN_TIME_COUNTER_VALUE();
}

void rtos_sysview_task_switched_out(uint32_t handle, const char* name, bool is_idle) {
  uint32_t now;
  uint32_t elapsed_ticks;

  (void)handle;

  if (!s_monitor_initialised) {
    return;
  }

  now = portGET_RUN_TIME_COUNTER_VALUE();

  if (is_idle) {
    s_last_idle_switch_out_ticks = now;
    s_idle_starvation_level = 0u;
    s_current_task_start_ticks = 0u;
    return;
  }

  if (s_current_task_start_ticks != 0u) {
    elapsed_ticks = now - s_current_task_start_ticks;
    if (elapsed_ticks > s_task_runtime_error_ticks) {
      sysview_report_named_threshold(true, "task_runtime_2000us", name);
    } else if (elapsed_ticks > s_task_runtime_warning_ticks) {
      sysview_report_named_threshold(false, "task_runtime_1500us", name);
    }
  }

  elapsed_ticks = now - s_last_idle_switch_out_ticks;
  if ((elapsed_ticks > s_idle_error_ticks) && (s_idle_starvation_level < 2u)) {
    SEGGER_SYSVIEW_Error("idle_starved_3000us");
    s_idle_starvation_level = 2u;
  } else if ((elapsed_ticks > s_idle_warning_ticks) && (s_idle_starvation_level < 1u)) {
    SEGGER_SYSVIEW_Warn("idle_starved_2500us");
    s_idle_starvation_level = 1u;
  }

  s_current_task_start_ticks = 0u;
}

void rtos_sysview_irq_disable_track_start(const char* caller_name) {
  if (!s_monitor_initialised) {
    return;
  }

  if (s_irq_disable_nest == 0u) {
    s_irq_disable_start_ticks = portGET_RUN_TIME_COUNTER_VALUE();
    s_irq_disable_start_caller = caller_name;
  }

  s_irq_disable_nest++;
}

void rtos_sysview_irq_disable_track_stop(bool restore_enables_irq) {
  uint32_t irq_disabled_ticks;

  if (!s_monitor_initialised || (s_irq_disable_nest == 0u)) {
    return;
  }

  s_irq_disable_nest--;
  if (!restore_enables_irq) {
    if (s_irq_disable_nest == 0u) {
      s_irq_disable_start_caller = NULL;
    }
    return;
  }

  if (s_irq_disable_start_caller == NULL) {
    s_irq_disable_nest = 0u;
    return;
  }

  irq_disabled_ticks = portGET_RUN_TIME_COUNTER_VALUE() - s_irq_disable_start_ticks;
  s_irq_disable_nest = 0u;
  if (irq_disabled_ticks > s_irq_disable_error_ticks) {
    sysview_report_irq_disable(true, s_irq_disable_start_caller);
  } else if (irq_disabled_ticks > s_irq_disable_warning_ticks) {
    sysview_report_irq_disable(false, s_irq_disable_start_caller);
  }

  s_irq_disable_start_caller = NULL;
}

static void sysview_send_system_desc(void) {
  SEGGER_SYSVIEW_SendSysDesc("N=" SYSVIEW_APP_NAME ",D=" SYSVIEW_DEVICE_NAME ",O=FreeRTOS");
  SEGGER_SYSVIEW_SendSysDesc("I#15=SysTick");
  SYSVIEW_SendInterruptList();

  // Re-emit every cached resource name so the host's name table is rebuilt
  // on every fresh START / restart, regardless of how late the resource was
  // created relative to the host attaching.
  for (unsigned i = 0; i < s_resource_count; ++i) {
    SEGGER_SYSVIEW_NameResource(s_resources[i].id, s_resources[i].name);
  }

  for (unsigned i = 0; i < s_marker_count; ++i) {
    SEGGER_SYSVIEW_NameMarker(s_markers[i].id, s_markers[i].name);
  }
}

void SEGGER_SYSVIEW_Conf(void) {
  if (s_next_marker_id < 1u) {
    s_next_marker_id = 1u;
  }
  portCONFIGURE_TIMER_FOR_RUN_TIME_STATS();

  SEGGER_SYSVIEW_Init(configCPU_CLOCK_HZ, configCPU_CLOCK_HZ, &SYSVIEW_X_OS_TraceAPI,
                      sysview_send_system_desc);
  rtos_sysview_monitor_init(configCPU_CLOCK_HZ);
  extern char ram_addr[];
  SEGGER_SYSVIEW_SetRAMBase((uint32_t)(uintptr_t)ram_addr);
  SYSVIEW_RegisterModules();
  s_sysview_initialised = true;

  // Drain any resources that were registered before init so they show up on
  // the very first session.
  for (unsigned i = 0; i < s_resource_count; ++i) {
    SEGGER_SYSVIEW_NameResource(s_resources[i].id, s_resources[i].name);
  }

  for (unsigned i = 0; i < s_marker_count; ++i) {
    SEGGER_SYSVIEW_NameMarker(s_markers[i].id, s_markers[i].name);
  }

  // Do not auto-start recording here. The host is expected to send the
  // explicit SystemView START command over RTT (GUI or headless capture
  // script). That keeps Trace Start boundaries aligned with host intent and
  // avoids duplicate start events.

  SEGGER_SYSVIEW_Print("sysview_ready");
}
