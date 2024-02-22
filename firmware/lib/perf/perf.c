#include "perf.h"

#include "assert.h"

#ifdef EMBEDDED_BUILD
#include "printf.h"
#else
#include <stdio.h>
#endif

#include "rtos_mutex.h"
#include "rtos_thread.h"
#include "utlist.h"

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>

static SHARED_TASK_DATA perf_counter_t* perf_counters = NULL;
static SHARED_TASK_DATA rtos_mutex_t perf_lock = {0};
static SHARED_TASK_DATA bool initialised = false;
static const uint64_t us_in_ms = 1000;

static void perf_init(void);
static bool perf_exists(perf_counter_t* counter);
static int compare_perf_name(perf_counter_t* a, perf_counter_t* b);
static void calculate_variance(const int64_t window, uint64_t count, float* mean, float* variance);
static void print_counter(perf_counter_t* counter);
static bool strcmp_wildcard(const char* string, const char* pattern);

void perf_create_static(perf_counter_t* counter) {
  ASSERT(counter->data != NULL);
  ASSERT(counter->type < PERF_MAX);

  if (!initialised) {
    perf_init();
  }

  if (perf_exists(counter)) {
    return;
  }

  perf_reset(counter);

  rtos_mutex_lock(&perf_lock);
  LL_INSERT_INORDER(perf_counters, counter, compare_perf_name);
  rtos_mutex_unlock(&perf_lock);
}

/* Count an event, PERF_COUNT and PERF_INTERVAL only */
void perf_count(perf_counter_t* counter) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_COUNT:
      ((perf_count_t*)counter->data)->event_count++;
      break;

    case PERF_INTERVAL:
      perf_count_interval(counter, rtos_thread_micros());
      break;

    case PERF_ELAPSED:
    default:
      break;
  }
}

/* Begin an event, PERF_ELAPSED only */
void perf_begin(perf_counter_t* counter) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_ELAPSED:
      ((perf_elapsed_t*)counter->data)->time_start = rtos_thread_micros();
      break;

    case PERF_COUNT:
    case PERF_INTERVAL:
    default:
      break;
  }
}

/* End an event, PERF_ELAPSED only */
void perf_end(perf_counter_t* counter) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_ELAPSED: {
      perf_elapsed_t* pce = (perf_elapsed_t*)counter->data;

      if (pce->time_start != 0) {
        const int64_t elapsed = (int64_t)(rtos_thread_micros() - pce->time_start);
        (void)elapsed;
        perf_set_elapsed(counter, elapsed);
      }
    } break;

    case PERF_COUNT:
    case PERF_INTERVAL:
    default:
      break;
  }
}

/* Register a measurement, PERF_ELAPSED only */
void perf_set_elapsed(perf_counter_t* counter, const int64_t elapsed) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_ELAPSED: {
      perf_elapsed_t* pce = (perf_elapsed_t*)counter->data;

      if (elapsed >= 0) {
        pce->event_count++;
        pce->time_total += (uint64_t)elapsed;

        if ((pce->time_least > (uint32_t)elapsed) || (pce->time_least == 0)) {
          pce->time_least = (uint32_t)elapsed;
        }

        if (pce->time_most < (uint32_t)elapsed) {
          pce->time_most = (uint32_t)elapsed;
        }

        pce->time_start = 0;
        calculate_variance(elapsed, pce->event_count, &pce->mean, &pce->variance);
      }
    } break;

    case PERF_COUNT:
    case PERF_INTERVAL:
    default:
      break;
  }
}

/* Register a measurement, PERF_INTERVAL only */
void perf_count_interval(perf_counter_t* counter, const uint64_t now) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_INTERVAL: {
      perf_interval_t* pci = (perf_interval_t*)counter->data;

      switch (pci->event_count) {
        case 0:
          pci->time_first = now;
          break;

        case 1:
          pci->time_least = (uint32_t)(now - pci->time_last);
          pci->time_most = (uint32_t)(now - pci->time_last);
          pci->mean = (float)pci->time_least / 1e6f;
          pci->variance = 0;
          break;

        default: {
          uint64_t interval = now - pci->time_last;

          if ((uint32_t)interval < pci->time_least) {
            pci->time_least = (uint32_t)interval;
          }

          if ((uint32_t)interval > pci->time_most) {
            pci->time_most = (uint32_t)interval;
          }

          calculate_variance(interval, pci->event_count, &pci->mean, &pci->variance);
          break;
        }
      }

      pci->time_last = now;
      pci->event_count++;
      break;
    }

    case PERF_ELAPSED:
    case PERF_COUNT:
    default:
      break;
  }
}

/* Set a counter, PERF_COUNT only */
void perf_set_count(perf_counter_t* counter, const uint64_t count) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_COUNT: {
      ((perf_count_t*)counter->data)->event_count = count;
    } break;

    case PERF_INTERVAL:
    case PERF_ELAPSED:
    default:
      break;
  }
}

/* Cancel an event, PERF_ELAPSED only */
void perf_cancel(perf_counter_t* counter) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_ELAPSED: {
      perf_elapsed_t* pce = (perf_elapsed_t*)counter->data;

      pce->time_start = 0;
    } break;

    case PERF_COUNT:
    case PERF_INTERVAL:
    default:
      break;
  }
}

/* Reset a counter */
void perf_reset(perf_counter_t* counter) {
  switch (counter->type) {
    case PERF_COUNT: {
      perf_count_t* data = counter->data;
      data->event_count = 0;
    } break;

    case PERF_ELAPSED: {
      perf_elapsed_t* data = counter->data;
      data->event_count = 0;
      data->time_start = 0;
      data->time_total = 0;
      data->time_least = 0;
      data->time_most = 0;
      data->mean = 0.0f;
      data->variance = 0.0f;
    } break;

    case PERF_INTERVAL: {
      perf_interval_t* data = counter->data;
      data->event_count = 0;
      data->time_event = 0;
      data->time_first = 0;
      data->time_last = 0;
      data->time_least = 0;
      data->time_most = 0;
      data->mean = 0.0f;
      data->variance = 0.0f;
    } break;

    default:
      break;
  }
}

void perf_print_all(void) {
  rtos_mutex_lock(&perf_lock);

  perf_counter_t* handle = NULL;
  LL_FOREACH (perf_counters, handle) {
    /* Print each counter */
    print_counter(handle);
  }

  rtos_mutex_unlock(&perf_lock);
}

void perf_print_search(const char* pattern) {
  ASSERT(pattern != NULL);

  rtos_mutex_lock(&perf_lock);

  perf_counter_t* handle = NULL;
  LL_FOREACH (perf_counters, handle) {
    if (strcmp_wildcard(handle->name, (const char*)pattern)) {
      print_counter(handle);
    }
  }

  rtos_mutex_unlock(&perf_lock);
}

static void perf_init(void) {
  if (initialised) {
    return;
  }

  rtos_mutex_create(&perf_lock);
  initialised = true;
}

static bool perf_exists(perf_counter_t* counter) {
  /* Check for existing perf counter with the same name */
  perf_counter_t* result = NULL;

  rtos_mutex_lock(&perf_lock);
  LL_SEARCH(perf_counters, result, counter, compare_perf_name);
  rtos_mutex_unlock(&perf_lock);

  return result != NULL;
}

static int compare_perf_name(perf_counter_t* a, perf_counter_t* b) {
  return strcmp(a->name, b->name);
}

static void calculate_variance(const int64_t window, uint64_t count, float* mean, float* variance) {
  /* Knuth/Welford recursive mean and variance of update intervals. see:
   * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance */
  const float dt = (float)window / 1e6f;
  const float delta_intvl = dt - *mean;
  *mean += delta_intvl / (float)count;
  *variance += delta_intvl * (dt - *mean);
}

static void print_counter(perf_counter_t* counter) {
  ASSERT(counter != NULL);

  switch (counter->type) {
    case PERF_COUNT:
      printf("%s: %llu events\n", counter->name,
             (unsigned long long)((perf_count_t*)counter->data)->event_count);
      break;
    case PERF_ELAPSED: {
      perf_elapsed_t* pce = (perf_elapsed_t*)counter->data;
      float rms = 0.0f;
      if (pce->event_count > 1) {
        rms = sqrtf(pce->variance / (float)(pce->event_count - 1));
      }
      if (pce->time_most > us_in_ms) {
        printf(
          "%s: %llu events, %llums elapsed, %.2fms avg, min %llums max "
          "%llums "
          "%5.3fus rms\n",
          counter->name, (unsigned long long)pce->event_count,
          (unsigned long long)pce->time_total / us_in_ms,
          (pce->event_count == 0)
            ? 0
            : ((double)pce->time_total / (double)pce->event_count) / (double)us_in_ms,
          (unsigned long long)pce->time_least / us_in_ms,
          (unsigned long long)pce->time_most / us_in_ms, (double)(1e6f * rms));
      } else {
        printf(
          "%s: %llu events, %lluus elapsed, %.2fus avg, min %lluus max "
          "%lluus "
          "%5.3fus rms\n",
          counter->name, (unsigned long long)pce->event_count, (unsigned long long)pce->time_total,
          (pce->event_count == 0) ? 0 : (double)pce->time_total / (double)pce->event_count,
          (unsigned long long)pce->time_least, (unsigned long long)pce->time_most,
          (double)(1e6f * rms));
      }
      break;
    }
    case PERF_INTERVAL: {
      perf_interval_t* pci = (perf_interval_t*)counter->data;
      float rms = 0.0f;
      if (pci->event_count > 1) {
        rms = sqrtf(pci->variance / (float)(pci->event_count - 1));
      }
      if (pci->time_most > us_in_ms) {
        printf(
          "%s: %llu events, %.2fms avg, min %llums max %llums %5.3fus "
          "rms\n",
          counter->name, (unsigned long long)pci->event_count,
          (pci->event_count == 0)
            ? 0
            : ((double)(pci->time_last - pci->time_first) / (double)pci->event_count) /
                (double)us_in_ms,
          (unsigned long long)pci->time_least / us_in_ms,
          (unsigned long long)pci->time_most / us_in_ms, (double)(1e6f * rms));
      } else {
        printf(
          "%s: %llu events, %.2fus avg, min %lluus max %lluus %5.3fus "
          "rms\n",
          counter->name, (unsigned long long)pci->event_count,
          (pci->event_count == 0)
            ? 0
            : (double)(pci->time_last - pci->time_first) / (double)pci->event_count,
          (unsigned long long)pci->time_least, (unsigned long long)pci->time_most,
          (double)(1e6f * rms));
      }
      break;
    }
    default:
      break;
  }
}

/* strcmp with support for optional characters (?) and wildcards (*) */
static bool strcmp_wildcard(const char* string, const char* pattern) {
  bool wildcard = false;
  char* placeholder = (char*)string;

  do {
    if ((*pattern == *string) || (*pattern == '?')) {
      string++;
      pattern++;
    } else if (*pattern == '*') {
      if (*(++pattern) == '\0') {
        return 1;
      }
      wildcard = true;
    } else if (wildcard) {
      if (pattern != placeholder) {
        string++;
      } else {
        pattern = placeholder;
      }
    } else {
      return 0;
    }
  } while (*string);

  if (*pattern == '\0') {
    return 1;
  } else {
    return 0;
  }
}

uint64_t perf_get_count(perf_counter_t* counter) {
  return (unsigned long long)((perf_count_t*)counter->data)->event_count;
}
