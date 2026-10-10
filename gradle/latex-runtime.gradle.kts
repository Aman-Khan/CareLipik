import org.gradle.api.tasks.Exec

val latexOutput = layout.buildDirectory.dir("generated/latex-assets")
val latexOffline = gradle.startParameter.isOffline
val prepareLatexRuntime = tasks.register<Exec>("prepareLatexRuntime") {
    val tooling = rootProject.file("tools/latex_runtime")
    inputs.dir(tooling)
    outputs.dir(latexOutput)
    commandLine(buildList {
        add("python")
        add(tooling.resolve("prepare_runtime.py").absolutePath)
        addAll(listOf("--cache", rootProject.file("tmp/latex-cache").absolutePath,
            "--output", latexOutput.get().asFile.absolutePath))
        if (latexOffline) add("--offline")
    })
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareLatexRuntime) }
