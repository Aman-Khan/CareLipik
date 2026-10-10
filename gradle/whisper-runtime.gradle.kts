import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.util.Properties
import javax.inject.Inject

@DisableCachingByDefault(because = "Builds a pinned Vulkan runtime using local Android and host toolchains")
abstract class BuildWhisperVulkanRuntime @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFile abstract val builder: RegularFileProperty
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:Input abstract val sdkPath: Property<String>
    @get:Input abstract val pythonCommand: Property<String>
    @get:Input abstract val offline: Property<Boolean>
    @get:LocalState abstract val nativeCache: DirectoryProperty
    @get:OutputDirectory abstract val libraries: DirectoryProperty
    @TaskAction fun buildRuntime() {
        execOperations.exec {
            val arguments = mutableListOf(pythonCommand.get(), builder.get().asFile.absolutePath,
                "--sdk", sdkPath.get(), "--cache", nativeCache.get().asFile.absolutePath,
                "--output", libraries.get().asFile.absolutePath)
            if (offline.get()) arguments += "--offline"
            commandLine(arguments)
        }
    }
}
val whisperSdkProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val prepareWhisperVulkanRuntime = tasks.register<BuildWhisperVulkanRuntime>("prepareWhisperVulkanRuntime") {
    group = "build setup"
    description = "Builds whisper.cpp 1.9.5 Vulkan for the experimental offline hybrid mode"
    builder.set(rootProject.layout.projectDirectory.file("tools/native_whisper/build_runtime.py"))
    sources.from(rootProject.fileTree("tools/native_whisper") { include("*.py", "*.cpp", "CMakeLists.txt") })
    sdkPath.set(whisperSdkProperties.getProperty("sdk.dir") ?: providers.environmentVariable("ANDROID_SDK_ROOT")
        .orElse(providers.environmentVariable("ANDROID_HOME")).orElse("").get())
    pythonCommand.set(providers.gradleProperty("carelipik.pythonCommand").orElse("python"))
    offline.set(gradle.startParameter.isOffline)
    nativeCache.set(rootProject.layout.projectDirectory.dir("tmp/w"))
    libraries.set(layout.buildDirectory.dir("generated/whisper-jni"))
}
tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("JniLibFolders"))) {
        dependsOn(prepareWhisperVulkanRuntime)
    }
}
