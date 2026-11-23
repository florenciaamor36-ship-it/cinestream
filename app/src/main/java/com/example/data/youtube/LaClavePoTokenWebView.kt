package com.example.data.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** BotGuard WebView implementation ported from La Clave; the webview is network-isolated. */
class LaClavePoTokenWebView private constructor(
    context: Context,
    private val initialized: Continuation<LaClavePoTokenWebView>
) {
    private val webView = WebView(context)
    private val continuations = mutableMapOf<String, Continuation<String>>()
    private lateinit var expiresAt: Instant
    private val exceptionHandler = CoroutineExceptionHandler { _, error -> fail(error) }

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            safeBrowsingEnabled = false
            userAgentString = USER_AGENT
            blockNetworkLoads = true
        }
        webView.addJavascriptInterface(this, JS_INTERFACE)
    }

    private fun load(context: Context) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            withContext(Dispatchers.Main) {
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"),
                    "text/html", "utf-8", null
                )
            }
        }
    }

    @JavascriptInterface fun downloadAndRunBotguard() {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val challenge = parseChallengeData(botguardRequest("https://www.youtube.com/api/jnn/v1/Create", listOf(REQUEST_KEY)))
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("""try { data = $challenge; runBotGuard(data).then(function (result) { this.webPoSignalOutput = result.webPoSignalOutput; $JS_INTERFACE.onRunBotguardResult(result.botguardResponse); }, function (error) { $JS_INTERFACE.onJsInitializationError(error + "\\n" + error.stack); }); } catch (error) { $JS_INTERFACE.onJsInitializationError(error + "\\n" + error.stack); }""", null)
            }
        }
    }

    @JavascriptInterface fun onRunBotguardResult(response: String) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val (integrityToken, lifetime) = parseIntegrityTokenData(botguardRequest("https://www.youtube.com/api/jnn/v1/GenerateIT", listOf(REQUEST_KEY, response)))
            expiresAt = Instant.now().plusSeconds((lifetime - 600).coerceAtLeast(60))
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                    initialized.resume(this@LaClavePoTokenWebView)
                }
            }
        }
    }

    @JavascriptInterface fun onJsInitializationError(error: String) = fail(IllegalStateException(error))

    suspend fun generatePoToken(identifier: String): String = suspendCancellableCoroutine { continuation ->
        continuations[identifier] = continuation
        val id = stringToU8(identifier)
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("""try { identifier = "$identifier"; u8Identifier = $id; poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier); poTokenU8String = Array.from(poTokenU8).join(","); $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String); } catch (error) { $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\\n" + error.stack); }""", null)
        }
    }

    @JavascriptInterface fun onObtainPoTokenError(identifier: String, error: String) {
        continuations.remove(identifier)?.resumeWithException(IllegalStateException(error))
    }

    @JavascriptInterface fun onObtainPoTokenResult(identifier: String, bytes: String) {
        runCatching { u8ToBase64(bytes) }
            .onSuccess { continuations.remove(identifier)?.resume(it) }
            .onFailure { continuations.remove(identifier)?.resumeWithException(it) }
    }

    fun isExpired() = !::expiresAt.isInitialized || Instant.now().isAfter(expiresAt)

    private fun fail(error: Throwable) {
        CoroutineScope(Dispatchers.Main).launch {
            close()
            initialized.resumeWithException(error)
        }
    }

    fun close() {
        webView.apply {
            clearHistory(); clearCache(true); loadUrl("about:blank"); onPause(); removeAllViews(); destroy()
        }
    }

    private suspend fun botguardRequest(url: String, values: List<String>): String = withContext(Dispatchers.IO) {
        val body = JSONArray(values).toString().toRequestBody("application/json+protobuf".toMediaType())
        val request = Request.Builder().url(url).post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", GOOGLE_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("BotGuard request failed: HTTP ${response.code}")
            response.body?.string() ?: error("BotGuard returned an empty response")
        }
    }

    companion object {
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val JS_INTERFACE = "LaClavePoTokenWebView"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        // Public client key used by La Clave's same BotGuard endpoint.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private val http = OkHttpClient()
        suspend fun create(context: Context): LaClavePoTokenWebView = suspendCancellableCoroutine { c ->
            Handler(Looper.getMainLooper()).post { LaClavePoTokenWebView(context, c).also { it.load(context) } }
        }
    }
}

private fun parseChallengeData(raw: String): String {
    val outer = JSONArray(raw)
    val challenge = if (outer.length() > 1 && outer.opt(1) is String) {
        JSONArray(String(decodeYouTubeBase64(outer.getString(1)).map { (it + 97).toByte() }.toByteArray(), Charsets.UTF_8))
    } else JSONArray(outer.get(0).toString())
    fun firstString(index: Int): Any = (challenge.opt(index) as? JSONArray)?.let { arr ->
        (0 until arr.length()).map { arr.opt(it) }.firstOrNull { it is String } ?: JSONObject.NULL
    } ?: JSONObject.NULL
    val interpreter = JSONObject()
        .put("privateDoNotAccessOrElseSafeScriptWrappedValue", firstString(1))
        .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", firstString(2))
    return JSONObject()
        .put("messageId", challenge.getString(0))
        .put("interpreterJavascript", interpreter)
        .put("interpreterHash", challenge.getString(3))
        .put("program", challenge.getString(4))
        .put("globalName", challenge.getString(5))
        .put("clientExperimentsStateBlob", challenge.getString(7)).toString()
}

private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
    val array = JSONArray(raw)
    return base64ToU8(array.getString(0)) to array.getLong(1)
}
private fun stringToU8(value: String) = "new Uint8Array([" + value.toByteArray().joinToString(",") { (it.toInt() and 255).toString() } + "])"
private fun base64ToU8(value: String) = "new Uint8Array([" + decodeYouTubeBase64(value).joinToString(",") { (it.toInt() and 255).toString() } + "])"
private fun decodeYouTubeBase64(value: String): ByteArray = android.util.Base64.decode(value.replace('-', '+').replace('_', '/').replace('.', '='), android.util.Base64.DEFAULT)
private fun u8ToBase64(value: String): String = android.util.Base64.encodeToString(value.split(',').map { it.trim().toInt().toByte() }.toByteArray(), android.util.Base64.NO_WRAP).replace('+', '-').replace('/', '_')
