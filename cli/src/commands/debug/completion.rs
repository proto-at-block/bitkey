use rustyline::completion::{Completer, Pair};
use rustyline::highlight::Highlighter;
use rustyline::hint::Hinter;
use rustyline::validate::Validator;
use rustyline::{Context, Helper};

pub struct DebugCompleter;

impl Completer for DebugCompleter {
    type Candidate = Pair;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        _ctx: &Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Pair>)> {
        const COMMANDS: &[&str] = &[
            "help",
            "h",
            "exit",
            "quit",
            "q",
            "clear",
            "load-account",
            "load-descriptors",
            "list-keysets",
            "list-addresses",
            "list-recoveries",
            "list-relationships",
            "list-challenges",
            "list-claims",
            "prepare-funding",
            "set-gap-limit",
            "set-electrum-server",
        ];

        let input = &line[..pos];
        let mut matches = Vec::new();

        // Find the start of the current word
        let start = input.rfind(' ').map(|i| i + 1).unwrap_or(0);
        let word = &input[start..];

        // Find matching commands
        for &cmd in COMMANDS {
            if cmd.starts_with(word) {
                matches.push(Pair {
                    display: cmd.to_string(),
                    replacement: cmd.to_string(),
                });
            }
        }

        Ok((start, matches))
    }
}

impl Hinter for DebugCompleter {
    type Hint = String;
}

impl Highlighter for DebugCompleter {}

impl Validator for DebugCompleter {}

impl Helper for DebugCompleter {}
