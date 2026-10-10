import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/offline-models.gradle.kts"))
apply(from = rootProject.file("gradle/sherpa-confidence.gradle.kts"))
apply(from = rootProject.file("gradle/llama-runtime.gradle.kts"))
apply(from = rootProject.file("gradle/whisper-runtime.gradle.kts"))
apply(from = rootProject.file("gradle/latex-runtime.gradle.kts"))

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
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    namespace = "com.carelipik.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
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
        noCompress += listOf("onnx", "txt", "gguf", "bin")
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/whisper-jni").get().asFile)
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/latex-assets").get().asFile)
    packaging {
        jniLibs.excludes += "**/libOpenCL.so"
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
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
    // Sherpa's unchanged Kotlin API and ONNX binaries are packaged with our patched 1.13.6 JNI runtime.
    // The build task in sherpa-confidence.gradle.kts generates this AAR; no native/model binaries are committed.
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
