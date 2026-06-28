# Build Error Log

Commit: 2433a91ede76599f446bf5bfcd79b94c754bb742

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
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:processDebugManifestForPackage
> Task :app:compressDebugAssets
> Task :app:processDebugResources
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugJniLibFolders
> Task :app:checkDebugDuplicateClasses FAILED
> Task :app:mergeDebugNativeLibs
> Task :app:l8DexDesugarLibDebug
> Task :app:compileDebugKotlin
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/SettingsScreen.kt:223:23 Unresolved reference 'launch'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/SettingsScreen.kt:224:40 Suspend function 'suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Boolean' should be called only from a coroutine or another suspend function.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/SettingsScreen.kt:236:31 Unresolved reference 'launch'.
e: file:///home/runner/work/filtertube-android/filtertube-android/app/src/main/java/com/filtertube/app/ui/SettingsScreen.kt:236:50 Suspend function 'suspend fun signOut(settings: SettingsStore): Unit' should be called only from a coroutine or another suspend function.
> Task :app:compileDebugKotlin FAILED
* What went wrong:
Execution failed for task ':app:checkDebugDuplicateClasses'.
> A failure occurred while executing com.android.build.gradle.internal.tasks.CheckDuplicatesRunnable
   > Duplicate class com.google.protobuf.DescriptorProtos found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$1 found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ExtensionRange found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ExtensionRange$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ExtensionRangeOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ReservedRange found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ReservedRange$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProto$ReservedRangeOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$DescriptorProtoOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProto found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProto$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProto$EnumReservedRange found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProto$EnumReservedRange$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProto$EnumReservedRangeOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumDescriptorProtoOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumOptions found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumOptions$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumOptionsOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueDescriptorProto found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueDescriptorProto$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueDescriptorProtoOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueOptions found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueOptions$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$EnumValueOptionsOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$ExtensionRangeOptions found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$ExtensionRangeOptions$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$ExtensionRangeOptionsOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Label found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Label$1 found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Label$LabelVerifier found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Type found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Type$1 found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProto$Type$TypeVerifier found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldDescriptorProtoOrBuilder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldOptions found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldOptions$Builder found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
     Duplicate class com.google.protobuf.DescriptorProtos$FieldOptions$CType found in modules protobuf-javalite-4.35.0.jar -> protobuf-javalite-4.35.0 (com.google.protobuf:protobuf-javalite:4.35.0) and protolite-well-known-types-18.0.0.aar -> protolite-well-known-types-18.0.0-runtime (com.google.firebase:protolite-well-known-types:18.0.0)
```
