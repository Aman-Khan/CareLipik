import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val sherpaOnnxRuntime by configurations.creating
val apolloOnnxRuntime by configurations.creating
val generatedSherpaOnnxRuntime = layout.buildDirectory.dir("generated/sherpaOnnxRuntime/jniLibs")
val generatedApolloOnnxRuntime = layout.buildDirectory.dir("generated/apolloOnnxRuntime/jniLibs")
val extractSherpaOnnxRuntime by tasks.registering(Copy::class) {
    from({ sherpaOnnxRuntime.files.map(::zipTree) }) {
        include("jni/**/libonnxruntime.so")
        eachFile { path = path.removePrefix("jni/") }
        includeEmptyDirs = false
    }
    into(generatedSherpaOnnxRuntime)
}
val extractApolloOnnxRuntime by tasks.registering {
    inputs.files(apolloOnnxRuntime)
    outputs.dir(generatedApolloOnnxRuntime)
    doLast {
        val outputRoot = generatedApolloOnnxRuntime.get().asFile
        outputRoot.deleteRecursively()
        val dependencyName = "libonnxruntime.so".encodeToByteArray()
        val isolatedName = "libapollo_ortx.so".encodeToByteArray()
        check(dependencyName.size == isolatedName.size)
        ZipFile(apolloOnnxRuntime.singleFile).use { archive ->
            archive.entries().asSequence()
                .filter { entry ->
                    entry.name.matches(
                        Regex("jni/[^/]+/libonnxruntime(4j_jni)?\\.so")
                    )
                }
                .forEach { entry ->
                    val parts = entry.name.split('/')
                    val abi = parts[1]
                    val originalName = parts[2]
                    val outputName = if (originalName == "libonnxruntime.so") {
                        "libapollo_ortx.so"
                    } else {
                        originalName
                    }
                    val bytes = archive.getInputStream(entry).use { it.readBytes() }
                    if (
                        originalName == "libonnxruntime.so" ||
                        originalName == "libonnxruntime4j_jni.so"
                    ) {
                        var replacements = 0
                        for (index in 0..bytes.size - dependencyName.size) {
                            if (dependencyName.indices.all { offset ->
                                    bytes[index + offset] == dependencyName[offset]
                                }
                            ) {
                                isolatedName.copyInto(bytes, index)
                                replacements += 1
                            }
                        }
                        check(replacements > 0) {
                            "Apollo native library did not reference libonnxruntime.so"
                        }
                    }
                    val output = outputRoot.resolve("$abi/$outputName")
                    output.parentFile.mkdirs()
                    output.writeBytes(bytes)
                }
        }
    }
}

val careLipikLocalProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val transcriptionBackendUrl = providers
    .gradleProperty("carelipik.transcriptionBackendUrl")
    .orElse(careLipikLocalProperties.getProperty("carelipik.transcriptionBackendUrl", ""))
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.carelipik.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.carelipik.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "TRANSCRIPTION_BACKEND_URL",
            "\"$transcriptionBackendUrl\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("onnx", "txt")
    }
    sourceSets.named("main") {
        jniLibs.srcDir(generatedSherpaOnnxRuntime.get().asFile)
        jniLibs.srcDir(generatedApolloOnnxRuntime.get().asFile)
    }
    packaging {
        jniLibs {
            // Prefer the generated Sherpa runtime: its newer Android build avoids Snapdragon
            // SIGILL crashes. Apollo Medical-NER keeps a guarded rules fallback if unavailable.
            pickFirsts += "lib/**/libonnxruntime.so"
            pickFirsts += "lib/**/libonnxruntime4j_jni.so"
        }
    }
}

dependencies {
    sherpaOnnxRuntime(
        "com.github.k2-fsa.sherpa-onnx:sherpa-onnx:${libs.versions.sherpaOnnx.get()}@aar"
    ) {
        isTransitive = false
    }
    apolloOnnxRuntime("com.microsoft.onnxruntime:onnxruntime-android:${libs.versions.onnxRuntime.get()}@aar") {
        isTransitive = false
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Keep Sherpa first: duplicate libonnxruntime.so resolution must retain Sherpa's matching
    // native runtime. Apollo uses the public ONNX Runtime Java API from the dependency below.
    implementation(libs.sherpa.onnx) {
        // The Android AAR already bundles these classes but also declares the JVM jar.
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }
    implementation(libs.onnxruntime.android)
    implementation(libs.litert.lm.android)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.named("preBuild") {
    dependsOn(extractSherpaOnnxRuntime, extractApolloOnnxRuntime)
}
