package com.barber.rush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Board = the customer layers + every mask of the original engine:
 *   revealMask  (before/after pixel diff, dilated by 1 px)
 *   brushMask   (same diff, dilated by 2 px - used by wet/paint tools)
 *   cutAllowMask(goal zones AND style mask, opened by erode+dilate)
 *   finalMask   (reveal AND cutAllow - what scissors are allowed to remove)
 */
class Board(val cid: String, val boxW: Float, val boxH: Float, val scale: Float) {

	private val cols = Data.COLS
	private val rows = Data.ROWS
	val zn: String = Data.zonesOf(cid)

	val w: Int = max(1, (boxW * scale).roundToInt())
	val h: Int = max(1, (boxH * scale).roundToInt())

	/* masks live at half resolution, scaled up when composited */
	private val mScale = max(0.8f, scale * 0.5f)
	private val k = mScale / scale
	private val mw: Int = max(1, (boxW * mScale).roundToInt())
	private val mh: Int = max(1, (boxH * mScale).roundToInt())

	val beforeBmp: Bitmap? = Data.bmp(cid + "_before")
	val afterBmp: Bitmap? = Data.bmp(cid + "_after")

	val hair: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
	val coat: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
	private val hairCv = Canvas(hair)
	private val coatCv = Canvas(coat)

	private val blit = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
	private val dstIn = Paint().apply {
		isAntiAlias = true
		isFilterBitmap = true
		xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
	}
	private val dstOut = Paint().apply {
		isAntiAlias = true
		isFilterBitmap = true
		xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
	}
	private val soft = Paint().apply { isAntiAlias = true }
	private val solid = Paint().apply { isAntiAlias = true }

	private var tile: Bitmap? = null
	private var tileCv: Canvas? = null

	private var baseDiff: ByteArray? = null
	private var revealM: Bitmap? = null
	private var brushM: Bitmap? = null
	private var finalM: Bitmap? = null
	private var allowArr: ByteArray? = null

	init {
		resetLayers()
	}

	fun resetLayers() {
		hair.eraseColor(0)
		coat.eraseColor(0)
		beforeBmp?.let { hairCv.drawBitmap(it, null, Rect(0, 0, w, h), blit) }
	}

	fun release() {
		hair.recycle()
		coat.recycle()
		tile?.recycle()
		revealM?.recycle()
		brushM?.recycle()
		finalM?.recycle()
		tile = null
		revealM = null
		brushM = null
		finalM = null
	}

	/* ---------- mask building ---------- */

	private fun pixels(b: Bitmap?, tw: Int, th: Int): IntArray {
		val out = IntArray(tw * th)
		if (b == null) return out
		val s = Bitmap.createScaledBitmap(b, tw, th, true)
		s.getPixels(out, 0, tw, 0, 0, tw, th)
		if (s !== b) s.recycle()
		return out
	}

	private fun diffBase(): ByteArray {
		baseDiff?.let { return it }
		val a = pixels(beforeBmp, mw, mh)
		val b = pixels(afterBmp, mw, mh)
		val m = ByteArray(mw * mh)
		for (i in m.indices) {
			val p = a[i]
			val q = b[i]
			val pa = (p ushr 24) and 255
			val qa = (q ushr 24) and 255
			if (pa < 8 && qa < 8) continue
			val dr = abs(((p shr 16) and 255) - ((q shr 16) and 255))
			val dg = abs(((p shr 8) and 255) - ((q shr 8) and 255))
			val db = abs((p and 255) - (q and 255))
			val da = abs(pa - qa)
			val dm = max(max(dr, dg), max(db, da))
			if (dm >= 14) m[i] = 1
		}
		baseDiff = m
		return m
	}

	private fun grow(src: ByteArray, times: Int): ByteArray {
		var cur = src
		repeat(max(0, times)) {
			val out = ByteArray(cur.size)
			for (y in 0 until mh) {
				val row = y * mw
				for (x in 0 until mw) {
					val i = row + x
					if (cur[i] == 1.toByte()) {
						out[i] = 1
						continue
					}
					var hit = false
					var dy = -1
					while (dy <= 1 && !hit) {
						var dx = -1
						while (dx <= 1) {
							val nx = x + dx
							val ny = y + dy
							if (nx in 0 until mw && ny in 0 until mh && cur[ny * mw + nx] == 1.toByte()) {
								hit = true
								break
							}
							dx++
						}
						dy++
					}
					if (hit) out[i] = 1
				}
			}
			cur = out
		}
		return cur
	}

	private fun shrink(src: ByteArray, times: Int): ByteArray {
		var cur = src
		repeat(max(0, times)) {
			val out = ByteArray(cur.size)
			for (y in 0 until mh) {
				val row = y * mw
				for (x in 0 until mw) {
					val i = row + x
					if (cur[i] == 0.toByte()) continue
					var keep = true
					var dy = -1
					while (dy <= 1 && keep) {
						var dx = -1
						while (dx <= 1) {
							val nx = x + dx
							val ny = y + dy
							val v = if (nx in 0 until mw && ny in 0 until mh) cur[ny * mw + nx] else 0
							if (v == 0.toByte()) {
								keep = false
								break
							}
							dx++
						}
						dy++
					}
					if (keep) out[i] = 1
				}
			}
			cur = out
		}
		return cur
	}

	private fun toBitmap(m: ByteArray): Bitmap {
		val px = IntArray(m.size)
		for (i in m.indices) px[i] = if (m[i] == 1.toByte()) Color.WHITE else 0
		val b = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
		b.setPixels(px, 0, mw, 0, 0, mw, mh)
		return b
	}

	/* cells of the goal zones that the style mask keeps */
	private fun cellMask(mk: ByteArray, zones: String): ByteArray {
		val m = ByteArray(mw * mh)
		val cw = mw.toFloat() / cols
		val ch = mh.toFloat() / rows
		for (r in 0 until rows) {
			for (c in 0 until cols) {
				val i = r * cols + c
				if (i >= zn.length) continue
				if (zones.indexOf(zn[i]) < 0) continue
				if (mk[i] != 1.toByte()) continue
				val x0 = max(0, ((c - 0.35f) * cw).toInt())
				val x1 = min(mw, ((c + 1.35f) * cw).toInt())
				val y0 = max(0, ((r - 0.35f) * ch).toInt())
				val y1 = min(mh, ((r + 1.35f) * ch).toInt())
				for (y in y0 until y1) {
					val row = y * mw
					for (x in x0 until x1) m[row + x] = 1
				}
			}
		}
		return m
	}

	fun buildMasks(mk: ByteArray, zones: String) {
		val base = diffBase()
		val d = max(1, scale.roundToInt())
		revealM?.recycle()
		brushM?.recycle()
		finalM?.recycle()
		val rev = grow(base, d)
		revealM = toBitmap(rev)
		brushM = toBitmap(grow(base, 2 * d))
		val g = max(1, (3f * mScale).roundToInt())
		val opened = grow(shrink(cellMask(mk, zones), g), g)
		allowArr = opened
		val fin = ByteArray(opened.size)
		for (i in fin.indices) if (opened[i] == 1.toByte() && rev[i] == 1.toByte()) fin[i] = 1
		finalM = toBitmap(fin)
	}

	/* ---------- brush operations (tile clipped by a mask) ---------- */

	private fun tileFor(size: Int): Canvas {
		var t = tile
		if (t == null || t.width < size) {
			t?.recycle()
			t = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
			tile = t
			tileCv = Canvas(t)
		}
		return tileCv!!
	}

	private fun clipByMask(cv: Canvas, mask: Bitmap, left: Float, top: Float, size: Int) {
		val sl = left * k
		val st = top * k
		val sr = (left + size) * k
		val sb = (top + size) * k
		val cl = sl.coerceIn(0f, mask.width.toFloat())
		val ct = st.coerceIn(0f, mask.height.toFloat())
		val cr = sr.coerceIn(0f, mask.width.toFloat())
		val cb = sb.coerceIn(0f, mask.height.toFloat())
		if (cr - cl < 1f || cb - ct < 1f) {
			cv.drawColor(0, PorterDuff.Mode.CLEAR)
			return
		}
		val src = Rect(cl.toInt(), ct.toInt(), max(cl.toInt() + 1, cr.toInt()), max(ct.toInt() + 1, cb.toInt()))
		val dst = RectF(
			(cl - sl) / k,
			(ct - st) / k,
			(cr - sl) / k,
			(cb - st) / k
		)
		cv.drawBitmap(mask, src, dst, dstIn)
	}

	/* eraseCut(): remove hair inside the radius, only where the mask allows */
	fun eraseCut(px: Float, py: Float, r: Float, keep: Boolean) {
		val mask = (if (keep) (finalM ?: revealM) else (brushM ?: revealM)) ?: return
		val rr = max(2f, r)
		val size = (rr * 2f).toInt() + 6
		val cv = tileFor(size)
		val c = size / 2f
		cv.save()
		cv.clipRect(0f, 0f, size.toFloat(), size.toFloat())
		cv.drawColor(0, PorterDuff.Mode.CLEAR)
		soft.shader = RadialGradient(
			c, c, rr,
			intArrayOf(Color.WHITE, Color.WHITE, 0),
			floatArrayOf(0f, .62f, 1f),
			Shader.TileMode.CLAMP
		)
		cv.drawCircle(c, c, rr, soft)
		soft.shader = null
		val left = px - c
		val top = py - c
		clipByMask(cv, mask, left, top, size)
		cv.restore()
		val t = tile ?: return
		hairCv.drawBitmap(t, Rect(0, 0, size, size), RectF(left, top, left + size, top + size), dstOut)
	}

	/* foam / dye: colour painted only over the customer's hair pixels */
	fun paintBlob(px: Float, py: Float, r: Float, color: Int) {
		val mask = (brushM ?: revealM) ?: return
		val rr = max(2f, r)
		val size = (rr * 2f).toInt() + 6
		val cv = tileFor(size)
		val c = size / 2f
		cv.save()
		cv.clipRect(0f, 0f, size.toFloat(), size.toFloat())
		cv.drawColor(0, PorterDuff.Mode.CLEAR)
		solid.color = color
		cv.drawCircle(c, c, rr, solid)
		val left = px - c
		val top = py - c
		clipByMask(cv, mask, left, top, size)
		cv.restore()
		val t = tile ?: return
		coatCv.drawBitmap(t, Rect(0, 0, size, size), RectF(left, top, left + size, top + size), blit)
	}

	/* cellWipe(): clear one grid cell of hair (or of the coat layer) */
	fun cellWipe(x0: Float, y0: Float, x1: Float, y1: Float) {
		val rect = RectF(x0, y0, x1, y1)
		hairCv.drawRect(rect, dstOut)
	}

	fun coatWipe(x0: Float, y0: Float, x1: Float, y1: Float) {
		coatCv.drawRect(RectF(x0, y0, x1, y1), dstOut)
	}

	fun clearAllHair() {
		val mask = finalM ?: return
		hairCv.drawBitmap(mask, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), dstOut)
	}

	fun allowedAt(px: Float, py: Float): Boolean {
		val a = allowArr ?: return true
		val x = (px * k).toInt()
		val y = (py * k).toInt()
		if (x < 0 || y < 0 || x >= mw || y >= mh) return false
		return a[y * mw + x] == 1.toByte()
	}
}
