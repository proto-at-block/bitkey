group "server" {
    targets = [
        "api",
        "wsm",
    ]
}

group "wsm" {
    targets = [
        "wsm-api",
        "wsm-enclave",
    ]
}

target "api" {
    contexts = {
        core = "../core"
    }
    dockerfile = "Dockerfile.server"
    target = "deployable"
    tags = ["api:latest"]
}

target "wsm-api" {
    contexts = {
        core = "../core"
    }
    dockerfile = "Dockerfile.wsm-api"
    target = "deployable"
    tags = ["wsm-api:latest"]
}

target "wsm-enclave" {
    contexts = {
        kmstool-enclave-cli = "target:kmstool-enclave-cli"
        core = "../core"
    }
    dockerfile = "Dockerfile.wsm"
    target = "deployable"
    tags = ["wsm-enclave:latest"]
}

target "kmstool-enclave-cli" {
    context = "src/wsm/third-party"
    dockerfile = "Dockerfile.aws-nitro-enclave-sdk-c-alpine"
    tags = ["kmstool-enclave-cli:latest"]
}

target "nitro-cli" {
    context = "src/wsm"
    target = "nitro-cli"
    dockerfile = "Dockerfile.nitro-cli"
    tags = ["nitro-cli:latest"]
}