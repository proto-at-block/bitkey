package bitkey.ui

import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * Generate Snapshot test cases for the annotated Presentation Model instance.
 * The Model should implement `ComposeModel` or have an `@Composable` function
 * in the same module which takes the Model as a parameter.
 *
 * Define a model using `ComposeModel` (preferred):
 * ```
 * data class MyPresentationModel(
 *   val transactions: List<Transaction> = emptyList(),
 *   val isLoading: Boolean = true,
 * ) : ComposeModel {
 *    override fun render(modifier: Modifier) {
 *      MyPresentationModelUiComposable(
 *        model = this,
 *        modifier = Modifier,
 *      )
 *    }
 * }
 * ```
 *
 * Alternatively, ensure the same module includes a matching `@Composable` function:
 * ```
 * data class MyPresentationModel(
 *   val transactions: List<Transaction> = emptyList(),
 *   val isLoading: Boolean = true,
 * )
 *
 * @Composable
 * fun MyPresentationModelScreen(
 *   val model: MyPresentationModel,
 *   val modifier: Modifier = Modifier,
 * ) {
 *   // ...
 * }
 * ```
 *
 *
 * Next to the model class definition, create test snapshot models:
 * ```
 * @Snapshot
 * val Snapshots.loadingWithData
 *    get() = MyPresentationModel(
 *      transactions = TEST_TRANSACTIONS,
 *      isLoading = true,
 *    )
 *
 * @Snapshot
 * val Snapshots.loadedWithoutData
 *    get() = MyPresentationModel(
 *      transactions = emptyList(),
 *      isLoading = false,
 *    )
 * ```
 */
@Retention(SOURCE)
@Target(PROPERTY)
annotation class Snapshot

/**
 * The base target for defining extension properties with snapshot test data.
 *
 * This object does not currently provide any explicit functionality,
 * it just acts as a namespace to hide snapshot data from autocomplete.
 *
 * @see Snapshot for usage of the snapshot test generation system.
 */
object SnapshotHost
