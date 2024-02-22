memfault_base_dir = '../../memfault-firmware-sdk/'
src_dir = memfault_base_dir/'components'

memfault_core_srcs = files(
  src_dir/'core/src/arch_arm_cortex_m.c',
  src_dir/'core/src/memfault_batched_events.c',
  src_dir/'core/src/memfault_build_id.c',
  src_dir/'core/src/memfault_compact_log_serializer.c',
  src_dir/'core/src/memfault_core_utils.c',
  src_dir/'core/src/memfault_custom_data_recording.c',
  src_dir/'core/src/memfault_data_export.c',
  src_dir/'core/src/memfault_data_packetizer.c',
  src_dir/'core/src/memfault_data_source_rle.c',
  src_dir/'core/src/memfault_event_storage.c',
  src_dir/'core/src/memfault_heap_stats.c',
  src_dir/'core/src/memfault_log_data_source.c',
  src_dir/'core/src/memfault_ram_reboot_info_tracking.c',
  src_dir/'core/src/memfault_reboot_tracking_serializer.c',
  src_dir/'core/src/memfault_sdk_assert.c',
  src_dir/'core/src/memfault_serializer_helper.c',
  src_dir/'core/src/memfault_task_watchdog.c',
  src_dir/'core/src/memfault_trace_event.c',
)

memfault_util_srcs = files(
  src_dir/'util/src/memfault_base64.c',
  src_dir/'util/src/memfault_chunk_transport.c',
  src_dir/'util/src/memfault_circular_buffer.c',
  src_dir/'util/src/memfault_crc16_ccitt.c',
  src_dir/'util/src/memfault_minimal_cbor.c',
  src_dir/'util/src/memfault_rle.c',
  src_dir/'util/src/memfault_varint.c',
)

memfault_panics_srcs = files(
  src_dir/'panics/src/memfault_coredump.c',
  src_dir/'panics/src/memfault_coredump_regions_armv7.c',
  src_dir/'panics/src/memfault_coredump_sdk_regions.c',
  src_dir/'panics/src/memfault_coredump_storage_debug.c',
  src_dir/'panics/src/memfault_coredump_utils.c',
  src_dir/'panics/src/memfault_fault_handling_arm.c',
)

memfault_metrics_srcs = files(
  src_dir/'metrics/src/memfault_metrics.c',
  src_dir/'metrics/src/memfault_metrics_reliability.c',
  src_dir/'metrics/src/memfault_metrics_serializer.c',
)

memfault_port_srcs = files(
  memfault_base_dir/'ports/freertos/src/memfault_metrics_freertos.c',
  memfault_base_dir/'ports/freertos/src/memfault_core_freertos.c',
  memfault_base_dir/'ports/freertos/src/memfault_freertos_ram_regions.c',
  memfault_base_dir/'ports/freertos/src/memfault_panics_freertos.c',
)

memfault_sdk_srcs = memfault_core_srcs + memfault_util_srcs + memfault_panics_srcs + memfault_metrics_srcs + memfault_port_srcs

# Sources for building Memfault serializer on host (MacOS, iOS, Android, etc.)
memfault_serializer_sdk_srcs = files(
  src_dir/'core/src/memfault_event_storage.c',
  src_dir/'core/src/memfault_serializer_helper.c',
  src_dir/'core/src/memfault_trace_event.c',
  src_dir/'util/src/memfault_minimal_cbor.c',
  src_dir/'util/src/memfault_circular_buffer.c',
  src_dir/'util/src/memfault_chunk_transport.c',
  src_dir/'core/src/memfault_sdk_assert.c',

  src_dir/'core/src/memfault_data_packetizer.c',
  src_dir/'util/src/memfault_varint.c',
  src_dir/'util/src/memfault_crc16_ccitt.c',
)

memfault_log_sdk_srcs = files(
  src_dir/'core/src/memfault_log.c',
)

# NOTE: Includes are defined in toplevel Meson file.
