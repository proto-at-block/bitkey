# lib/platform - POSIX platform header stubs
#
# Provides header-only stubs for hardware-dependent APIs that don't exist
# on POSIX systems. Used by POSIX builds (core-sim/core-sim, ui-simulate)
# to compile firmware code that references HAL functions.
#
# Headers include: power.h, bio.h, secure_engine.h, metadata.h, bitlog.h,
# kv.h, sleep.h, mcu_reset.h, mcu_wdog.h, telemetry_storage.h, feature_flags.h,
# sysinfo.h, attributes.h

platform_includes = include_directories('inc')

# Header-only dependency for POSIX builds
platform_posix_dep = declare_dependency(
  include_directories : platform_includes,
)
