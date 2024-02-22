# Architecture Overview

At the root of it all we have the `BitkeyApplication` class which implements Android's `Application`, this is the entry point for the app, and where we manage the app's lifecycle.

The app only ever creates and manages a single Android `Activity` - the `MainActivity`.
The activity is where we define the entry point for UI (as well as manage activity's core lifecycle).

We use [Compose UI](https://developer.android.com/jetpack/compose) for writing all of the app's UI, relevant code is located in `android/ui` directory.

We use state machines for writing business logic. The state machines produce platform agnostic model objects.
The `MainActivity` hosts the very root state machine is responsible for managing the app's state and creation of the entire app's model tree. 
`App` Composable function takes in the root model and renders it in UI.

The state machines are implemented confirming KMP `StateMachine` interface, leveraging [Compose Runtime state management capabilities](https://code.cash.app/the-state-of-managing-state-with-compose).
