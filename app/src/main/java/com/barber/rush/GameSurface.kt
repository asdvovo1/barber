package com.barber.rush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * One View renders every screen in the original 360x640 space and scales it
 * to the device: menu, level map, shop and the cutting gameplay.
 */
class GameSurface(context: Context) : View(context) {

	private val eng = Engine()

	private var screen = "menu"
	private var shopTab = 0

	private var vScale = 1f
	private var offX = 0f
	private var offY = 0f

	private var scrollMap = 0f
	private var scrollShop = 0f
	private var shopRows = 1

	private var toast = ""
	private var toastT = 0f

	private var running = false
	private var lastFrame = 0L

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
	private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
	private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
	private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		textAlign = Paint.Align.CENTER
	}

	private class Btn(val id: String, val r: RectF, val arg: Int = 0, val key: String = "")

	private val btns = ArrayList<Btn>()

	private var downX = 0f
	private var downY = 0f
	private var dragging = false
	private var scrollStart = 0f

	private var cutting = false
	private var lastPX = 0f
	private var lastPY = 0f
	private var toolAng = 0f
	private var toolTarget = 0f
	private var toolX = 180f
	private var toolY = 430f
	private var snipT = 0f

	companion object {
		private const val TILEH = 360f * 853f / 480f
		private val PADS = arrayOf(
			floatArrayOf(0.4362f, 0.3056f, 40f),
			floatArrayOf(0.6007f, 0.3370f, 42f),
			floatArrayOf(0.6802f, 0.3911f, 42f),
			floatArrayOf(0.5949f, 0.4525f, 45f),
			floatArrayOf(0.4378f, 0.4998f, 45f),
			floatArrayOf(0.3234f, 0.5656f, 48f),
			floatArrayOf(0.3435f, 0.6434f, 52f),
			floatArrayOf(0.5248f, 0.7026f, 55f),
			floatArrayOf(0.6487f, 0.7975f, 63f),
			floatArrayOf(0.5082f, 0.9142f, 70f)
		)
		private const val STAR = "\u2605"
		private const val STARO = "\u2606"
	}

	private val frame = object : Choreographer.FrameCallback {
		override fun doFrame(t: Long) {
			if (!running) return
			var dt = if (lastFrame == 0L) 1f / 60f else ((t - lastFrame) / 1_000_000_000.0).toFloat()
			lastFrame = t
			if (dt > .05f) dt = .05f
			step(dt)
			invalidate()
			Choreographer.getInstance().postFrameCallback(this)
		}
	}

	init {
		isClickable = true
		resumeLoop()
		Sfx.ambience(context, "breeze")
	}

	fun resumeLoop() {
		if (running) return
		running = true
		lastFrame = 0L
		Choreographer.getInstance().postFrameCallback(frame)
	}

	fun pauseLoop() {
		running = false
		Choreographer.getInstance().removeFrameCallback(frame)
	}

	fun onBack(): Boolean {
		return when (screen) {
			"game" -> {
				screen = "map"
				cutting = false
				true
			}
			"map", "shop" -> {
				screen = "menu"
				true
			}
			else -> false
		}
	}

	private fun step(dt: Float) {
		if (toastT > 0f) toastT = max(0f, toastT - dt)
		if (screen == "game") {
			eng.cutting = if (cutting) 1 else 0
			eng.update(dt)
			snipT += dt
			toolAng += (toolTarget - toolAng) * (1f - exp(-19f * dt))
		}
	}

	override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
		super.onSizeChanged(w, h, ow, oh)
		vScale = min(w / 360f, h / 640f)
		offX = (w - 360f * vScale) / 2f
		offY = (h - 640f * vScale) / 2f
	}

	/* ---------------- drawing helpers ---------------- */

	private fun img(cv: Canvas, key: String, x: Float, y: Float, w: Float, h: Float) {
		val b = Data.bmp(key) ?: return
		cv.drawBitmap(b, null, RectF(x, y, x + w, y + h), paint)
	}

	private fun imgW(cv: Canvas, key: String, cx: Float, y: Float, w: Float): Float {
		val b = Data.bmp(key) ?: return 0f
		val h = w * b.height / b.width
		cv.drawBitmap(b, null, RectF(cx - w / 2f, y, cx + w / 2f, y + h), paint)
		return h
	}

	private fun cover(cv: Canvas, key: String) {
		val b = Data.bmp(key)
		if (b == null) {
			cv.drawColor(Color.rgb(32, 26, 22))
			return
		}
		val s = max(360f / b.width, 640f / b.height)
		val w = b.width * s
		val h = b.height * s
		val x = (360f - w) / 2f
		val y = (640f - h) / 2f
		cv.drawBitmap(b, null, RectF(x, y, x + w, y + h), paint)
	}

	private fun label(
		cv: Canvas,
		s: String,
		x: Float,
		y: Float,
		size: Float,
		color: Int,
		align: Paint.Align = Paint.Align.CENTER,
		shadow: Boolean = true
	) {
		text.textSize = size
		text.textAlign = align
		if (shadow) {
			text.color = Color.argb(120, 0, 0, 0)
			cv.drawText(s, x + 1f, y + 1.6f, text)
		}
		text.color = color
		cv.drawText(s, x, y, text)
	}

	private fun panel(cv: Canvas, r: RectF, rad: Float, color: Int, border: Int = 0) {
		fill.color = color
		cv.drawRoundRect(r, rad, rad, fill)
		if (border != 0) {
			stroke.color = border
			stroke.strokeWidth = 2f
			cv.drawRoundRect(r, rad, rad, stroke)
		}
	}

	private fun button(
		cv: Canvas,
		id: String,
		r: RectF,
		txt: String,
		bg: Int,
		fg: Int = Color.WHITE,
		size: Float = 16f,
		arg: Int = 0,
		key: String = ""
	) {
		panel(cv, r, r.height() / 2.6f, bg, Color.argb(70, 0, 0, 0))
		label(cv, txt, r.centerX(), r.centerY() + size * .36f, size, fg)
		btns.add(Btn(id, RectF(r), arg, key))
	}

	private fun coinPill(cv: Canvas, x: Float, y: Float) {
		val r = RectF(x, y, x + 96f, y + 30f)
		panel(cv, r, 15f, Color.argb(150, 20, 16, 14), Color.argb(90, 255, 214, 100))
		img(cv, "coin", x + 5f, y + 4f, 22f, 22f)
		label(cv, Save.coins.toString(), x + 62f, y + 21f, 15f, Color.rgb(255, 215, 100))
	}

	private fun starsText(n: Int): String {
		val sb = StringBuilder()
		for (i in 0 until 3) sb.append(if (i < n) STAR else STARO)
		return sb.toString()
	}

	private fun toast(msg: String) {
		toast = msg
		toastT = 2.1f
	}

	private fun drawToast(cv: Canvas) {
		val a = min(1f, toastT / .35f)
		text.textSize = 15f
		val w = min(330f, text.measureText(toast) + 30f)
		val r = RectF(180f - w / 2f, 92f, 180f + w / 2f, 128f)
		fill.color = Color.argb((205 * a).toInt(), 24, 18, 16)
		cv.drawRoundRect(r, 12f, 12f, fill)
		label(cv, toast, 180f, 115f, 15f, Color.argb((255 * a).toInt(), 255, 236, 200))
	}

	/* ---------------- menu ---------------- */

	private fun drawMenu(cv: Canvas) {
		cover(cv, "bg_menu")
		fill.color = Color.argb(90, 0, 0, 0)
		cv.drawRect(0f, 0f, 360f, 640f, fill)
		imgW(cv, "logo", 180f, 74f, 250f)
		coinPill(cv, 12f, 14f)

		button(cv, "play", RectF(70f, 330f, 290f, 384f), Data.t("play"), Color.rgb(226, 106, 60), Color.WHITE, 21f)
		button(cv, "map", RectF(70f, 396f, 290f, 442f), Data.t("map"), Color.rgb(58, 92, 132), Color.WHITE, 18f)
		button(cv, "shop", RectF(70f, 454f, 290f, 500f), Data.t("shop"), Color.rgb(72, 62, 120), Color.WHITE, 18f)

		button(
			cv, "lang", RectF(70f, 514f, 168f, 552f),
			if (Data.lang == "ar") "English" else "عربي",
			Color.argb(160, 30, 26, 24), Color.WHITE, 15f
		)
		button(
			cv, "sound", RectF(192f, 514f, 290f, 552f),
			if (Save.sound == 1) "Sound: ON" else "Sound: OFF",
			Color.argb(160, 30, 26, 24), Color.WHITE, 14f
		)

		label(
			cv, Data.t("lvl").replace("{n}", Save.level.toString()) + "  ·  " + STAR + " " + Save.starsTotal(),
			180f, 580f, 15f, Color.argb(220, 255, 236, 200)
		)
	}

	/* ---------------- level map ---------------- */

	private fun tiles(): Int = max(1, ceil(Levels.visN() / 10.0).toInt())

	private fun mapContentH(): Float = tiles() * TILEH

	private fun nodeY(n: Int): Float {
		val t = (n - 1) / 10
		val i = (n - 1) % 10
		return t * TILEH + PADS[i][1] * TILEH
	}

	private fun centerMap() {
		scrollMap = clampScroll(nodeY(max(1, min(Data.NLV, Save.level))) - 310f, mapContentH())
	}

	private fun clampScroll(v: Float, contentH: Float): Float {
		val maxS = max(0f, contentH - 640f)
		return v.coerceIn(0f, maxS)
	}

	private fun drawMap(cv: Canvas) {
		cv.drawColor(Color.rgb(22, 30, 26))
		val n = tiles()
		for (t in 0 until n) {
			val y = t * TILEH - scrollMap
			if (y > 640f || y + TILEH < 0f) continue
			img(cv, "bg_map", 0f, y, 360f, TILEH)
		}
		val vis = Levels.visN()
		val ks = 360f / 480f
		for (t in 0 until n) {
			for (i in 0 until 10) {
				val lv = t * 10 + i + 1
				if (lv > vis) break
				val p = PADS[i]
				val x = p[0] * 360f
				val y = t * TILEH + p[1] * TILEH - scrollMap
				if (y < -70f || y > 700f) continue
				val size = (p[2] * ks).roundToInt().toFloat().coerceIn(30f, 86f)
				val open = lv <= Save.level
				img(cv, if (open) "ui_node" else "ui_node_lock", x - size / 2f, y - size / 2f, size, size)
				if (open) {
					label(cv, lv.toString(), x, y + size * .12f, size * .36f, Color.rgb(64, 38, 20), Paint.Align.CENTER, false)
					val st = Save.starsAt(lv)
					if (st > 0) label(cv, starsText(st), x, y + size * .78f, 13f, Color.rgb(255, 214, 100))
				} else {
					label(cv, lv.toString(), x, y + size * .12f, size * .30f, Color.argb(150, 255, 255, 255), Paint.Align.CENTER, false)
				}
				btns.add(Btn("node", RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f), lv))
			}
		}
		fill.color = Color.argb(150, 12, 10, 9)
		cv.drawRect(0f, 0f, 360f, 54f, fill)
		button(cv, "back", RectF(10f, 12f, 92f, 46f), Data.t("back"), Color.rgb(58, 92, 132), Color.WHITE, 15f)
		button(cv, "shop", RectF(102f, 12f, 184f, 46f), Data.t("shop"), Color.rgb(72, 62, 120), Color.WHITE, 15f)
		coinPill(cv, 250f, 14f)
	}

	/* ---------------- shop ---------------- */

	private class Card(
		val key: String,
		val icon: String,
		val swatch: Int,
		val name: String,
		val desc: String,
		val price: Int,
		val state: String, // buy | own | use | using | locked
		val lvl: Int,
		val action: String,
		val arg: Int
	)

	private fun cards(): List<Card> {
		val out = ArrayList<Card>()
		when (shopTab) {
			0 -> for (t in Data.tools) {
				if (t.free) continue
				val locked = Save.level < t.lvl && !Save.owned(t.id)
				out.add(
					Card(
						t.id, t.spr, 0,
						Data.t("i_" + t.id), Data.t("i_" + t.id + "_d"),
						t.price,
						if (Save.owned(t.id)) "own" else if (locked) "locked" else "buy",
						t.lvl, "buyTool", 0
					)
				)
			}

			1 -> for (u in Data.upgrades) {
				val have = Save.upg(u.id)
				val price = if (u.id == "sharp") 100 * max(1, have) else u.price
				val maxed = have >= u.max
				var desc = Data.t("i_" + u.id + "_d")
				if (u.max > 1) desc = desc.replace("{n}", have.toString())
				out.add(
					Card(
						u.id, u.ic, 0, Data.t("i_" + u.id), desc, price,
						if (maxed) "own" else "buy", 1, "buyUp", 0
					)
				)
			}

			2 -> for (d in Data.decos) {
				val have = (Save.ownDeco[d.n] ?: 0) > 0
				out.add(
					Card(
						"deco" + d.n, "bg_shop" + d.n, 0,
						Data.t("d_" + d.n), Data.t("d_" + d.n + "_d"), d.price,
						if (Save.deco == d.n) "using" else if (have) "use" else "buy",
						1, "deco", d.n
					)
				)
			}

			else -> for (d in Data.dyes) {
				val have = (Save.ownDye[d.n] ?: 0) > 0
				out.add(
					Card(
						"dye" + d.n, "", if (d.sw != 0) d.sw else d.c,
						Data.t("dye_" + d.n), Data.t("dyeDesc"), d.price,
						if (Save.dye == d.n) "using" else if (have) "use" else "buy",
						1, "dye", d.n
					)
				)
			}
		}
		return out
	}

	private fun shopContentH(): Float = 130f + shopRows * 168f + 30f

	private fun drawShop(cv: Canvas) {
		cover(cv, Levels.bgKey())
		fill.color = Color.argb(190, 16, 13, 12)
		cv.drawRect(0f, 0f, 360f, 640f, fill)

		val list = cards()
		shopRows = max(1, ceil(list.size / 2.0).toInt())

		cv.save()
		cv.clipRect(0f, 108f, 360f, 640f)
		var i = 0
		for (c in list) {
			val col = i % 2
			val row = i / 2
			val x = 12f + col * 172f
			val y = 118f + row * 168f - scrollShop
			i++
			if (y > 660f || y < -180f) continue
			drawCard(cv, c, x, y)
		}
		cv.restore()

		fill.color = Color.argb(225, 14, 11, 10)
		cv.drawRect(0f, 0f, 360f, 108f, fill)
		button(cv, "back", RectF(10f, 10f, 84f, 42f), Data.t("back"), Color.rgb(58, 92, 132), Color.WHITE, 14f)
		label(cv, Data.t("shopTitle"), 180f, 32f, 18f, Color.rgb(255, 226, 170))
		coinPill(cv, 252f, 10f)

		val tabs = listOf("tabTools", "tabUp", "tabDeco", "tabDye")
		for (t in tabs.indices) {
			val r = RectF(10f + t * 86f, 54f, 10f + t * 86f + 82f, 92f)
			val on = shopTab == t
			button(
				cv, "tab", r, Data.t(tabs[t]),
				if (on) Color.rgb(226, 106, 60) else Color.argb(150, 44, 36, 32),
				if (on) Color.WHITE else Color.argb(220, 235, 225, 215), 13f, t
			)
		}
	}

	private fun drawCard(cv: Canvas, c: Card, x: Float, y: Float) {
		val r = RectF(x, y, x + 164f, y + 158f)
		panel(cv, r, 14f, Color.argb(225, 33, 27, 24), Color.argb(80, 255, 214, 105))

		val slot = RectF(x + 52f, y + 8f, x + 112f, y + 68f)
		img(cv, "ui_slot", slot.left, slot.top, slot.width(), slot.height())
		if (c.swatch != 0) {
			fill.color = c.swatch
			cv.drawCircle(slot.centerX(), slot.centerY(), 18f, fill)
		} else {
			val b = Data.bmp(c.icon)
			if (b != null) {
				val s = min(40f / b.width, 40f / b.height)
				val w = b.width * s
				val h = b.height * s
				cv.drawBitmap(
					b, null,
					RectF(slot.centerX() - w / 2f, slot.centerY() - h / 2f, slot.centerX() + w / 2f, slot.centerY() + h / 2f),
					paint
				)
			}
		}

		label(cv, c.name, r.centerX(), y + 88f, 14f, Color.rgb(255, 232, 190))
		text.textSize = 11f
		val desc = if (text.measureText(c.desc) > 150f) c.desc.take(34) + "…" else c.desc
		label(cv, desc, r.centerX(), y + 104f, 11f, Color.argb(205, 226, 214, 200))

		val br = RectF(x + 26f, y + 116f, x + 138f, y + 148f)
		when (c.state) {
			"own" -> {
				panel(cv, br, 12f, Color.argb(190, 46, 84, 58))
				label(cv, Data.t("owned") + " \u2713", br.centerX(), br.centerY() + 5f, 14f, Color.rgb(200, 240, 210))
			}
			"using" -> {
				panel(cv, br, 12f, Color.argb(190, 40, 70, 110))
				label(cv, Data.t("using") + " \u2713", br.centerX(), br.centerY() + 5f, 14f, Color.rgb(205, 228, 255))
			}
			"use" -> button(cv, c.action, br, Data.t("use"), Color.rgb(58, 92, 132), Color.WHITE, 14f, c.arg, c.key)
			"locked" -> {
				panel(cv, br, 12f, Color.argb(180, 60, 48, 44))
				img(cv, "ui_lock", br.left + 8f, br.top + 6f, 20f, 20f)
				label(
					cv, Data.t("lockLevel").replace("{n}", c.lvl.toString()),
					br.centerX() + 10f, br.centerY() + 5f, 12f, Color.argb(220, 240, 220, 200)
				)
			}
			else -> {
				val afford = Save.coins >= c.price
				panel(cv, br, 12f, if (afford) Color.rgb(226, 106, 60) else Color.argb(170, 70, 58, 52), Color.argb(70, 0, 0, 0))
				img(cv, "coin", br.left + 10f, br.top + 7f, 18f, 18f)
				label(cv, c.price.toString(), br.centerX() + 12f, br.centerY() + 5f, 15f, Color.WHITE)
				btns.add(Btn(c.action, RectF(br), c.arg, c.key))
			}
		}
	}

	/* ---------------- gameplay ---------------- */

	private fun startLevel(n: Int) {
		eng.startLevel(n, vScale)
		screen = "game"
		cutting = false
		toolAng = 0f
		toolTarget = 0f
		toolX = 180f
		toolY = 430f
		val lv = eng.lv
		if (lv != null) {
			val traitKey = when (lv.trait) {
				"rush" -> "tRush"
				"vip" -> "tVip"
				"kid" -> "tKid"
				"picky" -> "tPicky"
				"tip" -> "tTip"
				else -> "tNormal"
			}
			toast(Data.t("newCust").replace("{t}", Data.t(traitKey)) + " · " + Data.t(lv.cid))
		}
		Sfx.play("arrive", .6f)
	}

	private fun drawGame(cv: Canvas) {
		val lv = eng.lv ?: return
		val b = eng.board
		cover(cv, Levels.bgKey())

		cv.save()
		if (lv.trait == "kid" && !eng.over) {
			cv.rotate(
				(Math.sin(eng.time * 3.1).toFloat() * .9f),
				eng.bx + eng.bw / 2f, eng.by + eng.bh
			)
		}
		val dst = RectF(eng.bx, eng.by, eng.bx + eng.bw, eng.by + eng.bh)
		b?.afterBmp?.let { cv.drawBitmap(it, null, dst, paint) }
		b?.let { cv.drawBitmap(it.hair, null, dst, paint) }
		b?.let { cv.drawBitmap(it.coat, null, dst, paint) }

		/* guide overlay on the very first level */
		if (lv.n == 1 && eng.stage == "cut") {
			fill.color = Color.argb(158, 255, 214, 105)
			val cw = eng.cw
			val ch = eng.ch
			for (r in 0 until Data.ROWS) {
				for (c in 0 until Data.COLS) {
					val i = r * Data.COLS + c
					if (i >= eng.mk.size || eng.mk[i] != 1.toByte()) continue
					if (eng.cutSet.contains(i)) continue
					cv.drawRect(
						eng.bx + c * cw, eng.by + r * ch,
						eng.bx + (c + 1) * cw, eng.by + (r + 1) * ch, fill
					)
				}
			}
		}
		cv.restore()

		/* particles */
		for (p in eng.parts) {
			val bmp = p.bmp ?: continue
			val a = if (p.fade) (255 * max(0f, 1f - p.t / p.life)).toInt() else 255
			paint.alpha = a.coerceIn(0, 255)
			cv.save()
			cv.translate(p.x, p.y)
			cv.rotate(p.rot * 57.3f)
			val h = p.s * bmp.height / bmp.width
			cv.drawBitmap(bmp, null, RectF(-p.s / 2f, -h / 2f, p.s / 2f, h / 2f), paint)
			cv.restore()
			paint.alpha = 255
		}

		/* reaction face */
		val face = when {
			eng.result == "win" -> "react_happy"
			eng.pat < .3f -> "react_angry"
			else -> "react_wait"
		}
		img(cv, face, 296f, 88f, 52f, 52f)

		/* tool */
		drawTool(cv)

		/* flash on a wrong cut */
		if (eng.flash > 0f) {
			fill.color = Color.argb((110 * (eng.flash / .16f)).toInt().coerceIn(0, 110), 220, 60, 50)
			cv.drawRect(0f, 0f, 360f, 640f, fill)
		}

		/* HUD */
		drawRing(cv, 38f, 42f, eng.pat)
		val goalTxt = when (eng.stage) {
			"prep" -> Data.t("prepFoam")
			"finish" -> when (lv.fin) {
				"towel" -> Data.t("finTowel")
				"iron" -> Data.t("finIron")
				"dye" -> Data.t("finDye")
				else -> Data.t("finMirror")
			}
			else -> Data.t(lv.tag)
		}
		label(cv, goalTxt, 180f, 32f, 16f, Color.rgb(255, 236, 200))
		label(
			cv,
			Data.t("lvl").replace("{n}", lv.n.toString()) + "  ·  " + Data.t("cut_" + eng.pid),
			180f, 52f, 12f, Color.argb(215, 240, 226, 205)
		)
		coinPill(cv, 252f, 14f)

		/* progress bar */
		val pr = (eng.progress() / max(.01f, if (eng.stage == "cut") lv.need else 1f)).coerceIn(0f, 1f)
		val bar = RectF(56f, 566f, 304f, 578f)
		panel(cv, bar, 6f, Color.argb(150, 18, 14, 13))
		fill.color = Color.rgb(111, 211, 154)
		cv.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * pr, bar.bottom), 6f, 6f, fill)

		drawToolBar(cv)

		if (eng.stage == "cut" && eng.pid != "full") {
			label(cv, Data.t("hintZone"), 180f, 556f, 12f, Color.argb(210, 255, 224, 160))
		}

		for (p in eng.pops) {
			val a = (255 * max(0f, 1f - p.t / 1.1f)).toInt()
			label(cv, p.txt, p.x, p.y - p.t * 42f, 18f, Color.argb(a, 255, 226, 150))
		}

		if (eng.result != null) drawResult(cv, lv)
	}

	private fun drawRing(cv: Canvas, cx: Float, cy: Float, v: Float) {
		val r = 24f
		stroke.strokeWidth = 6f
		stroke.strokeCap = Paint.Cap.ROUND
		stroke.color = Color.argb(140, 16, 13, 12)
		cv.drawCircle(cx, cy, r, stroke)
		stroke.color = when {
			v >= .999f -> Color.rgb(111, 211, 154)
			v < .3f -> Color.rgb(226, 86, 70)
			else -> Color.rgb(255, 215, 100)
		}
		cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * v.coerceIn(0f, 1f), false, stroke)
		label(cv, (v * 100f).roundToInt().toString(), cx, cy + 5f, 13f, Color.WHITE)
	}

	private fun drawToolBar(cv: Canvas) {
		val list = eng.tools
		if (list.isEmpty()) return
		val size = 52f
		val gap = 8f
		val total = list.size * size + (list.size - 1) * gap
		var x = 180f - total / 2f
		for (t in list) {
			val r = RectF(x, 586f, x + size, 586f + size)
			val on = eng.tool?.id == t.id
			panel(cv, r, 12f, if (on) Color.argb(235, 226, 106, 60) else Color.argb(190, 32, 26, 24), Color.argb(90, 255, 214, 105))
			img(cv, "ui_slot", r.left + 2f, r.top + 2f, size - 4f, size - 4f)
			val b = Data.bmp(t.spr)
			if (b != null) {
				val s = min(34f / b.width, 34f / b.height)
				val w = b.width * s
				val h = b.height * s
				cv.drawBitmap(
					b, null,
					RectF(r.centerX() - w / 2f, r.centerY() - h / 2f, r.centerX() + w / 2f, r.centerY() + h / 2f),
					paint
				)
			}
			btns.add(Btn("tool", RectF(r), 0, t.id))
			x += size + gap
		}
		button(cv, "menu", RectF(10f, 592f, 74f, 624f), Data.t("menu"), Color.argb(190, 40, 34, 30), Color.WHITE, 13f)
		if (eng.stage == "finish" && !eng.over) {
			button(cv, "skip", RectF(286f, 592f, 350f, 624f), Data.t("skip"), Color.argb(190, 58, 92, 132), Color.WHITE, 12f)
		}
	}

	private fun drawTool(cv: Canvas) {
		val t = eng.tool ?: return
		val sprKey = if (t.snip && t.alt != null && cutting && (snipT * 9f).toInt() % 2 == 0) t.alt else t.spr
		val b = Data.bmp(sprKey) ?: return
		val w = t.w
		val h = w * b.height / b.width
		val tip = Data.tip(t, sprKey)
		var shake = 0f
		if (t.buzz && cutting) shake = (Math.sin(eng.time * 60.0) * 2.2).toFloat()
		cv.save()
		cv.translate(toolX, toolY + shake)
		cv.rotate(toolAng)
		cv.drawBitmap(
			b, null,
			RectF(-tip[0] * w, -tip[1] * h, -tip[0] * w + w, -tip[1] * h + h),
			paint
		)
		cv.restore()
	}

	private fun drawResult(cv: Canvas, lv: LevelCfg) {
		fill.color = Color.argb(190, 10, 8, 8)
		cv.drawRect(0f, 0f, 360f, 640f, fill)
		val r = RectF(28f, 170f, 332f, 470f)
		panel(cv, r, 20f, Color.argb(245, 34, 27, 24), Color.argb(110, 255, 214, 105))

		if (eng.result == "win") {
			label(cv, Data.t("winTitle"), 180f, 214f, 22f, Color.rgb(255, 226, 160))
			label(cv, starsText(eng.resultStars), 180f, 262f, 40f, Color.rgb(255, 214, 100))
			val msg = when (eng.resultStars) {
				3 -> Data.t("win3")
				2 -> Data.t("win2")
				else -> Data.t("win1")
			}
			label(cv, msg, 180f, 292f, 14f, Color.argb(220, 235, 222, 205))
			img(cv, "coin", 132f, 308f, 26f, 26f)
			label(cv, "+" + eng.resultCoins, 196f, 328f, 20f, Color.rgb(255, 215, 100))
			if (lv.tr.tip > 0 && eng.resultStars == 3) {
				label(cv, Data.t("tipB") + " +60", 180f, 350f, 13f, Color.rgb(160, 220, 180))
			}
			button(cv, "next", RectF(56f, 366f, 304f, 408f), Data.t("next"), Color.rgb(226, 106, 60), Color.WHITE, 17f)
			button(cv, "map", RectF(56f, 416f, 176f, 452f), Data.t("map"), Color.rgb(58, 92, 132), Color.WHITE, 14f)
			button(cv, "shop", RectF(184f, 416f, 304f, 452f), Data.t("shop"), Color.rgb(72, 62, 120), Color.WHITE, 14f)
		} else {
			label(cv, Data.t("failTitle"), 180f, 222f, 22f, Color.rgb(255, 186, 170))
			label(
				cv, Data.t("failMsg").replace("{n}", eng.resultPct.toString()),
				180f, 266f, 14f, Color.argb(225, 240, 220, 210)
			)
			button(cv, "retry", RectF(56f, 320f, 304f, 364f), Data.t("retry"), Color.rgb(226, 106, 60), Color.WHITE, 17f)
			button(cv, "shop", RectF(56f, 376f, 304f, 414f), Data.t("goShop"), Color.rgb(72, 62, 120), Color.WHITE, 15f)
			button(cv, "map", RectF(56f, 424f, 304f, 458f), Data.t("map"), Color.rgb(58, 92, 132), Color.WHITE, 14f)
		}
	}

	/* ---------------- input ---------------- */

	override fun onTouchEvent(e: MotionEvent): Boolean {
		val x = (e.x - offX) / vScale
		val y = (e.y - offY) / vScale
		when (e.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				downX = x
				downY = y
				dragging = false
				scrollStart = if (screen == "map") scrollMap else scrollShop
				if (screen == "game") gameDown(x, y)
			}

			MotionEvent.ACTION_MOVE -> {
				if (abs(x - downX) > 6f || abs(y - downY) > 6f) dragging = true
				if (screen == "map" && dragging) scrollMap = clampScroll(scrollStart - (y - downY), mapContentH())
				if (screen == "shop" && dragging) scrollShop = clampScroll(scrollStart - (y - downY), shopContentH())
				if (screen == "game") gameMove(x, y)
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				if (screen == "game") gameUp()
				if (!dragging) tap(x, y)
			}
		}
		return true
	}

	private fun hit(x: Float, y: Float): Btn? {
		for (i in btns.indices.reversed()) {
			val b = btns[i]
			if (b.r.contains(x, y)) return b
		}
		return null
	}

	private fun gameDown(x: Float, y: Float) {
		if (hit(x, y) != null) return
		if (eng.result != null) return
		cutting = true
		lastPX = x
		lastPY = y
		toolX = x
		toolY = y
		eng.cutting = 1
		eng.stamp(x, y)
	}

	private fun gameMove(x: Float, y: Float) {
		if (!cutting) return
		val t = eng.tool ?: return
		val r = Levels.toolRadius(t)
		val dx = x - lastPX
		val dy = y - lastPY
		val d = sqrt(dx * dx + dy * dy)
		val steps = max(1, (d / max(4f, r * .4f)).toInt())
		for (i in 1..steps) {
			val px = lastPX + dx * i / steps
			val py = lastPY + dy * i / steps
			eng.stamp(px, py)
		}
		toolTarget = (dx * .035f * 57.3f).coerceIn(-20f, 20f)
		toolX = x
		toolY = y
		lastPX = x
		lastPY = y
	}

	private fun gameUp() {
		cutting = false
		eng.cutting = 0
		toolTarget = 0f
	}

	private fun tap(x: Float, y: Float) {
		val b = hit(x, y) ?: return
		Sfx.play("click", .3f)
		when (b.id) {
			"play" -> startLevel(max(1, min(Data.NLV, Save.level)))
			"map" -> {
				screen = "map"
				centerMap()
			}
			"shop" -> {
				screen = "shop"
				scrollShop = 0f
			}
			"back" -> screen = "menu"
			"menu" -> screen = "menu"
			"tab" -> {
				shopTab = b.arg
				scrollShop = 0f
			}
			"node" -> {
				if (b.arg <= Save.level) startLevel(b.arg)
				else toast(Data.t("lockLevel").replace("{n}", b.arg.toString()))
			}
			"next" -> {
				val n = eng.lv?.n ?: Save.level
				startLevel(min(Data.NLV, n + 1))
			}
			"retry" -> startLevel(eng.lv?.n ?: Save.level)
			"skip" -> eng.win()
			"tool" -> {
				val t = Data.toolById[b.key]
				if (t != null) {
					if (Save.owned(t.id)) eng.tool = t else toast(Data.t("locked"))
				}
			}
			"buyTool" -> buyTool(b.key)
			"buyUp" -> buyUpgrade(b.key)
			"deco" -> {
				val d = Data.decos.firstOrNull { it.n == b.arg } ?: return
				if ((Save.ownDeco[d.n] ?: 0) > 0) {
					Save.deco = d.n
					Save.save()
				} else if (Save.spend(d.price)) {
					Save.ownDeco[d.n] = 1
					Save.deco = d.n
					Save.save()
					toast(Data.t("bought").replace("{t}", Data.t("d_" + d.n)))
				} else toast(Data.t("noMoney"))
			}
			"dye" -> {
				val d = Data.dyes.firstOrNull { it.n == b.arg } ?: return
				if ((Save.ownDye[d.n] ?: 0) > 0) {
					Save.dye = d.n
					Save.save()
				} else if (Save.spend(d.price)) {
					Save.ownDye[d.n] = 1
					Save.dye = d.n
					Save.save()
					toast(Data.t("bought").replace("{t}", Data.t("dye_" + d.n)))
				} else toast(Data.t("noMoney"))
			}
			"lang" -> {
				Data.lang = if (Data.lang == "ar") "en" else "ar"
				Save.save()
			}
			"sound" -> {
				Save.sound = if (Save.sound == 1) 0 else 1
				Save.music = Save.sound
				if (Save.music == 0) Sfx.stopMusic() else Sfx.ambience(context, "breeze")
				Save.save()
			}
		}
	}

	private fun buyTool(id: String) {
		val t = Data.toolById[id] ?: return
		if (Save.owned(id)) return
		if (Save.level < t.lvl) {
			toast(Data.t("lockLevel").replace("{n}", t.lvl.toString()))
			return
		}
		if (!Save.spend(t.price)) {
			toast(Data.t("noMoney"))
			return
		}
		Save.own[id] = 1
		Save.save()
		toast(Data.t("bought").replace("{t}", Data.t("i_$id")))
	}

	private fun buyUpgrade(id: String) {
		val u = Data.upgrades.firstOrNull { it.id == id } ?: return
		val have = Save.upg(id)
		if (have >= u.max) return
		val price = if (id == "sharp") 100 * max(1, have) else u.price
		if (!Save.spend(price)) {
			toast(Data.t("noMoney"))
			return
		}
		Save.up[id] = have + 1
		Save.save()
		toast(Data.t("bought").replace("{t}", Data.t("i_$id")))
	}

	override fun onDraw(canvas: Canvas) {
		canvas.drawColor(Color.BLACK)
		canvas.save()
		canvas.translate(offX, offY)
		canvas.scale(vScale, vScale)
		canvas.clipRect(0f, 0f, 360f, 640f)
		btns.clear()
		when (screen) {
			"menu" -> drawMenu(canvas)
			"map" -> drawMap(canvas)
			"shop" -> drawShop(canvas)
			"game" -> drawGame(canvas)
		}
		if (toastT > 0f) drawToast(canvas)
		canvas.restore()
	}
}
