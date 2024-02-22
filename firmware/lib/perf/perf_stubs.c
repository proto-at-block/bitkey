#include "fff.h"
#include "perf.h"

FAKE_VOID_FUNC(perf_create_static, perf_counter_t*);
FAKE_VOID_FUNC(perf_count, perf_counter_t*);
FAKE_VOID_FUNC(perf_begin, perf_counter_t*);
FAKE_VOID_FUNC(perf_end, perf_counter_t*);
FAKE_VOID_FUNC(perf_set_elapsed, perf_counter_t*, const int64_t);
FAKE_VOID_FUNC(perf_count_interval, perf_counter_t*, const uint64_t);
FAKE_VOID_FUNC(perf_set_count, perf_counter_t*, const uint64_t);
FAKE_VOID_FUNC(perf_cancel, perf_counter_t*);
FAKE_VOID_FUNC(perf_reset, perf_counter_t*);

FAKE_VOID_FUNC(perf_print_all);
FAKE_VOID_FUNC(perf_print_search, const char*);
