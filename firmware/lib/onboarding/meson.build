deps = [
  helpers_dep,
  secutils_dep,
  bio_dep,
  unlock_dep,
  log_dep,
  wallet_dep,
]

onboarding_lib = library('onboarding',
  'onboarding.c',
  include_directories : ['.'],
  dependencies : deps,
)

onboarding_dep = declare_dependency(
  link_with : onboarding_lib,
  include_directories : ['.'],
  dependencies : deps,
)
