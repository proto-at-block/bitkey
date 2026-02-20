define_enum!(Action, ParseActionError, "invalid action: {0}" {
    Add,
    Remove,
    Set,
    Disable,
    Accept
});

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn action_from_str_exact_case() {
        // Canonical case succeeds
        assert_eq!("Add".parse::<Action>().unwrap(), Action::Add);
        assert_eq!("Remove".parse::<Action>().unwrap(), Action::Remove);

        // Wrong case fails
        assert!("add".parse::<Action>().is_err());
        assert!("ADD".parse::<Action>().is_err());
    }

    #[test]
    fn action_from_str_invalid() {
        assert!("invalid".parse::<Action>().is_err());
        assert!("".parse::<Action>().is_err());
    }
}
