## KotlinMultiplatformRustPlugin

This plugin compiles Rust and generates Kotlin bindings for Rust using Unifii.

The plugin declares the `rust` Gradle extension, which is used to configure the compilation and targets. 
The DSL is similar to the one used by the KMP plugin - with each KMP target having a dedicated configuration block specifying supported platforms and containing target-specific configuration.

Based on the target configuration, the plugin creates two different kinds of tasks:
- `compileRust${KmpTarget}${Platform}${Profile?}` (for example: `compileRustAndroidArm32Debug`)
- `generateKotlinRustBindings${KmpTarget}` (for example: `generateKotlinRustBindingsAndroid`)

The actual names of the created tasks are derived using a similar naming scheme to the one used by the KMP plugin.

Each supported KMP target will have at least one `compileRust` task, which produces a library with the compiled Rust code. 
Each supported platform for a given KMP target has its dedicated task for each supported profile. 
For example, Android supports both Debug and Release profiles, but JVM supports only Debug (because Release is currently not needed).

The `compileRust` tasks internally use `rustup` and `cargo`, so they are basically wrappers around these tools. 
`rustup` is used to install the required toolchains. `cargo` is then used to perform the Rust compilation.

The used commands differ slightly between individual platforms.
For example, Android additionally uses the `cargo-ndk` extension to set up some environment variables which are important for the cross-compilation (the task configures the remaining required environment variables).

Then there are the `generateKotlinRustBindings` tasks. 
This task uses Uniffi to generate Kotlin code with Rust bindings. 
This code is then compiled by the Kotlin compiler (done by different tasks) for each KMP target - so there is only one bindings task for each of those targets (no matter how many platforms there are).

During the tasks configuration, the plugin connects the outputs of these tasks to inputs of appropriate tasks from the KMP and Android plugins. 
This ensures that the produced binaries and generated Kotlin code are packaged into the artifacts produced by the KMP and Android plugins.
In the case of Android, this final artifact is the aar and for JVM it's the jar.
The final artifacts are still produced by the usual tasks from KMP and Android, which now depend on the custom tasks. 
For example, in the case of JVM the final jar can be produced by calling the `jvmJar` task.

All tasks defined by this plugin use the "_build/rust" directory as their build directory.
This directory also contains the Rust "target" directory, which is shared with other (non-Gradle) scripts. 

To verify that the custom tasks work correctly, you can, for example, call the given task and inspect the produced output. 
However, a more complete test would be to create all the KMP artifacts (for example, by calling the `build` task). 
An artifact can then be verified by using it as a dependency of some other Kotlin module, which will call some Rust function. 
There are already integration and unit tests that use the KMP bindings, so those indirectly verify the plugin’s core functionality.
