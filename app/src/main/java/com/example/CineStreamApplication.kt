package com.example

import android.app.Application
import com.example.data.youtube.YouTubeExtractorDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import com.example.data.youtube.LaClavePoTokenProvider

/** Initializes the open-source NewPipe extraction engine once for CineStream. */
class CineStreamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(YouTubeExtractorDownloader())
        LaClavePoTokenProvider.initialize(this)
        YoutubeStreamExtractor.setPoTokenProvider(LaClavePoTokenProvider)
    }
}
