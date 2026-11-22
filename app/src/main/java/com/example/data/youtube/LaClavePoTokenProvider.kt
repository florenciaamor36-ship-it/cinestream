package com.example.data.youtube

import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

/** Uses NewPipe's supported optional token-provider hook; no synthetic or reused tokens. */
object LaClavePoTokenProvider : PoTokenProvider {
    @Volatile private var cached: PoTokenResult? = null
    fun getCachedWebClientPoToken(): PoTokenResult? = cached
    override fun getWebClientPoToken(videoId: String?): PoTokenResult? = cached
    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = null
}
