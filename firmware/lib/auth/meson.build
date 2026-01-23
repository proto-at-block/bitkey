srcs = [
  'src/auth.c',
]

deps = [
  secutils_dep,
  rtos_dep,
  helpers_dep,
  sleep_dep,
]

auth_lib = static_library('auth',
  srcs,
  include_directories: ['inc', 'src'],
  dependencies: deps,
)

auth_dep = declare_dependency(
  include_directories: ['inc'],
  link_with: auth_lib,
  dependencies: deps,
)
