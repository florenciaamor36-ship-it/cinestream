package com.example.data.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** La Clave's Web PoToken provider, adapted to CineStream's application context. */
object LaClavePoTokenProvider : PoTokenProvider {
    @Volatile private var appContext: Context? = null
    private val lock = Any()
    private var visitorData: String? = null
    private var generator: LaClavePoTokenWebView? = null
    @Volatile private var cachedResult: PoTokenResult? = null

    fun initialize(context: Context) { appContext = context.applicationContext }
    fun getCachedWebClientPoToken(): PoTokenResult? = cachedResult

    override fun getWebClientPoToken(videoId: String?): PoTokenResult? {
        if (videoId.isNullOrBlank() || appContext == null || runCatching { CookieManager.getInstance() }.isFailure) return null
        return synchronized(lock) {
            val context = appContext ?: return null
            if (generator == null || generator?.isExpired() == true) {
                val requestInfo = InnertubeClientRequestInfo.ofWebClient().apply {
                    clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                }
                visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                    requestInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false
                )
                generator?.let { old -> Handler(Looper.getMainLooper()).post { old.close() } }
                generator = runBlocking { LaClavePoTokenWebView.create(context) }
            }
            val webView = generator ?: return null
            val visitor = visitorData ?: return null
            val token = runBlocking { webView.generatePoToken(videoId) }
            PoTokenResult(visitor, token, token).also { cachedResult = it }
        }
    }

    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = null
}
