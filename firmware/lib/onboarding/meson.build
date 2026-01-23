# Header includes for bio/unlock/grant are specified directly to break
# circular dependency (these modules need ipc, but ipc needs onboarding)
onboarding_incs = [
  'inc',
  '../../hal/biometrics/inc',
  '../unlock/inc',
  '../grant_protocol/inc',
]

deps = [
  helpers_dep,
  secutils_dep,
  log_dep,
  wallet_dep,
  kv_dep,
]

onboarding_lib = library('onboarding',
  'onboarding.c',
  include_directories : onboarding_incs,
  dependencies : deps,
)

onboarding_dep = declare_dependency(
  link_with : onboarding_lib,
  include_directories : ['inc'],
  dependencies : deps,
)
