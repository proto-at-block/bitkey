@file:OptIn(ExperimentalCompilerApi::class)

package build.wallet.di.codegen

import build.wallet.ksp.util.test.compilation
import build.wallet.ksp.util.test.getKspGeneratedFiles
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BitkeyInjectSymbolProcessorTests {
  @Test
  fun `generate component interface with binding in AppScope`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        interface Base
        
        @BitkeyInject(AppScope::class)
        class BaseImpl : Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase(implementation: BaseImpl): Base = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `generate component interface with binding in ActivityScope`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.ActivityScope
        import build.wallet.di.BitkeyInject
        
        interface Base
        
        @BitkeyInject(ActivityScope::class)
        class BaseImpl : Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = ActivityScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = ActivityScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase(implementation: BaseImpl): Base = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `use all supertypes as default bindings - interfaces and abstract classes are supported`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        interface Base1
        abstract class Base2()
        
        @BitkeyInject(AppScope::class)
        class BaseImpl : Base1, Base2()
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base1
        import build.wallet.test.Base2
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase1(implementation: BaseImpl): Base1 = implementation

          @Provides
          public fun provideBase2(implementation: BaseImpl): Base2 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind many explicit supertypes`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        interface Base1
        interface Base2
        interface Base3
        
        @BitkeyInject(AppScope::class, boundTypes = [Base1::class, Base2::class])
        class BaseImpl : Base1, Base2, Base3
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base1
        import build.wallet.test.Base2
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase1(implementation: BaseImpl): Base1 = implementation

          @Provides
          public fun provideBase2(implementation: BaseImpl): Base2 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind one explicit supertype`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        interface Base1
        interface Base2
        
        @BitkeyInject(AppScope::class, boundTypes = [Base2::class])
        class BaseImpl : Base1, Base2
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base2
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase2(implementation: BaseImpl): Base2 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind by the type itself`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        @BitkeyInject(AppScope::class, boundTypes = [Base::class])
        class Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = Base::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBase(): Base = Base()
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `ignore duplicate explicit bound types`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        
        interface Base1
        interface Base2
        
        @BitkeyInject(AppScope::class, boundTypes = [Base2::class, Base2::class])
        class BaseImpl : Base1, Base2
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base2
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          public fun provideBase2(implementation: BaseImpl): Base2 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind real implementation using Impl qualifier`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Impl
        
        interface Base
        
        @Impl
        @BitkeyInject(AppScope::class)
        class BaseImpl : Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          @Impl
          public fun provideBaseAsImpl(implementation: BaseImpl): Base = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind real implementation using Fake qualifier`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Fake
        
        interface Base
        
        @Fake
        @BitkeyInject(AppScope::class)
        class BaseImpl : Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          @Fake
          public fun provideBaseAsFake(implementation: BaseImpl): Base = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind all supertypes types when implementation is using Impl qualifier`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Impl
        
        interface Foo
        
        interface Base1
        interface Base2
        
        @Impl
        @BitkeyInject(AppScope::class)
        class BaseImpl(val foo: Foo) : Base1, Base2
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base1
        import build.wallet.test.Base2
        import build.wallet.test.BaseImpl
        import build.wallet.test.Foo
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(foo: Foo): BaseImpl = BaseImpl(foo)

          @Provides
          @Impl
          public fun provideBase1AsImpl(implementation: BaseImpl): Base1 = implementation

          @Provides
          @Impl
          public fun provideBase2AsImpl(implementation: BaseImpl): Base2 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `bind explicit type when implementation is using Impl qualifier`() {
    val source = """
        package build.wallet.test
        
        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Impl
        
        interface Base1
        interface Base2
        
        @Impl
        @BitkeyInject(AppScope::class, boundTypes = [Base1::class])
        class BaseImpl : Base1, Base2
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.Base1
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()

          @Provides
          @Impl
          public fun provideBase1AsImpl(implementation: BaseImpl): Base1 = implementation
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }

  @Test
  fun `implementation class cannot have multiple implementation qualifiers`() {
    val source = """
        package build.wallet.test

        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Fake
        import build.wallet.di.Impl
        
        interface Base
        
        @Fake
        @Impl
        @BitkeyInject(AppScope::class)
        class BaseImpl : Base
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode)
  }

  @Test
  fun `injected constructor does not inject parameters with default values`() {
    val source = """
        package build.wallet.test
        
        import build.wallet.di.AppScope
        import build.wallet.di.BitkeyInject
        import build.wallet.di.Impl
        
        @Impl
        @BitkeyInject(AppScope::class)
        class BaseImpl(val text: String = "")
    """.trimIndent()

    val compilation = compilation(source)
    val result = compilation.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val generatedFile = assertNotNull(compilation.getKspGeneratedFiles().single())
    assertEquals("BaseImplComponent.kt", generatedFile.name)

    assertEquals(
      """
        package build.wallet.di
        
        import build.wallet.test.BaseImpl
        import me.tatarka.inject.annotations.Provides
        import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
        import software.amazon.lastmile.kotlin.inject.anvil.`internal`.Origin

        @Origin(value = BaseImpl::class)
        @ContributesTo(scope = AppScope::class)
        public interface BaseImplComponent {
          @Provides
          @SingleIn(scope = AppScope::class)
          public fun provideBaseImpl(): BaseImpl = BaseImpl()
        }
      """.trimIndent(),
      generatedFile.readText().trim()
    )
  }
}
