package com.example.iptv

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var exo: ExoPlayer? = null
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val url = intent.getStringExtra("url") ?: return finish()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val http = DefaultHttpDataSource.Factory().setUserAgent("IPTVPlayer/1.0").setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
        exo = p
        setContentView(PlayerView(this).also { it.player = p; it.setBackgroundColor(Color.BLACK) })
        p.setMediaItem(MediaItem.fromUri(url)); p.prepare(); p.playWhenReady = true
    }
    override fun onStop() { super.onStop(); exo?.pause() }
    override fun onDestroy() { exo?.release(); super.onDestroy() }
}
