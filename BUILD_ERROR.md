# Build Error Log

Commit: 6cc56c8f3f9a74c109f8e0b8db3c9db6462ae09f

```
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:generateDebugBuildConfig
> Task :app:generateDebugResValues
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
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:processDebugManifestForPackage
> Task :app:mergeDebugJniLibFolders
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeDebugNativeLibs
> Task :app:processDebugResources
> Task :app:l8DexDesugarLibDebug
> Task :app:mergeLibDexDebug
> Task :app:validateSigningDebug
> Task :app:writeDebugAppMetadata
> Task :app:stripDebugDebugSymbols
> Task :app:writeDebugSigningConfigVersions
> Task :app:mergeExtDexDebug
> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/InnerTube.kt:107:38 Unresolved reference 'async'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/InnerTube.kt:107:46 Suspension functions can only be called within coroutine body.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/InnerTube.kt:108:37 Unresolved reference 'async'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/InnerTube.kt:108:45 Suspension functions can only be called within coroutine body.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/InnerTube.kt:110:9 Argument type mismatch: actual type is 'kotlin.Any', but 'com.filtertube.app.data.StreamData?' was expected.
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
BUILD FAILED in 46s
```
