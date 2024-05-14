### Description

Sample app that demonstrates our app architecture
and [patterns](https://docs.wallet.build/guides/mobile/architecture/patterns/) at a smaller scale.

Additionally, this sample app can be used as a playground to experiment with new patterns and ideas before integrating
them into the main app.

### Implementation

- `SampleUiAppStateMachine` is the top level UI state machine that produces `ScreenModel`s to be rendered by the app.
- Only Android app is implemented at the moment
    - Reuses UI components from our design system

### TODOs

- [ ] Add sample tests that demonstrate
  our [testing patterns](https://docs.wallet.build/guides/mobile/testing/overview/#test-types).
    - [ ] State machine tests
    - [ ] Unit tests
    - [ ] Integration tests
- [ ] Implement DAOs using real SqlDelight database.
- [ ] Add iOS app integration.
- [ ] Demonstrate various model presentation styles: sheets, alerts.