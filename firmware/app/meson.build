objcopy = find_program('arm-none-eabi-objcopy', required : embedded_build)
invoke = find_program('inv', required : embedded_build)
signer = find_program(meson.project_source_root()/'python/bitkey/firmware_signer.py', required : embedded_build)

subdir('tasks')
subdir('ui-snapshot')

# Embedded-only apps
if embedded_build
  subdir('w1')
  subdir('w3-core')
  subdir('w3-uxc')
endif

# POSIX-only apps
subdir('core-sim')
