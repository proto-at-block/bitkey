#pragma once

#include <stdint.h>

typedef enum {
  PERF_COUNT,
  PERF_ELAPSED,
  PERF_INTERVAL,
  PERF_MAX,
} perf_type_t;

typedef struct perf_counter {
  perf_type_t type;
  char* name;
  void* data;
  struct perf_counter* next;
} perf_counter_t;

typedef struct {
  uint64_t event_count;
} perf_count_t;

typedef struct {
  uint64_t event_count;
  uint64_t time_start;
  uint64_t time_total;
  uint32_t time_least;
  uint32_t time_most;
  float mean;
  float variance;
} perf_elapsed_t;

typedef struct {
  uint64_t event_count;
  uint64_t time_event;
  uint64_t time_first;
  uint64_t time_last;
  uint32_t time_least;
  uint32_t time_most;
  float mean;
  float variance;
} perf_interval_t;

#define _PERF_COUNT_TYPE    perf_count_t
#define _PERF_ELAPSED_TYPE  perf_elapsed_t
#define _PERF_INTERVAL_TYPE perf_interval_t

void perf_create_static(perf_counter_t* counter);
#define perf_create(_type, _name)                                       \
  ({                                                                    \
    static SHARED_TASK_DATA perf_counter_t _##_name##_perf = {0};       \
    static SHARED_TASK_DATA _##_type##_TYPE _##_name##_perf_data = {0}; \
    _##_name##_perf.data = &_##_name##_perf_data;                       \
    _##_name##_perf.type = _type;                                       \
    _##_name##_perf.name = #_name;                                      \
    perf_create_static(&_##_name##_perf);                               \
    /* returns perf_counter_t* type */                                  \
    &_##_name##_perf;                                                   \
  })

void perf_count(perf_counter_t* counter);
void perf_begin(perf_counter_t* counter);
void perf_end(perf_counter_t* counter);
void perf_set_elapsed(perf_counter_t* counter, const int64_t elapsed);
void perf_count_interval(perf_counter_t* counter, const uint64_t now);
void perf_set_count(perf_counter_t* counter, const uint64_t count);
uint64_t perf_get_count(perf_counter_t* counter);
void perf_cancel(perf_counter_t* counter);
void perf_reset(perf_counter_t* counter);

void perf_print_all(void);
void perf_print_search(const char* pattern);
