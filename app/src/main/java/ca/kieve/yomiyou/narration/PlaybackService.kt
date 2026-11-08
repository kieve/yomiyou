package ca.kieve.yomiyou.narration

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.MainActivity
import ca.kieve.yomiyou.home.services

/**
 * Plays the narration in the background, screen off included, with Android's media notification, the lock screen and
 * the headset buttons; the app drives it through [Narration].
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val speech = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VoiceDataSource.Factory(services.narration)))
            .setAudioAttributes(speech, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            // Otherwise the position moves in steps of about 200 ms while audio plays, and the light lags the voice.
            .experimentalSetDynamicSchedulingEnabled(false)
            .build()
        val back = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .setDisplayName(getString(R.string.narration_back))
            .build()
        val forward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .setDisplayName(getString(R.string.narration_forward))
            .build()
        val open = Intent(this, MainActivity::class.java).setAction(MainActivity.OPEN_NARRATION)
        services.narration.attach(player)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setMediaButtonPreferences(listOf(back, forward))
            .setCallback(object : MediaSession.Callback {
                // Previous and next would skip a whole segment (about five minutes): 10 seconds back and forward take
                // their places.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .removeAll(
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        )
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailablePlayerCommands(commands)
                        .build()
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.player?.let(services.narration::detach)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SKIP_MS = 10_000L
    }
}
