# Why do we have `buildSrc` and `gradle/build-logic`

It's recommended to move away from `buildSrc`,
and use a `includeBuild` instead for convention plugins.
However, to work around [a bug in Kotlin Gradle Plugin (KGP)](https://youtrack.jetbrains.com/issue/KT-65131/Incorrect-compilation-name-for-custom-test-runs-KotlinJvmTest-tasks-in-KMP-modules),
we need to make "shadow" a class from KGP.
The safest way is to put it to `buildSrc`,
because it's loaded by a parent classloader to any `build.gradle.kts` classloader.
That means our `KotlinJvmTest` will be loaded,
instead of the one from KGP.

The reason why we need to replace the class is explained in [KT-64131](https://youtrack.jetbrains.com/issue/KT-65131/Incorrect-compilation-name-for-custom-test-runs-KotlinJvmTest-tasks-in-KMP-modules).
In short, there's no way to make KGP instantiate a different class.
So we need to resort to replacing it.

In our implementation of `KotlinJvmTest`,
we add a property `compilation`,
which returns an instance of our own `data class KotlinCompilationNameContainer`.
We can't use the `KotlinJvmCompilation` from KGP, 
because it's not serializable and breaks Gradle Configuration Cache.
The `KotlinCompilationNameContainer` then has a single property `compilationName`.
During sync, `KotlinTargetBuilder.buildTestRunTasks` looks at test tasks to provide information to the IDE.
It uses reflection to call `getCompilation` on any test task and then `getCompilationName` on the returned instance.
Thankfully it doesn't use a specific class for reflection metadata.
Instead, it asks the instance's class directly.
That's why we can sneak in the `KotlinCompilationNameContainer`.

We provide our `KotlinJvmTest` with a compilation name in [`KotlinMultiplatformExtension.configureJvmTarget()`](../gradle/build-logic/src/main/kotlin/build/wallet/gradle/logic/extensions/KotlinMultiplatformExtension.kt).
We have to use reflection here ourselves,
because Kotlin compiler considers the original `KotlinJvmTest` class from KGP the one we compile against.
That results in `unknown reference setCompilationName` error.
Our `gradle/build-logic` also doesn't (and can't) depend on `buildSrc`,
so from its point of view, KGP's `KotlinJvmTest` is the only implementation at compile time.

Once this issue is fixed, we can remove the whole `buildSrc` and forget this ever happened.
