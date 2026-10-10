import java.net.URI
import java.security.MessageDigest
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Prepares a checksum-pinned local native source archive")
abstract class PrepareLlamaSource @Inject constructor(
    private val archives: ArchiveOperations,
    private val fileSystem: FileSystemOperations
) : DefaultTask() {
    @get:OutputDirectory abstract val sourceDirectory: DirectoryProperty
    @get:Input abstract val offline: Property<Boolean>
    @get:Input abstract val archiveName: Property<String>
    @get:Input abstract val archiveUrl: Property<String>
    @get:Input abstract val archiveSha256: Property<String>

    @TaskAction fun prepare() {
        val cache = sourceDirectory.get().asFile.parentFile
        val archive = java.io.File(cache, archiveName.get())
        archive.parentFile.mkdirs()
        fun digest(): String = MessageDigest.getInstance("SHA-256").run {
            archive.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    update(buffer, 0, count)
                }
            }
            this.digest().joinToString("") { "%02x".format(it) }
        }
        val expected = archiveSha256.get()
        if (!archive.isFile || digest() != expected) {
            check(!offline.get()) { "llama.cpp source is missing. Run :app:prepareLlamaSource once without --offline." }
            val pending = java.io.File(cache, archiveName.get() + ".pending")
            try {
                URI(archiveUrl.get()).toURL()
                    .openConnection().apply { connectTimeout = 30_000; readTimeout = 120_000 }
                    .getInputStream().use { input -> pending.outputStream().use { input.copyTo(it) } }
                java.nio.file.Files.move(pending.toPath(), archive.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                check(digest() == expected) { "llama.cpp source checksum mismatch." }
            } finally { pending.delete() }
        }
        fileSystem.copy { from(archives.zipTree(archive)); into(cache) }
    }
}
val prepareLlamaSource = tasks.register<PrepareLlamaSource>("prepareLlamaSource") {
    group = "build setup"
    description = "Prepares checksum-pinned llama.cpp b11489 with Adreno Q4_K kernels"
    sourceDirectory.set(rootProject.layout.projectDirectory.dir("tmp/l/llama.cpp-b11489"))
    offline.set(gradle.startParameter.isOffline)
    archiveName.set("b11489.zip")
    archiveUrl.set("https://codeload.github.com/ggml-org/llama.cpp/zip/refs/tags/b11489")
    archiveSha256.set("282f9c9ea5c7cdf60b94e9035435ed7d4518fd62474c7fc21ba604cf08ec4778")
}
val prepareOpenClHeaders = tasks.register<PrepareLlamaSource>("prepareOpenClHeaders") {
    group = "build setup"
    sourceDirectory.set(rootProject.layout.projectDirectory.dir("tmp/l/OpenCL-Headers-2025.07.22"))
    offline.set(gradle.startParameter.isOffline)
    archiveName.set("OpenCL-Headers-v2025.07.22.zip")
    archiveUrl.set("https://codeload.github.com/KhronosGroup/OpenCL-Headers/zip/refs/tags/v2025.07.22")
    archiveSha256.set("ef9ba8d4231110bb369becb20cb98259d94a448fc8232ede96551c26d110840f")
}
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name == "preBuild") {
        dependsOn(prepareLlamaSource)
        dependsOn(prepareOpenClHeaders)
    }
}
