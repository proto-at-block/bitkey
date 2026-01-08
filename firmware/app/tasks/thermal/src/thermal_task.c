#include "thermal_task.h"

#include "assert.h"
#include "attributes.h"
#include "log.h"
#include "rtos.h"
#include "rtos_mpu.h"
#include "sysevent.h"
#include "temperature.h"

// Task configuration
#define THERMAL_TASK_STACK_SIZE 1024
#define THERMAL_TASK_PRIORITY   RTOS_THREAD_PRIORITY_LOW

static void thermal_thread(void* arg) {
  (void)arg;

  while (true) {
    // Wait for temperature events
    sysevent_wait(SYSEVENT_TEMP_HIGH | SYSEVENT_TEMP_LOW | SYSEVENT_USB_THERMAL_FAULT, false);

    // Handle high temperature event
    if (sysevent_get(SYSEVENT_TEMP_HIGH)) {
      LOGW("High temperature event detected");
      sysevent_clear(SYSEVENT_TEMP_HIGH);
    }

    // Handle low temperature event
    if (sysevent_get(SYSEVENT_TEMP_LOW)) {
      LOGW("Low temperature event detected");
      sysevent_clear(SYSEVENT_TEMP_LOW);
    }

    // Handle USB IC thermal fault
    if (sysevent_get(SYSEVENT_USB_THERMAL_FAULT)) {
      LOGW("USB IC thermal fault");
      sysevent_clear(SYSEVENT_USB_THERMAL_FAULT);
    }
  }
}

void thermal_task_create(void) {
  // Initialize the temperature monitoring library
  if (!temperature_init()) {
    LOGE("Failed to initialize temperature monitoring library");
    ASSERT(false);
  }

  // Create thermal monitoring task
  rtos_thread_t* task_handle =
    rtos_thread_create(thermal_thread, NULL, THERMAL_TASK_PRIORITY, THERMAL_TASK_STACK_SIZE);
  ASSERT(task_handle);

  LOGI("Thermal monitoring task created successfully");
}
