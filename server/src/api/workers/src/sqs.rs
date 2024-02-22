use std::future::Future;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;

use queue::sqs::SqsQueue;

use crate::error::WorkerError;
use crate::jobs::WorkerState;

pub(crate) async fn sqs_job_handler<F, Fut>(
    state: &WorkerState,
    queue_url: String,
    operation: F,
) -> Result<(), WorkerError>
where
    F: Fn(Vec<String>) -> Fut,
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
        let sqs_messages;
        let serialized_messages = {
            sqs_messages = state.sqs.fetch_messages(&queue_url).await?;
            sqs_messages
                .iter()
                .map(|m| m.body().unwrap_or_default().to_string())
                .collect()
        };

        operation(serialized_messages).await?;

        state
            .sqs
            .delete_messages(&queue_url, sqs_messages.clone())
            .await?;

        if run_once {
            break;
        }
    }
    Ok(())
}
