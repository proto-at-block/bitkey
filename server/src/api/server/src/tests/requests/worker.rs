use types::notification::NotificationChannel;
use workers::jobs::WorkerState;

pub struct TestWorker {
    state: WorkerState,
}

impl TestWorker {
    pub(crate) async fn new(state: WorkerState) -> Self {
        Self { state }
    }

    pub(crate) async fn scheduled_notification(&self) {
        workers::jobs::scheduled_notification::run_once(self.state.clone())
            .await
            .unwrap();
    }

    pub(crate) async fn customer_notification(&self, notification_channel: NotificationChannel) {
        workers::jobs::customer_notification::handler(&self.state.clone(), notification_channel)
            .await
            .unwrap();
    }

    pub(crate) async fn blockchain_polling(&self) {
        workers::jobs::blockchain_polling::run_once(&self.state.clone())
            .await
            .unwrap();
    }
}
