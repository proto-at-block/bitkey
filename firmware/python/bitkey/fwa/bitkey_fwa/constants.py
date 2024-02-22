
# Products
PRODUCT_W1A = "w1a"
PRODUCTS = {
    PRODUCT_W1A,
}

# Platforms
PLATFORM_DVT = "dvt"
PLATFORM_EVT = "evt"
PLATFORMS = {
    PLATFORM_DVT,
    PLATFORM_EVT,
}

# Assets
ASSET_LOADER = "loader"
ASSET_APP = "app"
ASSETS = {
    ASSET_LOADER,
    ASSET_APP,
}

# Slots
SLOT_A = "a"
SLOT_B = "b"
SLOTS = {
    SLOT_A,
    SLOT_B,
}

# Environment
ENV_MFGTEST = "mfgtest"
ENV_NON_MFGTEST = "non-mfgtest"
ENVIRONMENTS = {
    ENV_MFGTEST,
    ENV_NON_MFGTEST,
}

# Security levels
SECURITY_DEV = "dev"
SECURITY_PROD = "prod"
SECURITIES = {
    SECURITY_DEV,
    SECURITY_PROD,
}

# Firmware Signer environments
SIGNER_LOCALSTACK = "localstack"
SIGNER_DEVELOPMENT = "development"
SIGNER_STAGING = "staging"
SIGNER_PRODUCTION = "production"
SIGNER_ENVS = {
    SIGNER_LOCALSTACK,
    SIGNER_DEVELOPMENT,
    SIGNER_STAGING,
    SIGNER_PRODUCTION,
}

# File suffixes - Keep signed suffix separate
SUFFIX_BIN = "bin"
SUFFIX_ELF = "elf"
SUFFIXES = {
    SUFFIX_BIN,
    SUFFIX_ELF,
}
SUFFIX_SIGNED = "signed"

# Filter types to be used by decorators and in discovery
FILTER_TYPE_PRODUCT = "product"
FILTER_TYPE_PLATFORM = "platform"
FILTER_TYPE_ASSET = "asset"
FILTER_TYPE_SLOT = "slot"
FILTER_TYPE_SECURITY = "security"
FILTER_TYPE_ENVIRONMENT = "environment"
FILTER_TYPE_SUFFIX = "suffix"
FILTER_TYPES = {
    FILTER_TYPE_PRODUCT: PRODUCTS,
    FILTER_TYPE_PLATFORM: PLATFORMS,
    FILTER_TYPE_ASSET: ASSETS,
    FILTER_TYPE_SLOT: SLOTS,
    FILTER_TYPE_SECURITY: SECURITIES,
    FILTER_TYPE_ENVIRONMENT: ENVIRONMENTS,
    FILTER_TYPE_SUFFIX: SUFFIXES,
}
