import android.os.Bundle
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

class MyMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private val TAG = "MyMediaBrowserService"

    override fun onCreate() {
        super.onCreate()

        // Initialize media session
        mediaSession = MediaSessionCompat(this, "OBD2MediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    Log.d(TAG, "Play action received in MediaBrowserService")
                    // Activate the media session
                    isActive = true
                }

                override fun onPause() {
                    super.onPause()
                    Log.d(TAG, "Pause action received in MediaBrowserService")
                    // Deactivate the media session
                    isActive = false
                }
            })
        }

        sessionToken = mediaSession.sessionToken

        // Set initial playback state
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(ArrayList())
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}
