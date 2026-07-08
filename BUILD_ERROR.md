# Build Error Log

Commit: f259793bedadf4f93cb12ddfa45accb383ffce18

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
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:mergeDebugResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:processDebugManifestForPackage
> Task :app:checkDebugDuplicateClasses
> Task :app:processDebugResources
> Task :app:l8DexDesugarLibDebug
> Task :app:mergeLibDexDebug
> Task :app:validateSigningDebug
> Task :app:mergeDebugNativeLibs
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions
> Task :app:stripDebugDebugSymbols
> Task :app:mergeExtDexDebug
> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/data/StreamRepository.kt:163:1 Syntax error: Expecting a top level declaration.
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
BUILD FAILED in 47s
```
