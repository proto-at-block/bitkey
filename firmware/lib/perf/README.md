# Perf

This library provides a simple but powerful interface for counting performance events within an embedded system. Examples of performance events are operations like I2C transfers, filesystem operations. Three types of performance counters are supported, as described further below. This library is heavily inspired by the similarly named library by the [px4](https://github.com/PX4/PX4-Autopilot/blob/master/src/lib/perf/perf_counter.cpp) project, but reimplemented in `C` using only statically allocated memory, to be more friendly to lower-power embedded targets.

## Counter types

### `PERF_COUNT`

This type is the simplest type and is used for counting events. Other than the count itself, no other information about the event is tracked. These counters are useful for tracking how many times a function or code path is executed.

```c
/* Counter initialisation */
static perf_counter_t* cycle_count;
cycle_count = perf_create(PERF_COUNT, perf_count_t, cycle_count);

for(uint32_t i = 0; i < num; i++) {
  /* Do some operation `num` times */

  /* Count each time the loop executes */
  perf_count(cycle_count);
}
```

### `PERF_ELAPSED`

This type is used to track the elapsed time of an operation, such as an I2C transfer. To use this counter a begin and end function are called on either end of desired code path. Subsequent calls to the begin and end functions will be used to provide statistics about the duration of that code path, such as minimum, maximum, average, mean elapsed time, and the standard deviation of the elapsed time.

```c
/* Counter initialisation */
static perf_counter_t* xfer_counter;
xfer_counter = perf_create(PERF_ELAPSED, perf_elapsed_t, xfer_counter);

perf_begin(xfer_counter);

/* Begin the i2c transfer */

if(error) {
  /* Cancel the counter on any early-exit event */
  perf_cancel(xfer_counter);
  return;
}

/* Finish the i2c transfer */

/* End the counter after the transfer is complete */
perf_end(xfer_counter);
```

### `PERF_INTERVAL`

This type is use to track the periodicity of a code path, such as a loop that is expected to run at 500Hz. This counter is useful for tracking if regular RTOS tasks or threads are running at their expected frequency, including statistics on the min,max and standard deviation of the execution frequency.

```c
/* Counter initialisation */
static perf_counter_t* loop_counter;
loop_counter = perf_create(PERF_INTERVAL, perf_interval_t, loop_counter);

while(true) {
  /* Track the time between the start of every loop */
  perf_count(loop_counter);

  /* Perform some operation */

  /* Sleep for 50 milliseconds */
  rtos_thread_sleep(50);
}
```
