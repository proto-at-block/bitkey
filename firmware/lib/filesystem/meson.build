lfs_config_includes = include_directories('config')
lfs_config_srcs = files('config/lfs_crc.c')

lfs_c_args = [
  '-Wno-unused-function',

  # LittleFS compile options
  '-DLFS_NO_MALLOC',

  # Debugging support
  #'-DLFS_NO_ASSERT',
  #'-DLFS_YES_TRACE',
  #'-DLFS_NO_DEBUG', '-DLFS_NO_WARN', '-DLFS_NO_ERROR',
]

lfs_c_args += ['-DLFS_CONFIG=lfs_config.h']

lfs_lib = library('littlefs',
  lfs_srcs + lfs_config_srcs,
  c_args : lfs_c_args,
  include_directories : [lfs_includes, lfs_config_includes],
  dependencies : [log_dep]
)
lfs_dep = declare_dependency(
  link_with : lfs_lib,
  include_directories : lfs_includes,
  dependencies : [log_dep]
)

subdir('block_device')

deps = [
  helpers_dep,
  assert_dep,
  lfs_dep,
  rtos_dep,
  log_dep,
  sysevent_dep,
]

srcs = [
  'filesystem.c',
  'filesystem_util.c',
]

if system == 'efr32mg24'
  deps += perf_dep

  fs_lib = library('filesystem',
    srcs,
    include_directories : [
      '.',
      'block_device',
    ],
    dependencies : deps
  )
  fs_dep = declare_dependency(
    link_with : fs_lib,
    include_directories : ['.'],
    dependencies : [lfs_dep]
  )
elif system == 'darwin' or system == 'linux'
  deps += perf_stubs_dep

  fs_lib = library('filesystem',
    srcs,
    include_directories : [
      '.',
      'block_device',
    ],
    dependencies : deps
  )
  fs_dep = declare_dependency(
    link_with : fs_lib,
    include_directories : ['.'],
    dependencies : [lfs_dep, lfs_testbd_dep]
  )
endif

fs_cmd_lib = library('filesystem-cmd',
  'fs_cmd.c',
  include_directories : ['.'],
  dependencies : [deps, fs_bd_cmd_dep, shell_dep]
)
fs_cmd_dep = declare_dependency(
  link_whole : fs_cmd_lib,
  include_directories : ['.'],
  dependencies : [deps, fs_bd_cmd_dep, shell_dep]
)
