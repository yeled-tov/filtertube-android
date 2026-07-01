# Build Error Log

Commit: 7e08d1bac92e0d0c48b6d72b152e97b8fea7cb74

```
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:generateDebugBuildConfig
> Task :app:generateDebugResValues
> Task :app:processDebugGoogleServices
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:processDebugResources
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeDebugNativeLibs
> Task :app:compileDebugKotlin
e: The daemon has terminated unexpectedly on startup attempt #1 with error code: 0. The daemon process output:
> Task :app:l8DexDesugarLibDebug
> Task :app:mergeExtDexDebug
> Task :app:compileDebugKotlin
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:154:72 @Composable invocations can only happen from the context of a @Composable function
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:154:80 Unresolved reference 'lifecycleScope'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:155:56 Suspend function 'suspend fun createCheckout(settings: SettingsStore, plan: String, method: String): ServerBilling.CheckoutResult' should be called only from a coroutine or another suspend function.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:177:72 @Composable invocations can only happen from the context of a @Composable function
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:177:80 Unresolved reference 'lifecycleScope'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/PremiumScreen.kt:178:56 Suspend function 'suspend fun verifyPurchase(settings: SettingsStore, plan: String, method: String): ServerBilling.VerifyResult' should be called only from a coroutine or another suspend function.
> Task :app:compileDebugKotlin FAILED
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
BUILD FAILED in 2m 36s
```
