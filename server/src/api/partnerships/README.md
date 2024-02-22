## What is this?

Partnerships API definitions. These APIs are exposed by the Server if the partnerships feature is enabled.

## How can I get started?

Access to the wallet-partnerships repository is required.
Cargo needs to be configured to use git cli to properly operate on the git repositories accesses with certificate-based authorization.
The necessary flag is set through hermit.

In case of using a profile-wide rust-analyzer, in order for the IDE's rust-analyzer to be able to analyze the project after enabling, this variable needs to be profile wide, for example set in ~/.zprofile:
```
export CARGO_NET_GIT_FETCH_WITH_CLI=true
```

To build the Server API with partnerships, run the following command from the root of the repository:
```
cd server
cargo build --features partnerships
```

