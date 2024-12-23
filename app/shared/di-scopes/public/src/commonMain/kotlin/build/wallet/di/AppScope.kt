package build.wallet.di

/**
 * Scope marker for the application scope, which gets created when the app launches. The
 * application scope stays alive as long as the app itself.
 *
 * To make an object a singleton within the application scope, use this marker class in conjunction
 * with [BitkeyInject], e.g.
 * ```
 * @BitkeyInject(AppScope::class)
 * class MyClass : SuperType {
 *     ...
 * }
 * ```
 *
 * To contribute a component interface to the application scope, use the `ContributesTo`
 * annotation:
 * ```
 * @ContributesTo(AppScope::class)
 * interface AbcComponent {
 *     @Provides fun provideAbc(): Abc = ...
 *
 *     val abc: Abc
 * }
 * ```
 */
abstract class AppScope private constructor()
