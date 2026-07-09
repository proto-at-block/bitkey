use std::future::Future;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::time::Duration;

use queue::sqs::SqsQueue;
use tokio::time::timeout;
use tracing::{event, info_span, Instrument, Level};

use crate::error::WorkerError;
use crate::jobs::WorkerState;

// Defense-in-depth hang detector for the polling loop. Tuned to "could only
// fire if something is genuinely stuck," not to bound normal-case latency:
// the per-call timeouts on the underlying AWS/HTTP clients already bound
// healthy operation, so this just needs to catch a future that never resolves.
// 10 minutes is well above any plausible healthy batch under the configured
// per-call timeouts and well below the multi-hour silent stall this is meant
// to catch.
const ITERATION_TIMEOUT: Duration = Duration::from_secs(600);

// Runs `operation` once per fetched SQS message. A message is deleted from
// SQS only if `operation` returns Ok; on Err we log and leave the message in
// the queue so SQS visibility-timeout redelivery can retry it. Errors out of
// any one message therefore do not affect other messages in the same batch
// or stop the loop.
pub(crate) async fn sqs_job_handler<F, Fut>(
    state: &WorkerState,
    queue_url: String,
    operation: F,
) -> Result<(), WorkerError>
where
    F: Fn(String) -> Fut,
    Fut: Future<Output = Result<(), WorkerError>>,
{
    let run_once = matches!(state.sqs, SqsQueue::Test(_));
    let terminate_flag = Arc::new(AtomicBool::new(false));
    let handler_terminate_flag = terminate_flag.clone();
    if !run_once {
        ctrlc::set_handler(move || {
            handler_terminate_flag.store(true, Ordering::SeqCst);
        })
        .map_err(WorkerError::SetupSigtermHandler)?;
    }

    while !terminate_flag.load(Ordering::SeqCst) {
        // Each iteration runs under its own span so each loop turn becomes its
        // own root trace, instead of every iteration accumulating under a span
        // that lives for the worker's entire lifetime.
        let iteration = async {
            let sqs_messages = state.sqs.fetch_messages(&queue_url).await?;

            let mut to_delete = Vec::with_capacity(sqs_messages.len());
            for msg in &sqs_messages {
                let body = msg.body().unwrap_or_default().to_string();
                match operation(body).await {
                    Ok(()) => to_delete.push(msg.clone()),
                    Err(e) => event!(
                        Level::ERROR,
                        "Failed to process SQS message from {}: {e}; leaving for redelivery",
                        queue_url,
                    ),
                }
            }

            state.sqs.delete_messages(&queue_url, to_delete).await?;
            Ok::<(), WorkerError>(())
        }
        .instrument(info_span!("sqs_iteration", queue_url = %queue_url));

        match timeout(ITERATION_TIMEOUT, iteration).await {
            Ok(result) => result?,
            Err(_elapsed) => {
                // Drop the iteration future and continue. Any messages already
                // fetched will become visible again after their SQS visibility
                // timeout and be retried on a future iteration.
                event!(
                    Level::ERROR,
                    "SQS worker iteration exceeded {:?} for queue {}; abandoning iteration and continuing. SQS visibility timeout will redeliver any in-flight messages.",
                    ITERATION_TIMEOUT,
                    queue_url,
                );
            }
        }

        if run_once {
            break;
        }
    }
    Ok(())
}
