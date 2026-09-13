package com.barber.rush

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class LevelCfg(
	val n: Int,
	val cid: String,
	val trait: String,
	val tr: Trait,
	val gid: String,
	val zones: String,
	val tag: String,
	val cells: Int,
	val time: Float,
	val need: Float,
	val prep: String?,
	val fin: String?,
	val cyc: Int
)

class MaskResult(val mk: ByteArray, val cnt: Int, val pid: String)

/* Faithful port of the level generator, style masks and tool gating. */
object Levels {

	val NO_TRAIT = Trait(1f, 1, false, false, 0)

	fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v

	/* park-miller RNG, identical numbers to the JS build */
	fun rngOf(n: Int): () -> Double {
		var x = (n.toDouble() * 2654435761.0) % 2147483647.0
		if (x <= 0) x += 2147483646.0
		return {
			x = (x * 48271.0) % 2147483647.0
			x / 2147483647.0
		}
	}

	fun traitOf(cid: String): Trait = Data.traits[Data.traitOf[cid] ?: ""] ?: NO_TRAIT

	fun cellsOf(cid: String, zs: String): Int {
		val z = Data.zonesOf(cid)
		var c = 0
		for (i in z.indices) if (zs.indexOf(z[i]) >= 0) c++
		return c
	}

	fun goalsFor(cid: String): List<String> {
		val z = Data.zonesOf(cid)
		var nb = 0
		for (i in z.indices) if (z[i] == '2') nb++
		return if (nb >= 60) listOf("hair", "beard", "all", "all", "hair")
		else listOf("hair", "hair", "hair", "hair")
	}

	fun visN(): Int =
		max(Data.BATCH, min(Data.NLV, (ceil((Save.level + 2).toDouble() / Data.BATCH) * Data.BATCH).toInt()))

	/* shuffled customer blocks: never the same face twice in a row */
	private val custBlocks = HashMap<Int, MutableList<String>>()

	fun custBlock(b: Int): List<String> {
		custBlocks[b]?.let { return it }
		val L = Data.cust.size
		if (L == 0) return listOf("c02")
		var last: String? = null
		for (g in 0..b) {
			val cached = custBlocks[g]
			if (cached != null) {
				last = cached[L - 1]
				continue
			}
			val a = ArrayList(Data.cust)
			if (g > 0) {
				val r = rngOf(g * 7349 + 131)
				for (i in L - 1 downTo 1) {
					val j = floor(r() * (i + 1)).toInt() % (i + 1)
					val t = a[i]
					a[i] = a[j]
					a[j] = t
				}
				if (a[0] == last) {
					val t = a[0]
					a[0] = a[L - 1]
					a[L - 1] = t
				}
			}
			custBlocks[g] = a
			last = a[L - 1]
		}
		return custBlocks[b] ?: listOf("c02")
	}

	fun custFor(n: Int): String {
		val L = max(1, Data.cust.size)
		val k = max(0, n - 1)
		return custBlock(k / L)[k % L]
	}

	fun ownedPrep(p: String): Boolean = Data.tools.any { it.prep == p && Save.owned(it.id) }

	fun levelCfg(n: Int): LevelCfg {
		val r = rngOf(n * 7919 + 17)
		val cyc = min(7, (n - 1) / Data.CYC)
		val cid = custFor(n)
		val gl = goalsFor(cid)
		var gid = gl[floor(r() * gl.size).toInt() % gl.size]
		var g = Data.goals[gid] ?: Data.goals["hair"]!!

		if (g.z.indexOf('2') >= 0 && cellsOf(cid, "2") < 34) {
			gid = "hair"
			g = Data.goals["hair"]!!
		}
		if (g.z.indexOf('1') >= 0 && cellsOf(cid, "1") < 34 && cellsOf(cid, "2") >= 34) {
			gid = "beard"
			g = Data.goals["beard"] ?: g
		}

		val tr = traitOf(cid)
		val cells = cellsOf(cid, g.z)
		val cf = clamp(cells / 300f, .58f, 1.3f)

		var prep: String? = null
		var fin: String? = null
		if (r() < .55) {
			val pp = Data.prepOf[cid] ?: "foam"
			if (ownedPrep(pp) && g.z.indexOf('2') >= 0) prep = pp
		}
		if (r() < .6) {
			val fl = Data.tools.filter { it.fin != null && Save.owned(it.id) }
			if (fl.isNotEmpty()) fin = fl[floor(r() * fl.size).toInt() % fl.size].fin
		}

		val need = min(
			.94,
			.8 + min(.1, (n - 1) * .005) + min(.02, cyc * .005) + (if (r() < .3) .03 else .0)
		).toFloat()

		val time = max(
			17.0,
			g.time * cf * .975.pow(cyc.toDouble()) * (if (tr.pat > 0f) tr.pat.toDouble() else 1.0) *
				(if (Save.upg("mug") > 0) 1.14 else 1.0) *
				(if (Save.upg("clock") > 0) 1.12 else 1.0)
		).toFloat()

		return LevelCfg(
			n, cid, Data.traitOf[cid] ?: "normal", tr, gid, g.z, g.tag,
			cells, time, need, prep, fin, cyc
		)
	}

	/* ---------- haircut style masks ---------- */

	val PATF: Map<String, (Float, Float) -> Boolean> = mapOf(
		"full" to { _, _ -> true },
		"fade" to { _, v -> v > .40f },
		"top" to { _, v -> v < .60f },
		"mohawk" to { u, _ -> abs(u - .5f) > .18f },
		"left" to { u, _ -> u < .54f },
		"right" to { u, _ -> u > .46f },
		"band" to { u, _ -> floor(u * 4.5f).toInt() % 2 == 0 },
		"mid" to { u, _ -> abs(u - .5f) < .32f },
		"check" to { u, v -> (floor(u * 3.2f).toInt() + floor(v * 4.2f).toInt()) % 2 == 0 },
		"crop" to { u, v -> v < .46f || abs(u - .5f) > .33f }
	)

	fun patOf(n: Int): String {
		if (n <= 2) return "full"
		if (Data.pats.isEmpty()) return "full"
		var tot = 0
		for (p in Data.pats) tot += p.second
		val r = rngOf(n * 104729 + 7)
		var x = r() * tot
		for (p in Data.pats) {
			x -= p.second
			if (x <= 0) return p.first
		}
		return Data.pats[0].first
	}

	/* MK(): style mask over the goal zones, with the original safety fallback */
	fun buildMask(n: Int, cid: String, zones: String): MaskResult {
		val zn = Data.zonesOf(cid)
		val cols = Data.COLS
		val rows = Data.ROWS
		var pid = patOf(n)
		val f = PATF[pid] ?: PATF["full"]!!
		val mk = ByteArray(cols * rows)
		var cnt = 0
		var tot = 0
		for (r in 0 until rows) {
			for (c in 0 until cols) {
				val i = r * cols + c
				if (i >= zn.length) continue
				if (zones.indexOf(zn[i]) < 0) continue
				tot++
				val u = (c + .5f) / cols
				val v = (r + .5f) / rows
				if (f(u, v)) {
					mk[i] = 1
					cnt++
				}
			}
		}
		if (cnt < 32 || cnt < tot * .3f) {
			for (i in mk.indices) {
				mk[i] = if (i < zn.length && zones.indexOf(zn[i]) >= 0) 1 else 0
			}
			cnt = tot
			pid = "full"
		}
		return MaskResult(mk, cnt, pid)
	}

	/* ---------- tools ---------- */

	fun toolRadius(t: Tool): Float {
		var r = t.r
		if (t.id == "scissors") r += 8f * ((max(1, Save.upg("sharp"))) - 1)
		if (Save.upg("pomade") > 0) r += 6f
		return r
	}

	fun barTools(lv: LevelCfg): List<Tool> {
		val out = ArrayList<Tool>()
		for (t in Data.tools) {
			if (!Save.owned(t.id)) continue
			if (t.prep != null) {
				if (lv.prep != null && lv.prep == t.prep) out.add(t)
				continue
			}
			if (t.fin != null) {
				if (lv.fin != null && lv.fin == t.fin) out.add(t)
				continue
			}
			if (t.z.isEmpty()) continue
			var hit = false
			for (ch in lv.zones) if (t.z.indexOf(ch) >= 0) hit = true
			if (hit) out.add(t)
		}
		if (out.isEmpty()) Data.toolById["scissors"]?.let { out.add(it) }
		return out
	}

	fun bgKey(): String = "bg_shop" + max(1, Save.deco)

	fun dyeColor(): Int {
		val d = Data.dyes.firstOrNull { it.n == Save.dye } ?: Data.dyes.firstOrNull()
		return d?.c ?: 0
	}

	/* stars: identical thresholds to starsOf() */
	fun starsOf(miss: Int, hard: Boolean): Int {
		val sm = if (Save.upg("smile") > 0) 2 else 0
		val a = (if (hard) 2 else 4) + sm
		val b = (if (hard) 7 else 10) + sm
		return if (miss <= a) 3 else if (miss <= b) 2 else 1
	}
}
