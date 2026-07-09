This module contains domain-specific components for creating the Emergency Exit
Kit PDF, saving it to the customer's cloud file storage, reading it from cloud
storage, and restoring a minimal offline account from an EEK payload.

The EEK payload stores the hardware-sealed CSEK and the active spending key
backup encrypted by that CSEK. Restoring an EEK payload decrypts that backup,
stores the app spending key locally, and creates a `FullAccountConfig` with
`F8eEnvironment.ForceOffline`. That restored account is intentionally limited:
it has enough data to move funds, not enough data to operate the full online app
experience.

The Android emergency app variant is packaged separately under
`app/android/app/src/emergency`. It is the break-glass build used to import EEK
material, and many normal app features are disabled in that mode.

Primary code anchors:

- `EmergencyExitKitPayload` defines the payload and backup data.
- `EmergencyExitPayloadCreatorImpl` creates the sealed backup payload.
- `EmergencyExitPayloadRestorerImpl` restores a force-offline account from the
  payload.
- `EmergencyExitKitPdfGeneratorImpl` creates the PDF data.
- `EmergencyExitKitRepositoryImpl` saves and loads EEK data from cloud file
  storage.

Currently, the Android PDF snapshot is be created manually:
1. Run the `EmergencyExitKitSnapshotTest` in IntelliJ.
2. Go to the Device Explorer > data > data > build.wallet.domain.emergency.exit.kit.impl.test > files.
3. Right-click on "Emergency Exit Kit.pdf" and Save As.
4. Copy the PDF to `app/domain/emergency-exit-kit/impl/src/commonTest/snapshots/Emergency Exit Kit.pdf`.

Similarly, the iOS PDF snapshot is created manually (see BKR-1052 for reason):
1. Go to `EmergencyExitKitPdfSnapshotTests.test_eek_pdf()` and temporarily comment out the first `throw XCTSkip…` line.
2. Run the iOS snapshots in record mode.
3. Commit the updated `test_eek_pdf.1.pdf` file.
