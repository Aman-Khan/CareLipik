package com.carelipik.app.data.export

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import com.carelipik.app.domain.prescription.Prescription
import com.carelipik.app.domain.prescription.PrescriptionExporter
import com.carelipik.app.domain.prescription.PrescriptionFiles
import com.carelipik.app.domain.prescription.PrescriptionLatexTemplate
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Actual XeLaTeX WASM compilation in a worker, with all resources served from the APK. */
class AndroidLatexPrescriptionExporter(context: Context) : PrescriptionExporter {
    private val context = context.applicationContext

    override suspend fun generate(prescription: Prescription, onProgress: (String) -> Unit): PrescriptionFiles {
        val source = PrescriptionLatexTemplate.render(prescription)
        val pdf = compile(source, onProgress)
        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "prescription_exports").apply { mkdirs() }
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            directory.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
            val name = "carelipik-prescription-${UUID.randomUUID()}"
            val pdfFile = File(directory, "$name.pdf")
            val texFile = File(directory, "$name.tex")
            pdfFile.writeBytes(pdf)
            texFile.writeText(source)
            PrescriptionFiles(pdfFile.absolutePath, texFile.absolutePath, name)
        }
    }

    override suspend fun save(localPath: String, destination: String) = withContext(Dispatchers.IO) {
        val file = File(localPath).canonicalFile
        val root = File(context.cacheDir, "prescription_exports").canonicalFile
        require(file.parentFile == root && file.isFile) { "Prescription file unavailable" }
        val uri = Uri.parse(destination)
        require(uri.scheme == "content") { "Select a document destination" }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destination unavailable")
        output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        Unit
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun compile(source: String, onProgress: (String) -> Unit): ByteArray {
        val result = CompletableDeferred<ByteArray>()
        var webView: WebView? = null
        try {
            return withTimeout(180_000) {
                withContext(Dispatchers.Main) {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                                val uri = request.url
                                val path = uri.path.orEmpty().removePrefix("/")
                                if (uri.scheme != "https" || uri.host != "carelipik.local" ||
                                    !path.startsWith("latex/") || path.contains("..")) return missing()
                                return try {
                                    val mime = when {
                                        path.endsWith(".html") -> "text/html"
                                        path.endsWith(".js") -> "application/javascript"
                                        path.endsWith(".wasm") -> "application/wasm"
                                        else -> "application/octet-stream"
                                    }
                                    WebResourceResponse(mime, "utf-8", context.assets.open(path))
                                } catch (_: Exception) { missing() }
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean = true
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                result.completeExceptionally(IllegalStateException("LaTeX worker stopped"))
                                return true
                            }
                        }
                        addJavascriptInterface(CompilerBridge(source, result, onProgress), "PrescriptionBridge")
                        loadUrl("https://carelipik.local/latex/compiler.html")
                    }
                }
                result.await()
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                webView?.apply {
                    evaluateJavascript("window.cancelCompilation?.()", null)
                    stopLoading()
                    removeJavascriptInterface("PrescriptionBridge")
                    destroy()
                }
            }
        }
    }

    private class CompilerBridge(
        private val source: String,
        private val result: CompletableDeferred<ByteArray>,
        private val onProgress: (String) -> Unit
    ) {
        @JavascriptInterface fun source(): String = source
        @JavascriptInterface fun stage(value: String) {
            val known = setOf("Loading offline LaTeX compiler and packages", "Typesetting prescription with XeLaTeX", "Finishing prescription PDF")
            val packageProgress = Regex("^Preparing offline LaTeX packages: (\\d{1,3})%$")
                .matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()?.let { it in 0..100 } == true
            if ((value in known || packageProgress) && !result.isCompleted) {
                Log.i("CareLipikPrescription", value)
                onProgress(value)
            }
        }
        @JavascriptInterface fun complete(base64: String) {
            if (result.isCompleted) return
            try {
                require(base64.length <= 12 * 1024 * 1024)
                val pdf = Base64.decode(base64, Base64.DEFAULT)
                require(pdf.size >= 5 && pdf.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-")
                result.complete(pdf)
            } catch (_: Exception) { failed() }
        }
        @JavascriptInterface fun failed() {
            Log.e("CareLipikPrescription", "Offline LaTeX compilation failed")
            result.completeExceptionally(IllegalStateException("Offline LaTeX compilation failed"))
        }
    }

    private fun missing() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
}
