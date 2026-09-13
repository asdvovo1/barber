package com.barber.rush

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {

	private lateinit var game: GameSurface

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		Data.init(this)
		Save.load(this)
		Sfx.init(this)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		game = GameSurface(this)
		setContentView(game)
		immersive()
	}

	private fun immersive() {
		@Suppress("DEPRECATION")
		window.decorView.systemUiVisibility = (
			View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				or View.SYSTEM_UI_FLAG_FULLSCREEN
				or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			)
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) immersive()
	}

	override fun onResume() {
		super.onResume()
		game.resumeLoop()
		Sfx.resumeMusic()
	}

	override fun onPause() {
		super.onPause()
		game.pauseLoop()
		Sfx.pauseMusic()
		Save.save()
	}

	override fun onDestroy() {
		super.onDestroy()
		Sfx.stopMusic()
	}

	@Suppress("DEPRECATION")
	override fun onBackPressed() {
		if (!game.onBack()) super.onBackPressed()
	}
}
