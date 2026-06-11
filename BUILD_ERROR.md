# Build Error Log

Commit: 1701d0b4a884e659506db94d45d82c0df79f7931

```
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
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
> Task :app:mergeLibDexDebug
> Task :app:stripDebugDebugSymbols
> Task :app:processDebugResources
> Task :app:validateSigningDebug
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions
> Task :app:compileDebugKotlin
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/FilterTubeApp.kt:8:7 Conflicting overloads:
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/MainActivity.kt:33:21 Overload resolution ambiguity between candidates:
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/MainActivity.kt:40:1 Conflicting overloads:
> Task :app:compileDebugKotlin FAILED
> Task :app:mergeExtDexDebug
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
BUILD FAILED in 31s
```
