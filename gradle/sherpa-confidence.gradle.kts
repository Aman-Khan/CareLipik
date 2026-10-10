import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.util.Properties
import javax.inject.Inject

@DisableCachingByDefault(because = "Compiles a pinned native runtime with a local Android NDK toolchain")
abstract class BuildSherpaConfidenceRuntime @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputFile abstract val originalAar: RegularFileProperty
    @get:InputFile abstract val builder: RegularFileProperty
    @get:InputFiles abstract val buildSources: ConfigurableFileCollection
    @get:Input abstract val sdkPath: Property<String>
    @get:Input abstract val pythonCommand: Property<String>
    @get:Input abstract val offline: Property<Boolean>
    @get:LocalState abstract val nativeCache: DirectoryProperty
    @get:OutputFile abstract val runtimeAar: RegularFileProperty

    @TaskAction
    fun buildRuntime() {
        execOperations.exec {
            val arguments = mutableListOf(pythonCommand.get(), builder.get().asFile.absolutePath,
                "--original-aar", originalAar.get().asFile.absolutePath,
                "--sdk", sdkPath.get(), "--output", runtimeAar.get().asFile.absolutePath,
                "--cache", nativeCache.get().asFile.absolutePath)
            if (offline.get()) arguments += "--offline"
            commandLine(arguments)
        }
    }
}

// Resolve only the original Android AAR. Its Kotlin API and ONNX binaries are preserved.
val originalSherpaAar = configurations.create("originalSherpaAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies.add(originalSherpaAar.name, "com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.6@aar")

val nativeSdkProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val buildSherpaConfidenceRuntime = tasks.register<BuildSherpaConfidenceRuntime>("buildSherpaConfidenceRuntime") {
    group = "build"
    description = "Builds Sherpa 1.13.6 with actual Whisper token probabilities for automatic hybrid review"
    originalAar.set(layout.file(originalSherpaAar.elements.map { it.single().asFile }))
    builder.set(rootProject.layout.projectDirectory.file("tools/native_sherpa/build_runtime.py"))
    buildSources.from(rootProject.fileTree("tools/native_sherpa") { include("*.py", "*.patch", "*.json") })
    val configuredSdk = nativeSdkProperties.getProperty("sdk.dir")
    if (!configuredSdk.isNullOrBlank()) sdkPath.set(configuredSdk)
    else sdkPath.set(providers.environmentVariable("ANDROID_SDK_ROOT")
        .orElse(providers.environmentVariable("ANDROID_HOME")).orElse(""))
    pythonCommand.set(providers.gradleProperty("carelipik.pythonCommand").orElse("python"))
    offline.set(gradle.startParameter.isOffline)
    nativeCache.set(rootProject.layout.projectDirectory.dir("tmp/sherpa-confidence"))
    runtimeAar.set(rootProject.layout.buildDirectory.file("native-sherpa/sherpa-onnx-confidence-1.13.6.aar"))
}

dependencies.add("implementation", files(buildSherpaConfidenceRuntime.flatMap { it.runtimeAar }))
