package com.barber.rush

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Particle(
	var x: Float,
	var y: Float,
	var vx: Float,
	var vy: Float,
	var s: Float,
	var g: Float,
	var life: Float,
	var bmp: Bitmap?,
	var fade: Boolean
) {
	var t = 0f
	var rot = (Math.random() * 6.28).toFloat()
	var vr = (Math.random() * 6.0 - 3.0).toFloat()
	var settled = false
}

class PopText(var x: Float, var y: Float, var txt: String) {
	var t = 0f
}

/* The gameplay state machine: G, stamp(), penal(), update(), win(), fail(). */
class Engine {

	var lv: LevelCfg? = null
	var board: Board? = null
	var mk = ByteArray(0)
	var pid = "full"

	/* customer box, in the original 360x640 space */
	var bx = 0f
	var by = 0f
	var bw = 0f
	var bh = 0f
	var scale = 1f

	/* G */
	var pat = 1f
	var time = 0f
	var nCut = 0
	var nWet = 0
	var nFin = 0
	var miss = 0
	var off = 0
	var pool = 0
	var combo = 0
	var maxCombo = 0
	var coinsGain = 0
	var cutting = 0
	var stage = "cut"
	var over = false
	var warn = 0f
	var flash = 0f
	var goal = 6
	var wetGoal = 4
	var finGoal = 8
	var finZones = ""
	private var softN = 0

	val cutSet = HashSet<Int>()
	val wetSet = HashSet<Int>()
	val wipeSet = HashSet<Int>()

	val tools = ArrayList<Tool>()
	var tool: Tool? = null

	val parts = ArrayList<Particle>()
	val pops = ArrayList<PopText>()

	/* result */
	var result: String? = null
	var resultStars = 0
	var resultCoins = 0
	var resultPct = 0
	var unlocked = false

	val cols get() = Data.COLS
	val rows get() = Data.ROWS
	val cw get() = bw / cols
	val ch get() = bh / rows

	fun startLevel(n: Int, viewScale: Float) {
		val cfg = Levels.levelCfg(n)
		lv = cfg
		scale = min(2.2f, max(1f, viewScale))

		val cd = Data.chars[cfg.cid] ?: CharDef(600, 760, "", "hd")
		var h = 516f
		var w = h * cd.w / cd.h
		val maxW = 360f - 22f
		if (w > maxW) {
			w = maxW
			h = w * cd.h / cd.w
		}
		bw = w
		bh = h
		bx = (360f - bw) / 2f
		by = 70f + (516f - bh) * .92f

		val m = Levels.buildMask(n, cfg.cid, cfg.zones)
		mk = m.mk
		pid = m.pid

		board?.release()
		val b = Board(cfg.cid, bw, bh, scale)
		b.buildMasks(mk, cfg.zones)
		board = b

		pat = 1f
		time = 0f
		nCut = 0
		nWet = 0
		nFin = 0
		miss = 0
		off = 0
		pool = 0
		combo = 0
		maxCombo = 0
		coinsGain = 0
		cutting = 0
		over = false
		warn = 0f
		flash = 0f
		softN = 0
		result = null
		unlocked = false
		cutSet.clear()
		wetSet.clear()
		wipeSet.clear()
		parts.clear()
		pops.clear()

		goal = max(6, m.cnt)
		wetGoal = max(4, Levels.cellsOf(cfg.cid, "2"))
		stage = if (cfg.prep != null) "prep" else "cut"

		tools.clear()
		tools.addAll(Levels.barTools(cfg))
		tool = firstToolFor(stage) ?: tools.firstOrNull()
	}

	fun firstToolFor(st: String): Tool? = when (st) {
		"prep" -> tools.firstOrNull { it.prep != null }
		"finish" -> tools.firstOrNull { it.fin != null }
		else -> tools.firstOrNull { it.prep == null && it.fin == null }
	}

	fun progress(): Float = when (stage) {
		"prep" -> nWet.toFloat() / max(1, wetGoal)
		"finish" -> nFin.toFloat() / max(1, finGoal)
		else -> nCut.toFloat() / max(1, goal)
	}

	/* ---------- grid ---------- */

	private fun eachCell(x: Float, y: Float, r: Float, fn: (Int, Int, Int) -> Unit) {
		val rr = (r * 1.22f) * (r * 1.22f)
		val c0 = max(0, ((x - r * 1.3f - bx) / cw).toInt())
		val c1 = min(cols - 1, ((x + r * 1.3f - bx) / cw).toInt())
		val r0 = max(0, ((y - r * 1.3f - by) / ch).toInt())
		val r1 = min(rows - 1, ((y + r * 1.3f - by) / ch).toInt())
		for (ry in r0..r1) {
			for (c in c0..c1) {
				val cx = bx + (c + .5f) * cw
				val cy = by + (ry + .5f) * ch
				val dx = cx - x
				val dy = cy - y
				if (dx * dx + dy * dy > rr) continue
				fn(ry * cols + c, c, ry)
			}
		}
	}

	private fun zoneAt(i: Int): Char {
		val zn = board?.zn ?: return '0'
		return if (i < zn.length) zn[i] else '0'
	}

	/* ---------- patience ---------- */

	fun penal(soft: Boolean) {
		val cfg = lv ?: return
		val base = min(.045f, .55f / max(8f, cfg.time))
		var unit = base
		if (soft) {
			softN++
			if (softN <= 14) return
			unit = base * .45f
		}
		miss++
		pat = max(.06f, pat - unit)
		warn = .35f
		Sfx.play("error", .45f)
	}

	/* ---------- brush stamp ---------- */

	fun stamp(px: Float, py: Float) {
		if (over) return
		val cfg = lv ?: return
		val b = board ?: return
		val t = tool ?: return
		val r = Levels.toolRadius(t)
		val lx = (px - bx) * scale
		val ly = (py - by) * scale

		when (stage) {
			"prep" -> {
				if (t.prep == null) {
					penal(false)
					return
				}
				var added = 0
				eachCell(px, py, r) { i, _, _ ->
					if (zoneAt(i) == '2' && !wetSet.contains(i)) {
						wetSet.add(i)
						nWet++
						added++
					}
				}
				b.paintBlob(lx, ly, cw * 1.42f * scale, if (t.paint != 0) t.paint else 0x80FFFFFF.toInt())
				if (added > 0) {
					Sfx.play("brush", .45f)
					spawnFx(px, py, t.fx ?: "fm")
				}
				val need = if (Save.upg("bowl") > 0) .5f else .62f
				if (nWet.toFloat() / max(1, wetGoal) >= need) {
					stage = "cut"
					tool = firstToolFor("cut") ?: tool
					pops.add(PopText(180f, 300f, Data.t("prepDone")))
				}
			}

			"cut" -> {
				if (t.prep != null || t.fin != null) {
					penal(false)
					return
				}
				var cut = 0
				eachCell(px, py, r) { i, c, ry ->
					val z = zoneAt(i)
					if (z == '0' || z == '3') return@eachCell
					if (t.z.isNotEmpty() && t.z.indexOf(z) < 0) {
						if (!cutSet.contains(i)) {
							penal(false)
							flash = .16f
						}
						return@eachCell
					}
					if (cfg.zones.indexOf(z) < 0) {
						if (!cutSet.contains(i)) {
							penal(false)
							flash = .16f
						}
						return@eachCell
					}
					if (i >= mk.size || mk[i] != 1.toByte()) {
						if (!cutSet.contains(i)) {
							off++
							penal(true)
							flash = .16f
						}
						return@eachCell
					}
					if (cutSet.contains(i)) return@eachCell
					cutSet.add(i)
					nCut++
					pool++
					cut++
					val x0 = (c - .4f) * cw * scale
					val y0 = (ry - .4f) * ch * scale
					val x1 = (c + .8f) * cw * scale
					val y1 = (ry + .8f) * ch * scale
					b.cellWipe(x0, y0, x1, y1)
					b.coatWipe(x0, y0, x1, y1)
					spawnHair(bx + (c + .5f) * cw, by + (ry + .5f) * ch, z)
				}
				b.eraseCut(lx, ly, r * scale, true)
				if (cut > 0) {
					if (t.snip) Sfx.play("snip" + (1 + (Math.random() * 3).toInt().coerceAtMost(2)), .5f)
					else if (t.buzz) Sfx.play(if (t.id == "trimmer") "trimmer" else "clipper", .35f)
					if (pool >= 7) {
						pool = 0
						coinsGain++
						combo++
						if (combo > maxCombo) maxCombo = combo
						if (combo >= 3 && (combo - 3) % 5 == 0) pops.add(PopText(px, py - 20f, "x" + combo))
						Sfx.play("coin", .3f)
					}
				}
				if (progress() >= cfg.need) done()
			}

			"finish" -> {
				if (t.fin == null || t.fin != cfg.fin) {
					penal(false)
					return
				}
				var hit = 0
				eachCell(px, py, r) { i, c, ry ->
					val z = zoneAt(i)
					if (finZones.indexOf(z) < 0) return@eachCell
					if (wipeSet.contains(i)) return@eachCell
					wipeSet.add(i)
					nFin++
					hit++
					if (t.fin == "towel" || t.fin == "iron") {
						b.coatWipe(
							(c - .4f) * cw * scale,
							(ry - .4f) * ch * scale,
							(c + .8f) * cw * scale,
							(ry + .8f) * ch * scale
						)
					}
				}
				val pc = if (t.fin == "dye") Levels.dyeColor() else t.paint
				if (pc != 0) b.paintBlob(lx, ly, r * scale * .9f, pc)
				if (hit > 0) {
					spawnFx(px, py, t.fx ?: "st")
					when (t.fin) {
						"towel" -> Sfx.play("towel", .4f)
						"iron" -> Sfx.play("steam", .4f)
						"mirror" -> Sfx.play("mirror", .4f)
						else -> Sfx.play("spray", .4f)
					}
				}
				if (nFin >= finGoal) win()
			}
		}
	}

	/* cut goal reached: go to the finishing stage, or win */
	fun done() {
		val cfg = lv ?: return
		if (cfg.fin != null && stage != "finish") {
			stage = "finish"
			finZones = finZonesFor(cfg)
			var count = 0
			val zn = board?.zn ?: ""
			for (i in zn.indices) if (finZones.indexOf(zn[i]) >= 0) count++
			finGoal = max(8, (count * .7f).roundToInt())
			wipeSet.clear()
			nFin = 0
			tool = firstToolFor("finish") ?: tool
			val key = when (cfg.fin) {
				"towel" -> "finTowel"
				"iron" -> "finIron"
				"dye" -> "finDye"
				else -> "finMirror"
			}
			pops.add(PopText(180f, 300f, Data.t(key)))
			return
		}
		win()
	}

	private fun finZonesFor(cfg: LevelCfg): String = when (cfg.fin) {
		"mirror" -> "123"
		"dye" -> if (Levels.cellsOf(cfg.cid, "1") > 24) "1" else "2"
		else -> cfg.zones
	}

	/* ---------- loop ---------- */

	fun update(dt: Float) {
		val cfg = lv ?: return
		time += dt
		if (warn > 0f) warn = max(0f, warn - dt)
		if (flash > 0f) flash = max(0f, flash - dt)

		if (!over) {
			val mul = when (stage) {
				"finish" -> .4f
				"prep" -> .5f
				else -> 1f
			}
			pat -= (if (cutting > 0) .72f else 1f) * mul * dt / cfg.time *
				(if (Save.upg("ribbon") > 0) .88f else 1f)
			if (pat <= 0f) {
				pat = 0f
				fail()
			}
		}

		var i = 0
		while (i < parts.size) {
			val p = parts[i]
			p.t += dt
			if (!p.settled) {
				p.vy += p.g * dt
				p.x += p.vx * dt
				p.y += p.vy * dt
				p.rot += p.vr * dt
				val fy = 452f + (p.x - 180f) * .06f
				if (p.g > 0f && p.y >= fy) {
					p.y = fy
					p.settled = true
					p.vx = 0f
					p.vy = 0f
				}
			}
			if (p.t > p.life && (p.fade || !p.settled)) {
				parts.removeAt(i)
				continue
			}
			i++
		}

		var settled = 0
		for (p in parts) if (p.settled) settled++
		if (settled > 44) {
			var removed = 0
			val target = settled - 44
			var j = 0
			while (j < parts.size && removed < target) {
				if (parts[j].settled) {
					parts.removeAt(j)
					removed++
				} else j++
			}
		}

		var q = 0
		while (q < pops.size) {
			pops[q].t += dt
			if (pops[q].t > 1.1f) pops.removeAt(q) else q++
		}
	}

	/* ---------- particles ---------- */

	fun spawnHair(x: Float, y: Float, zone: Char) {
		if (parts.size > 110) return
		val cfg = lv ?: return
		val fam: String = if (zone == '2') "hb" else (Data.chars[cfg.cid]?.fx ?: "hd")
		val n = 1 + (Math.random() * 2).toInt()
		repeat(n) {
			parts.add(
				Particle(
					x, y,
					(Math.random() * 140.0 - 70.0).toFloat(),
					(-160.0 + Math.random() * 120.0).toFloat(),
					((if (zone == '2') 7.0 else 9.0) + Math.random() * 6.0).toFloat(),
					1150f,
					1.5f,
					Data.randomPart(fam) ?: Data.randomPart("hd"),
					false
				)
			)
		}
	}

	fun spawnFx(x: Float, y: Float, fam: String) {
		if (parts.size > 190) return
		repeat(2) {
			parts.add(
				Particle(
					x, y,
					(Math.random() * 90.0 - 45.0).toFloat(),
					(Math.random() * 90.0 - 45.0).toFloat(),
					(16.0 + Math.random() * 14.0).toFloat(),
					if (fam == "st") -160f else 420f,
					1.1f,
					Data.randomPart(fam),
					true
				)
			)
		}
	}

	fun burst(x: Float, y: Float) {
		val fams = listOf("cf", "cg", "sp")
		repeat(26) {
			val fam = fams[(Math.random() * 3.0).toInt().coerceIn(0, 2)]
			parts.add(
				Particle(
					x, y,
					(Math.random() * 300.0 - 150.0).toFloat(),
					(-260.0 + Math.random() * 160.0).toFloat(),
					(14.0 + Math.random() * 16.0).toFloat(),
					560f,
					1.6f,
					Data.randomPart(fam),
					true
				)
			)
		}
	}

	/* ---------- end of round ---------- */

	fun win() {
		if (over) return
		over = true
		val cfg = lv ?: return
		val st = Levels.starsOf(miss, cfg.tr.hard)
		var rew = (
			(10 + st * 6 + (maxCombo * .5f).roundToInt() + (coinsGain * .45f).roundToInt() +
				(if (Save.upg("bag") > 0) 30 else 0)) *
				(if (Save.upg("gem") > 0) 1.1f else 1f)
			).roundToInt()
		rew *= max(1, cfg.tr.coin)
		if (Save.upg("crown") > 0) rew = (rew * 1.2f).roundToInt()
		if (cfg.tr.tip > 0 && st == 3) rew += 60

		Save.coins += rew
		Save.stars[cfg.n] = max(Save.starsAt(cfg.n), st)
		if (cfg.n >= Save.level && cfg.n < Data.NLV) {
			Save.level = cfg.n + 1
			unlocked = true
		}
		Save.save()

		result = "win"
		resultStars = st
		resultCoins = rew
		burst(180f, 240f)
		Sfx.play("success", .7f)
	}

	fun fail() {
		if (over) return
		over = true
		val cfg = lv ?: return
		resultPct = if (stage == "finish") 100
		else (100f * min(1f, (nCut.toFloat() / max(1, goal)) / max(.01f, cfg.need))).roundToInt()
		result = "fail"
		Sfx.play("error", .8f)
	}
}
