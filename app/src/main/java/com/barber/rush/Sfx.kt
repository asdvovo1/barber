package com.barber.rush

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/* SoundPool for the 16 one-shots, MediaPlayer for the looping ambience. */
object Sfx {

	private var pool: SoundPool? = null
	private val ids = HashMap<String, Int>()
	private var music: MediaPlayer? = null
	private var musicName = ""

	private val oneShots = listOf(
		"arrive", "brush", "click", "clipper", "coin", "error", "mirror",
		"razor", "snip1", "snip2", "snip3", "spray", "steam", "success",
		"towel", "trimmer"
	)

	fun init(ctx: Context) {
		if (pool != null) return
		val attrs = AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_GAME)
			.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
			.build()
		val p = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
		pool = p
		for (n in oneShots) {
			try {
				ctx.assets.openFd("assets/$n.mp3").use { fd ->
					ids[n] = p.load(fd, 1)
				}
			} catch (e: Exception) {
				// asset missing: stay silent instead of crashing
			}
		}
	}

	fun play(name: String, volume: Float) {
		if (Save.sound == 0) return
		val p = pool ?: return
		val id = ids[name] ?: return
		val v = volume.coerceIn(0f, 1f)
		p.play(id, v, v, 1, 0, 1f)
	}

	fun ambience(ctx: Context, name: String) {
		if (Save.music == 0) {
			stopMusic()
			return
		}
		if (musicName == name && music != null) return
		stopMusic()
		try {
			val mp = MediaPlayer()
			ctx.assets.openFd("assets/$name.mp3").use { fd ->
				mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
			}
			mp.isLooping = true
			mp.setVolume(.34f, .34f)
			mp.prepare()
			mp.start()
			music = mp
			musicName = name
		} catch (e: Exception) {
			music = null
			musicName = ""
		}
	}

	fun pauseMusic() {
		try {
			music?.pause()
		} catch (e: Exception) {
		}
	}

	fun resumeMusic() {
		if (Save.music == 0) return
		try {
			music?.start()
		} catch (e: Exception) {
		}
	}

	fun stopMusic() {
		try {
			music?.stop()
			music?.release()
		} catch (e: Exception) {
		}
		music = null
		musicName = ""
	}
}
