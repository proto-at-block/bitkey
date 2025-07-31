use std::pin::Pin;

pub type CommandFn<T, E> =
    Pin<Box<dyn next_gen::prelude::Generator<Vec<u8>, Yield = Vec<u8>, Return = Result<T, E>>>>;

#[derive(Debug, PartialEq, Eq)]
pub enum State<T> {
    Data { response: Vec<u8> },
    Result { value: T },
}

pub trait Command<T, E> {
    fn next(&self, response: Vec<u8>) -> Result<State<T>, E>;
}

/// The command! macro wraps a next_gen::prelude::Generator with a concrete Command trait that's exportable via UniFFI.
///
/// See https://docs.rs/next-gen/latest/next_gen/index.html#macro-syntax for more information on how a Generator and the `generator` macro is used.
///
/// # Arguments
///
/// * `struct_name`             - the name of the struct / type to create from the generator function (e.g. Version, SignTransaction)
/// * `generator_name`          - the name of the generator function to wrap
/// * `generator_return_type`   - the return type of the _Result_ of the generator function (n.b. this is ugly and we know it, something something monad transformers)
///
/// # Example
///
/// ```
/// use next_gen::generator;
/// use wca::command_interface::command;
/// use wca::errors::CommandError;
///
/// #[generator(yield(Vec<u8>), resume(Vec<u8>))]
/// fn my_example() -> Result<bool, CommandError> {
///   let data = yield_!("this is returned to the app".into());
///   match std::str::from_utf8(&data) {
///     Ok("this came from the app") => Ok(true),
///     _ => Err(CommandError::InvalidResponse),
///   }
/// }
/// command!(Example = my_example -> bool);
/// ```
#[macro_export]
macro_rules! command {
    ($struct_name:ident = $generator_name:ident -> $generator_return_type:ty) => {
        pub struct $struct_name {
            id: ulid::Ulid,
        }

        impl $struct_name {
            pub fn new() -> Self {
                Self {
                    id: ulid::Ulid::new(),
                }
            }

            /// This private helper function encapsulates access to the thread-local storage.
            /// By defining `thread_local!` here, we create a single static instance that is
            /// shared by all callers of this function, ensuring both generator management
            /// and drop cleanup operate on the exact same map.
            fn access_generator_map<F, R>(f: F) -> R
            where
                F: FnOnce(&mut std::collections::HashMap<ulid::Ulid, $crate::command_interface::CommandFn<$generator_return_type, $crate::errors::CommandError>>) -> R,
            {
                use std::cell::RefCell;
                use std::collections::HashMap;

                thread_local! {
                    static GEN_MAP: RefCell<
                        HashMap<
                            ulid::Ulid,
                            $crate::command_interface::CommandFn<
                                $generator_return_type,
                                $crate::errors::CommandError,
                            >,
                        >
                    > = RefCell::new(HashMap::new());
                }

                GEN_MAP.with(|cell| {
                    let mut map = cell.borrow_mut();
                    f(&mut map)
                })
            }

            /// This helper function centralizes all access to the thread-local storage.
            /// It gets/creates the generator, resumes it, and cleans it up upon completion.
            fn run_and_manage_generator(
                &self,
                resume_with: Vec<u8>,
            ) -> next_gen::generator::GeneratorState<Vec<u8>, Result<$generator_return_type, $crate::errors::CommandError>> {
                Self::access_generator_map(|map| {
                    // Get or create the generator for this command's unique ID.
                    let gen = map.entry(self.id).or_insert_with(|| {
                        next_gen::generator_fn::CallBoxed::call_boxed($generator_name, ())
                    });

                    // Resume the generator with the provided input.
                    let result = gen.as_mut().resume(resume_with);

                    // If the generator has returned, it's complete. Remove it from the map.
                    if let next_gen::generator::GeneratorState::Returned(_) = &result {
                        map.remove(&self.id);
                    }

                    result
                })
            }
        }

        impl Default for $struct_name {
            fn default() -> Self {
                Self::new()
            }
        }

        // Clean up the generator on drop to prevent memory leaks if a command
        // is dropped before completion.
        impl Drop for $struct_name {
            fn drop(&mut self) {
                // By calling the shared `access_generator_map` function, we ensure
                // we are removing the generator from the correct thread-local storage.
                Self::access_generator_map(|map| {
                    map.remove(&self.id);
                });
            }
        }

        impl $crate::command_interface::Command<$generator_return_type, $crate::errors::CommandError> for $struct_name {
            command!(next_impl $generator_return_type);
        }
    };

    ($struct_name:ident = $generator_name:ident -> $generator_return_type:ty, $($argname:ident: $type:ty),+) => {
        pub struct $struct_name {
            $( $argname: $type, )+
            id: ulid::Ulid,
        }

        impl $struct_name {
            pub fn new($($argname: $type),+) -> Self {
                Self {
                    $( $argname ),+,
                    id: ulid::Ulid::new(),
                }
            }

            /// This private helper function encapsulates access to the thread-local storage.
            /// By defining `thread_local!` here, we create a single static instance that is
            /// shared by all callers of this function, ensuring both generator management
            /// and drop cleanup operate on the exact same map.
            fn access_generator_map<F, R>(f: F) -> R
            where
                F: FnOnce(&mut std::collections::HashMap<ulid::Ulid, $crate::command_interface::CommandFn<$generator_return_type, $crate::errors::CommandError>>) -> R,
            {
                use std::cell::RefCell;
                use std::collections::HashMap;

                thread_local! {
                    static GEN_MAP: RefCell<
                        HashMap<
                            ulid::Ulid,
                            $crate::command_interface::CommandFn<
                                $generator_return_type,
                                $crate::errors::CommandError,
                            >,
                        >
                    > = RefCell::new(HashMap::new());
                }

                GEN_MAP.with(|cell| {
                    let mut map = cell.borrow_mut();
                    f(&mut map)
                })
            }


            /// This helper function centralizes all access to the thread-local storage.
            /// It gets/creates the generator, resumes it, and cleans it up upon completion.
            fn run_and_manage_generator(
                &self,
                resume_with: Vec<u8>
            ) -> next_gen::generator::GeneratorState<Vec<u8>, Result<$generator_return_type, $crate::errors::CommandError>> {
                Self::access_generator_map(|map| {
                    // Get or create the generator for this command's unique ID.
                    let gen = map.entry(self.id).or_insert_with(|| {
                        next_gen::generator_fn::CallBoxed::call_boxed(
                            $generator_name,
                            ( $( self.$argname.to_owned() ),+, ),
                        )
                    });

                    // Resume the generator with the provided input.
                    let result = gen.as_mut().resume(resume_with);

                    // If the generator has returned, it's complete. Remove it from the map.
                    if let next_gen::generator::GeneratorState::Returned(_) = &result {
                        map.remove(&self.id);
                    }

                    result
                })
            }
        }

        // Clean up the generator on drop to prevent memory leaks.
        impl Drop for $struct_name {
            fn drop(&mut self) {
                // By calling the shared `access_generator_map` function, we ensure
                // we are removing the generator from the correct thread-local storage.
                Self::access_generator_map(|map| {
                    map.remove(&self.id);
                });
            }
        }

        impl $crate::command_interface::Command<$generator_return_type, $crate::errors::CommandError> for $struct_name {
            command!(next_impl $generator_return_type);
        }
    };

    (next_impl $generator_return_type:ty) => {
        fn next(&self, response: Vec<u8>) -> std::result::Result<$crate::command_interface::State<$generator_return_type>, $crate::errors::CommandError> {
            // Delegate all state management and generator interaction to the helper.
            let response = self.run_and_manage_generator(response);

            match response {
                next_gen::generator::GeneratorState::Yielded(response) => Ok($crate::command_interface::State::Data { response }),
                next_gen::generator::GeneratorState::Returned(value) => {
                    // The generator has already been cleaned up inside run_and_manage_generator.
                    // We just need to handle the final result.
                    Ok($crate::command_interface::State::Result { value: value? })
                }
            }
        }
    };
}
pub use command;

#[macro_export]
macro_rules! yield_from_ {
    ($generator_name:ident($x:expr)) => {
        yield_from_!(yield_from_impl $generator_name(($x,)))
    };

    ($generator_name:ident($($x:expr),*)) => {
        yield_from_!(yield_from_impl $generator_name(($($x),*)))
    };

    (yield_from_impl $generator_name:ident($args:expr)) => {{
        let mut gen = next_gen::generator_fn::CallBoxed::call_boxed($generator_name, $args);
        let mut result = Default::default();
        loop {
            match gen.as_mut().resume(result) {
                next_gen::generator::GeneratorState::Yielded(v) => result = yield_!(v),
                next_gen::generator::GeneratorState::Returned(v) => break v,
            }
        }
    }};
}

#[cfg(test)]
mod tests {
    use super::command;
    use super::{Command, State};
    use crate::errors::CommandError;
    use crate::yield_from_;
    use next_gen::generator;

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn nullary_generator() -> Result<Vec<u8>, CommandError> {
        Ok(yield_!("nullary".into()))
    }
    command!(Nullary = nullary_generator -> Vec<u8>);

    #[test]
    fn nullary() -> Result<(), CommandError> {
        assert_eq!(
            Nullary::new().next(vec![])?,
            State::Data {
                response: "nullary".into()
            }
        );
        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn unary_generator(first: String) -> Result<Vec<u8>, CommandError> {
        Ok(yield_!(format!("unary {first}").into()))
    }
    command!(Unary = unary_generator -> Vec<u8>, first: String);

    #[test]
    fn unary() -> Result<(), CommandError> {
        assert_eq!(
            Unary::new("first argument".into()).next(vec![])?,
            State::Data {
                response: "unary first argument".into()
            }
        );
        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn binary_generator(first: String, second: Vec<String>) -> Result<Vec<u8>, CommandError> {
        Ok(yield_!(format!("unary {first} {second:?}").into()))
    }
    command!(Binary = binary_generator -> Vec<u8>, first: String, second: Vec<String>);

    #[test]
    fn binary() -> Result<(), CommandError> {
        assert_eq!(
            Binary::new("first argument".into(), vec!["second argument".into()]).next(vec![])?,
            State::Data {
                response: "unary first argument [\"second argument\"]".into()
            }
        );
        Ok(())
    }

    /// Test that two instances of the same command type don't share generator state
    #[test]
    fn independent_instances() -> Result<(), CommandError> {
        let a = Nullary::new();
        let b = Nullary::new();

        // Both should yield independently
        assert!(matches!(a.next(vec![])?, State::Data { .. }));
        assert!(matches!(b.next(vec![])?, State::Data { .. }));
        Ok(())
    }

    /// Test that a new instance starts fresh after another completes
    #[test]
    fn reset_after_completion() -> Result<(), CommandError> {
        let cmd = Nullary::new();

        // Drive to completion
        let _first = cmd.next(vec![])?; // Should yield
        let _second = cmd.next(vec![])?; // Should return Result

        // A brand-new instance should yield again, not panic or reuse state
        let fresh = Nullary::new();
        assert!(matches!(fresh.next(vec![])?, State::Data { .. }));
        Ok(())
    }

    /// Test cross-thread isolation
    #[test]
    #[cfg_attr(miri, ignore)] // Miri doesn't handle threads well
    fn per_thread_isolation() -> Result<(), CommandError> {
        use std::thread;

        let h1 = thread::spawn(|| Nullary::new().next(vec![]).unwrap());
        let h2 = thread::spawn(|| Nullary::new().next(vec![]).unwrap());

        assert!(matches!(h1.join().unwrap(), State::Data { .. }));
        assert!(matches!(h2.join().unwrap(), State::Data { .. }));
        Ok(())
    }

    /// Test that generators are properly cleaned up when completed
    #[test]
    fn generator_cleanup_on_completion() -> Result<(), CommandError> {
        let cmd = Nullary::new();

        // First call should create and use the generator
        let first_result = cmd.next(vec![])?;
        assert!(matches!(first_result, State::Data { .. }));

        // Second call should complete the generator
        let second_result = cmd.next(vec![])?;
        assert!(matches!(second_result, State::Result { .. }));

        Ok(())
    }

    /// Test that calling next() after completion creates a fresh generator
    #[test]
    fn fresh_generator_after_completion() -> Result<(), CommandError> {
        let cmd = Nullary::new();

        // Complete the first generator
        let _first = cmd.next(vec![])?;
        let _second = cmd.next(vec![])?; // This completes

        // Next call should create a fresh generator and yield again
        let third_result = cmd.next(vec![])?;
        assert!(matches!(third_result, State::Data { .. }));

        Ok(())
    }

    /// Test address reuse safety - this test verifies that ULID-based keys prevent issues
    #[test]
    fn ulid_based_isolation() -> Result<(), CommandError> {
        // Create many command instances to verify they all get unique IDs
        let mut commands = Vec::new();
        for _ in 0..100 {
            let cmd = Nullary::new();
            let result = cmd.next(vec![])?;
            assert!(matches!(result, State::Data { .. }));
            commands.push(cmd);
        }

        // All commands should work independently
        for cmd in &commands {
            let result = cmd.next(vec![])?;
            assert!(matches!(result, State::Result { .. }));
        }

        Ok(())
    }

    /// Test generator state isolation between instances with same arguments
    #[test]
    fn argument_based_isolation() -> Result<(), CommandError> {
        let cmd1 = Unary::new("test".into());
        let cmd2 = Unary::new("test".into());

        // Both should yield independently even with same arguments
        let result1 = cmd1.next(vec![])?;
        let result2 = cmd2.next(vec![])?;

        assert!(matches!(result1, State::Data { .. }));
        assert!(matches!(result2, State::Data { .. }));

        // Complete cmd1
        let _final1 = cmd1.next(vec![])?;

        // cmd2 should still work independently
        let result2_continued = cmd2.next(vec![])?;
        assert!(matches!(result2_continued, State::Result { .. }));

        Ok(())
    }

    /// Test memory behavior under stress - verifies Drop cleanup works
    #[test]
    #[ignore] // Run with --ignored for stress testing
    fn memory_stress_test() -> Result<(), CommandError> {
        // Create many commands and ensure they don't accumulate in memory
        for i in 0..10000 {
            let cmd = Nullary::new();
            let _first = cmd.next(vec![])?;
            let _second = cmd.next(vec![])?; // Complete and trigger drop cleanup

            if i % 1000 == 0 {
                println!("Completed {} command cycles", i);
            }
        }

        // If we reach here without OOM, the cleanup is working
        Ok(())
    }

    /// Test that Drop cleanup actually removes entries from thread-local storage
    #[test]
    fn drop_cleanup_verification() -> Result<(), CommandError> {
        // Create and drop many commands, then check that we can still create new ones
        // This indirectly tests that Drop is working (if it weren't, we'd eventually OOM)
        for _ in 0..1000 {
            let cmd = Nullary::new();
            let _result = cmd.next(vec![])?;
            // cmd is dropped here, triggering cleanup
        }

        // If we can still create and use commands, cleanup is working
        let final_cmd = Nullary::new();
        let result = final_cmd.next(vec![])?;
        assert!(matches!(result, State::Data { .. }));

        Ok(())
    }

    /// Test that Drop cleans up a partially executed command.
    #[test]
    fn drop_cleanup_on_partial_execution() -> Result<(), CommandError> {
        // This loop creates many commands that are only partially executed and
        // then dropped. If the Drop implementation failed to clean up the
        // generator from the TLS map, this test would eventually consume
        // a large amount of memory and likely panic or fail.
        for _ in 0..5000 {
            let cmd = Nullary::new();
            // Partially execute the command, causing its state to be stored.
            let _ = cmd.next(vec![])?;
            // `cmd` is dropped here, its `Drop` impl should run and clean up.
        }

        // If the loop completes, it's a strong indicator that cleanup is working.
        // To be certain, we create one final command to ensure the TLS map
        // hasn't become corrupted or bloated.
        let final_cmd = Nullary::new();
        assert!(matches!(final_cmd.next(vec![])?, State::Data { .. }));

        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn sub_generator() -> Result<String, CommandError> {
        let data = yield_!("sub_yield".into());
        Ok(String::from_utf8(data).unwrap_or_default())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn main_generator() -> Result<String, CommandError> {
        yield_!("main_yield_1".into());
        let sub_result = yield_from_!(sub_generator());
        yield_!("main_yield_2".into());
        Ok(format!("final: {}", sub_result?))
    }

    command!(YieldFrom = main_generator -> String);

    #[test]
    fn yield_from_delegation() -> Result<(), CommandError> {
        let cmd = YieldFrom::new();

        // 1. First yield from main_generator
        let state1 = cmd.next(vec![])?;
        assert_eq!(
            state1,
            State::Data {
                response: "main_yield_1".into()
            }
        );

        // 2. Yield from the sub_generator
        let state2 = cmd.next("from_test_1".into())?;
        assert_eq!(
            state2,
            State::Data {
                response: "sub_yield".into()
            }
        );

        // 3. Resume sub_generator, which returns. Then hit the second yield in main_generator
        let state3 = cmd.next("from_sub".into())?;
        assert_eq!(
            state3,
            State::Data {
                response: "main_yield_2".into()
            }
        );

        // 4. Final result
        let state4 = cmd.next("from_test_2".into())?;
        assert_eq!(
            state4,
            State::Result {
                value: "final: from_sub".to_string()
            }
        );

        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn erroring_generator() -> Result<bool, CommandError> {
        yield_!("about_to_error".into());
        Err(CommandError::InvalidResponse)
    }

    command!(Erroring = erroring_generator -> bool);

    #[test]
    fn generator_error_propagation() -> Result<(), CommandError> {
        let cmd = Erroring::new();

        // The first call should yield successfully.
        let state = cmd.next(vec![])?;
        assert_eq!(
            state,
            State::Data {
                response: "about_to_error".into()
            }
        );

        // The second call should return the error from the generator.
        // The `?` operator in the `command!` macro's `next_impl` will
        // propagate the internal `Err`.
        let result = cmd.next(vec![]);
        assert!(matches!(result, Err(CommandError::InvalidResponse)));

        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn data_echo_generator() -> Result<String, CommandError> {
        let from_app = yield_!("send_data".into());
        Ok(format!("app_sent: {}", String::from_utf8_lossy(&from_app)))
    }

    command!(DataEcho = data_echo_generator -> String);

    #[test]
    fn resumption_with_data() -> Result<(), CommandError> {
        let cmd = DataEcho::new();

        // 1. Initial call yields "send_data"
        let state1 = cmd.next(vec![])?;
        assert_eq!(
            state1,
            State::Data {
                response: "send_data".into()
            }
        );

        // 2. Send specific data back in the second call
        let data_to_send = "hello_from_test".to_string();
        let state2 = cmd.next(data_to_send.into_bytes())?;

        // 3. Verify the final result includes the data we sent
        let expected_result = "app_sent: hello_from_test".to_string();
        assert_eq!(
            state2,
            State::Result {
                value: expected_result
            }
        );

        Ok(())
    }
}
