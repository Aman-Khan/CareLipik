import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Downloads verified model assets into the local source assets directory")
abstract class PrepareOfflineModels : DefaultTask() {
    @get:Input
    abstract val modelFiles: ListProperty<String>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun prepare() {
        for (entry in modelFiles.get()) {
            val (path, url, expectedHash) = entry.split('|')
            val target = destination.file(path).get().asFile
            if (target.isFile && sha256(target) == expectedHash) {
                logger.lifecycle("Offline model ready: $path")
                continue
            }
            if (offline.get()) {
                throw GradleException(
                    "Offline model missing or invalid: $path. " +
                        "Run :app:prepareOfflineModels without --offline while connected to the internet."
                )
            }
            target.parentFile.mkdirs()
            var lastFailure: Exception? = null
            for (attempt in 1..3) {
                val pending = Files.createTempFile(target.parentFile.toPath(), target.name, ".pending")
                try {
                    logger.lifecycle("Downloading offline model: $path (attempt $attempt/3)")
                    val connection = URI(url).toURL().openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 120_000
                    }
                    connection.getInputStream().use { input ->
                        Files.newOutputStream(pending).use { output -> input.copyTo(output) }
                    }
                    check(sha256(pending.toFile()) == expectedHash) {
                        "SHA-256 verification failed for $path"
                    }
                    Files.move(pending, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    lastFailure = null
                    break
                } catch (failure: Exception) {
                    lastFailure = failure
                    logger.warn("Could not prepare $path: ${failure.message}")
                } finally {
                    Files.deleteIfExists(pending)
                }
            }
            if (lastFailure != null) {
                throw GradleException(
                    "Could not prepare offline model $path. Build stopped to avoid an APK " +
                        "with missing models. Check internet access and retry :app:prepareOfflineModels.",
                    lastFailure
                )
            }
        }
    }

    private fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val prepareOfflineModels = tasks.register<PrepareOfflineModels>("prepareOfflineModels") {
    group = "build setup"
    description = "Downloads and verifies all offline speech and speaker models before APK packaging."
    destination.set(layout.projectDirectory.dir("src/main/assets/models"))
    offline.set(gradle.startParameter.isOffline)
    // Validate manually installed assets too, and detect corruption before every packaging run.
    outputs.upToDateWhen { false }
    val medAsr = "https://huggingface.co/csukuangfj/sherpa-onnx-medasr-ctc-en-int8-2025-12-25/resolve/main"
    val whisper = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/8f3c18b358db4d1f2fc1eae49d75cd20989e4309"
    val segmentation = "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/9403a6902bb58e3d5ae8c7e77c3422de279db2e0/model.onnx"
    modelFiles.set(listOf(
        "sherpa-onnx-medasr-ctc-en-int8/model.int8.onnx|$medAsr/model.int8.onnx|2c20f03265ee6144c566fd18b0f7bbb4f0d005d11ce9440dd641920210f4c33a",
        "sherpa-onnx-medasr-ctc-en-int8/tokens.txt|$medAsr/tokens.txt|b43987c0f8f660068a166d155f02b1e439d1f03dda36d50759b4e282e98814f2",
        "sherpa-onnx-whisper-small/small-encoder.int8.onnx|$whisper/small-encoder.int8.onnx|4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
        "sherpa-onnx-whisper-small/small-decoder.int8.onnx|$whisper/small-decoder.int8.onnx|acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
        "sherpa-onnx-whisper-small/small-tokens.txt|$whisper/small-tokens.txt|b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
        "sherpa-onnx-speaker-diarization/segmentation-model.onnx|$segmentation|220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079",
        "sherpa-onnx-speaker-diarization/nemo_en_titanet_small.onnx|https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/nemo_en_titanet_small.onnx|ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e"
    ))
}

// assemble, bundle, install, and Android Studio Run all merge assets before packaging.
// JVM tests and Gradle sync do not need to download hundreds of MB of native model data.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareOfflineModels)
    }
    if (name.contains("lint", ignoreCase = true)) {
        // Lint reads the assets directory too. Order it after preparation when both run,
        // without making lint-only invocations download models.
        mustRunAfter(prepareOfflineModels)
    }
}
