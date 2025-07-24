# DAO (Data Access Object) Pattern

A DAO (Data Access Object) is a component responsible for managing data persistence. It provides mechanisms for reading from and writing to local storage facilities such as databases (e.g., using SqlDelight with a SQLite database), local settings (e.g., `Keychain` on iOS, `SharedPreferences` on Android), or file systems.

The DAO ensures data integrity and consistency across the application while hiding the specifics of the storage mechanism from other components.

## Module Structure

DAOs follow a specific module organization pattern:

- **Interface and Implementation**: Both the DAO interface and its implementation are placed in the `:impl` module
- **Not Exposed**: DAO interfaces are NOT exposed in `:public` modules  
- **Testing**: Fake implementations are provided in `:fake` modules for testing

## Example: CoachmarkDao

```kotlin
// domain/coachmark/impl/src/commonMain/kotlin/.../CoachmarkDao.kt
interface CoachmarkDao {
  suspend fun insertCoachmark(id: CoachmarkIdentifier, expiration: Instant): Result<Unit, Error>
  suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, Error>
  suspend fun getAllCoachmarks(): Result<List<Coachmark>, Error>
  // ... other methods
}

// domain/coachmark/impl/src/commonMain/kotlin/.../CoachmarkDaoImpl.kt
@BitkeyInject(AppScope::class)
class CoachmarkDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : CoachmarkDao {
  // Implementation using SQLDelight
}

// domain/coachmark/fake/src/commonMain/kotlin/.../CoachmarkDaoFake.kt
class CoachmarkDaoFake : CoachmarkDao {
  // In-memory fake implementation for testing
}
```

## Key Patterns

1. **Result Types**: All DAO methods return `Result<T, Error>` for error handling
2. **Suspend Functions**: Database operations are suspend functions for async execution
3. **Dependency Injection**: Implementations use `@BitkeyInject` for DI
4. **Database Provider**: Use `BitkeyDatabaseProvider` to access the database
5. **SQLDelight Integration**: Use extension functions like `awaitTransactionWithResult()`, `awaitAsOneOrNullResult()`, `awaitAsListResult()`

## Service Layer

DAOs are typically consumed by Service classes that provide higher-level business logic:

```kotlin
// domain/coachmark/public/src/commonMain/kotlin/.../CoachmarkService.kt
interface CoachmarkService {
  // Business logic methods exposed to the rest of the app
}

// domain/coachmark/impl/src/commonMain/kotlin/.../CoachmarkServiceImpl.kt
class CoachmarkServiceImpl(
  private val coachmarkDao: CoachmarkDao, // DAO injected here
) : CoachmarkService {
  // Service implementation using DAO
}
```

## Testing

- **Unit Tests**: Test DAO implementations in `:impl` modules
- **Fake Objects**: Use fake implementations from `:fake` modules in higher-level tests

This pattern ensures proper separation of concerns, testability, and encapsulation of data access logic.
