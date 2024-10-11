# FormBodyModel SnapshotTestModels generator

Generates `SnapshotTestModels` containing factory methods for `FormBodyModel` implementations.

Example:

```kotlin
public object SnapshotTestModels {
    public fun CreateAccountAccessMoreOptionsFormBodyModel(
        onBack: () -> Unit,
        onRestoreYourWalletClick: () -> Unit,
        onBeTrustedContactClick: () -> Unit,
        onResetExistingDevice: (() -> Unit)?,
    ): FormBodyModel = AccountAccessMoreOptionsFormBodyModel(
        onBack = onBack,
        onRestoreYourWalletClick = onRestoreYourWalletClick,
        onBeTrustedContactClick = onBeTrustedContactClick,
        onResetExistingDevice = onResetExistingDevice,
    )
}
```

This is required for `FormBodyModel` because it contains an `@Composable` method, meaning concrete implementations
are not exported to Swift, but they are required for iOS snapshot tests.
We will remove this code generation if/when the remaining iOS UI is transitioned to Compose.