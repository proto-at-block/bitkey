import CloudKit
import Foundation
import Shared

/// Swift shim that uses `queryResultBlock`, which is not exposed to Kotlin/Native.
///
/// This bridges the Swift-only API into KMP and keeps error transport in a
/// Swift-friendly wrapper instead of throwing across the Kotlin boundary.
public final class CloudKitQueryRunnerImpl: Shared.CloudKitQueryRunner {
    public init() {}

    /// Fetch a single query page via `CKQueryOperation` and return records + cursor.
    ///
    /// Uses `recordMatchedBlock` for per-record callbacks and `queryResultBlock`
    /// for the final cursor or failure. The operation is cancelled if the
    /// Kotlin coroutine is cancelled.
    public func queryPage(
        database: CKDatabase,
        recordType: String,
        predicate: NSPredicate,
        cursor: CKQueryOperation.Cursor?,
        desiredKeys: [String]?
    ) async throws -> Shared.CloudKitQueryPageResult {
        let state = QueryState()

        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                Task { await state.setContinuation(continuation) }

                let op: CKQueryOperation
                if let cursor {
                    op = CKQueryOperation(cursor: cursor)
                } else {
                    let query = CKQuery(recordType: recordType, predicate: predicate)
                    op = CKQueryOperation(query: query)
                }

                if let desiredKeys {
                    op.desiredKeys = desiredKeys
                }

                op.recordMatchedBlock = { [state] _, result in
                    Task { await state.addRecordResult(result) }
                }

                op.queryResultBlock = { [state] result in
                    Task { await state.complete(with: result) }
                }

                Task { await state.setOperation(op) }
                database.add(op)
            }
        } onCancel: {
            Task { await state.cancel() }
        }
    }
}

/// Actor that serializes all state for a single query page operation.
/// Prevents races between CloudKit callbacks and task cancellation.
private actor QueryState {
    private var operation: CKQueryOperation?
    private var continuation: CheckedContinuation<Shared.CloudKitQueryPageResult, Never>?
    private var didResume = false
    private var records: [CKRecord] = []
    private var firstRecordError: NSError?

    func setOperation(_ op: CKQueryOperation) {
        operation = op
    }

    func setContinuation(_ cont: CheckedContinuation<Shared.CloudKitQueryPageResult, Never>) {
        continuation = cont
    }

    func addRecordResult(_ result: Result<CKRecord, Error>) {
        switch result {
        case let .success(record):
            records.append(record)
        case let .failure(error):
            if firstRecordError == nil {
                firstRecordError = error as NSError
            }
        }
    }

    func complete(with result: Result<CKQueryOperation.Cursor?, Error>) {
        guard !didResume, let cont = continuation else { return }
        didResume = true

        switch result {
        case let .success(queryCursor):
            if let recordError = firstRecordError {
                cont.resume(returning: Shared.CloudKitQueryPageResult.Err(error: recordError))
            } else {
                let page = Shared.CloudKitQueryPage(records: records, cursor: queryCursor)
                cont.resume(returning: Shared.CloudKitQueryPageResult.Ok(value: page))
            }
        case let .failure(error):
            cont.resume(returning: Shared.CloudKitQueryPageResult.Err(error: error as NSError))
        }
    }

    func cancel() {
        operation?.cancel()
        guard !didResume, let cont = continuation else { return }
        didResume = true
        let cancelError = NSError(
            domain: CKErrorDomain,
            code: CKError.Code.operationCancelled.rawValue,
            userInfo: nil
        )
        cont.resume(returning: Shared.CloudKitQueryPageResult.Err(error: cancelError))
    }
}
