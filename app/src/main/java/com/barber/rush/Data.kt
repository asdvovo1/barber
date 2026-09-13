package com.barber.rush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONObject

/* ---------- data model (1:1 with the original tables) ---------- */

class Tool(
	val id: String,
	val z: String,
	val r: Float,
	val w: Float,
	val spr: String,
	val alt: String?,
	val tk: String,
	val snip: Boolean,
	val buzz: Boolean,
	val free: Boolean,
	val prep: String?,
	val fin: String?,
	val paint: Int,
	val fx: String?,
	val price: Int,
	val lvl: Int,
	val fix: FloatArray?
)

class Goal(val z: String, val time: Float, val tag: String)

class Trait(val pat: Float, val coin: Int, val fidget: Boolean, val hard: Boolean, val tip: Int)

class Upg(val id: String, val ic: String, val max: Int, val price: Int)

class Deco(val n: Int, val price: Int)

class Dye(val n: Int, val c: Int, val sw: Int, val price: Int)

class CharDef(val w: Int, val h: Int, val zones: String, val fx: String)

object Data {

	lateinit var app: Context

	private var A = JSONObject()
	private var txtAr = JSONObject()
	private var txtEn = JSONObject()

	var COLS = 26
	var ROWS = 40
	var NLV = 110
	var CYC = 14
	var BATCH = 10
	var lang = "ar"

	val cust = ArrayList<String>()
	val traitOf = HashMap<String, String>()
	val traits = HashMap<String, Trait>()
	val prepOf = HashMap<String, String>()
	val finOf = HashMap<String, String>()
	val goals = HashMap<String, Goal>()
	val tools = ArrayList<Tool>()
	val toolById = HashMap<String, Tool>()
	val upgrades = ArrayList<Upg>()
	val decos = ArrayList<Deco>()
	val dyes = ArrayList<Dye>()
	val pats = ArrayList<Pair<String, Int>>()
	val chars = HashMap<String, CharDef>()

	private val bitmaps = HashMap<String, Bitmap?>()
	private val partCache = HashMap<String, List<Bitmap>>()
	private val tips = HashMap<String, HashMap<String, FloatArray>>()
	private var ready = false

	fun init(ctx: Context) {
		if (ready) return
		ready = true
		app = ctx.applicationContext
		A = json("data/assets.json")
		txtAr = json("data/txt_ar.json")
		txtEn = json("data/txt_en.json")
		readZones(json("data/zones.json"))
		readTables(json("data/tables.json"))
	}

	private fun json(path: String): JSONObject {
		return try {
			app.assets.open(path).use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
		} catch (e: Exception) {
			JSONObject()
		}
	}

	private fun readZones(m: JSONObject) {
		COLS = m.optInt("cols", 26)
		ROWS = m.optInt("rows", 40)
		val ch = m.optJSONObject("chars") ?: JSONObject()
		val ci = ch.keys()
		while (ci.hasNext()) {
			val k = ci.next()
			val o = ch.optJSONObject(k) ?: continue
			chars[k] = CharDef(
				o.optInt("w", 600),
				o.optInt("h", 760),
				o.optString("zones", ""),
				o.optString("fx", "hd")
			)
		}
		val tl = m.optJSONObject("tools") ?: JSONObject()
		val ti = tl.keys()
		while (ti.hasNext()) {
			val k = ti.next()
			val tp = tl.optJSONObject(k)?.optJSONObject("tips") ?: continue
			val map = HashMap<String, FloatArray>()
			val pi = tp.keys()
			while (pi.hasNext()) {
				val tk = pi.next()
				val arr = tp.optJSONArray(tk) ?: continue
				map[tk] = floatArrayOf(arr.optDouble(0, 0.95).toFloat(), arr.optDouble(1, 0.05).toFloat())
			}
			tips[k] = map
		}
	}

	private fun readTables(t: JSONObject) {
		NLV = t.optInt("NLV", 110)
		CYC = t.optInt("CYC", 14)
		BATCH = t.optInt("BATCH", 10)

		val cu = t.optJSONArray("CUST")
		if (cu != null) for (i in 0 until cu.length()) cust.add(cu.optString(i))

		val to = t.optJSONObject("TRAITOF") ?: JSONObject()
		val toi = to.keys()
		while (toi.hasNext()) {
			val k = toi.next()
			traitOf[k] = to.optString(k)
		}

		val tr = t.optJSONObject("TRAIT") ?: JSONObject()
		val tri = tr.keys()
		while (tri.hasNext()) {
			val k = tri.next()
			val o = tr.optJSONObject(k) ?: JSONObject()
			traits[k] = Trait(
				o.optDouble("pat", 1.0).toFloat(),
				o.optInt("coin", 1),
				o.optInt("fidget", 0) == 1,
				o.optInt("hard", 0) == 1,
				o.optInt("tip", 0)
			)
		}

		val pr = t.optJSONObject("PREP") ?: JSONObject()
		val pri = pr.keys()
		while (pri.hasNext()) {
			val k = pri.next()
			prepOf[k] = pr.optString(k)
		}

		val fi = t.optJSONObject("FIN") ?: JSONObject()
		val fii = fi.keys()
		while (fii.hasNext()) {
			val k = fii.next()
			finOf[k] = fi.optString(k)
		}

		val go = t.optJSONObject("GOALS") ?: JSONObject()
		val goi = go.keys()
		while (goi.hasNext()) {
			val k = goi.next()
			val o = go.optJSONObject(k) ?: continue
			goals[k] = Goal(o.optString("z"), o.optDouble("time", 40.0).toFloat(), o.optString("tag"))
		}

		val ts = t.optJSONArray("TOOLS")
		if (ts != null) for (i in 0 until ts.length()) {
			val o = ts.optJSONObject(i) ?: continue
			val fixArr = o.optJSONArray("fix")
			val tool = Tool(
				o.optString("id"),
				o.optString("z", ""),
				o.optDouble("r", 48.0).toFloat(),
				o.optDouble("w", 120.0).toFloat(),
				o.optString("spr"),
				if (o.has("alt")) o.optString("alt") else null,
				o.optString("tk", "ur"),
				o.optInt("snip", 0) == 1,
				o.optInt("buzz", 0) == 1,
				o.optInt("free", 0) == 1,
				if (o.has("prep")) o.optString("prep") else null,
				if (o.has("fin")) o.optString("fin") else null,
				parseCss(o.optString("paint", "")),
				if (o.has("fx")) o.optString("fx") else null,
				o.optInt("price", 0),
				o.optInt("lvl", 1),
				if (fixArr != null) floatArrayOf(
					fixArr.optDouble(0, 0.52).toFloat(),
					fixArr.optDouble(1, 0.30).toFloat()
				) else null
			)
			tools.add(tool)
			toolById[tool.id] = tool
		}

		val up = t.optJSONArray("UPG")
		if (up != null) for (i in 0 until up.length()) {
			val o = up.optJSONObject(i) ?: continue
			upgrades.add(Upg(o.optString("id"), o.optString("ic"), o.optInt("max", 1), o.optInt("price", 0)))
		}

		val dc = t.optJSONArray("DECO")
		if (dc != null) for (i in 0 until dc.length()) {
			val o = dc.optJSONObject(i) ?: continue
			decos.add(Deco(o.optInt("n", 1), o.optInt("price", 0)))
		}

		val dy = t.optJSONArray("DYES")
		if (dy != null) for (i in 0 until dy.length()) {
			val o = dy.optJSONObject(i) ?: continue
			dyes.add(
				Dye(
					o.optInt("n", 1),
					parseCss(o.optString("c")),
					parseCss(o.optString("sw")),
					o.optInt("price", 0)
				)
			)
		}

		val pt = t.optJSONArray("PATS")
		if (pt != null) for (i in 0 until pt.length()) {
			val o = pt.optJSONArray(i) ?: continue
			pats.add(Pair(o.optString(0, "full"), o.optInt(1, 1)))
		}
	}

	/* ---------- text ---------- */

	fun t(key: String): String {
		if (lang == "en") {
			val en = txtEn.optString(key, "")
			if (en.isNotEmpty()) return en
		}
		val ar = txtAr.optString(key, "")
		return if (ar.isNotEmpty()) ar else key
	}

	fun t(key: String, token: String, value: String): String =
		t(key).replace("{$token}", value)

	/* ---------- art ---------- */

	fun path(key: String): String = A.optString(key, "")

	fun bmp(key: String): Bitmap? {
		if (bitmaps.containsKey(key)) return bitmaps[key]
		val b = decode(A.optString(key, ""))
		bitmaps[key] = b
		return b
	}

	fun decode(path: String): Bitmap? {
		if (path.isEmpty()) return null
		return try {
			app.assets.open(path).use { BitmapFactory.decodeStream(it) }
		} catch (e: Exception) {
			null
		}
	}

	fun parts(family: String): List<Bitmap> {
		partCache[family]?.let { return it }
		val out = ArrayList<Bitmap>()
		val arr = A.optJSONObject("parts")?.optJSONArray(family)
		if (arr != null) for (i in 0 until arr.length()) {
			decode(arr.optString(i))?.let { out.add(it) }
		}
		partCache[family] = out
		return out
	}

	fun randomPart(family: String): Bitmap? {
		val list = parts(family)
		if (list.isEmpty()) return null
		return list[(Math.random() * list.size).toInt().coerceAtMost(list.size - 1)]
	}

	fun tip(tool: Tool, spr: String): FloatArray {
		if (tool.tk == "fix" && tool.fix != null) return tool.fix
		return tips[spr]?.get(tool.tk) ?: floatArrayOf(0.95f, 0.05f)
	}

	fun zonesOf(cid: String): String = chars[cid]?.zones ?: ""

	/* ---------- css colors ---------- */

	fun parseCss(raw: String?): Int {
		if (raw == null) return 0
		val v = raw.trim()
		if (v.isEmpty()) return 0
		if (v.startsWith("#")) return try {
			Color.parseColor(v)
		} catch (e: Exception) {
			0
		}
		if (v.startsWith("rgb")) {
			val open = v.indexOf('(')
			val close = v.lastIndexOf(')')
			if (open < 0 || close <= open) return 0
			val seg = v.substring(open + 1, close).split(",")
			if (seg.size < 3) return 0
			return try {
				val r = seg[0].trim().toFloat().toInt()
				val g = seg[1].trim().toFloat().toInt()
				val b = seg[2].trim().toFloat().toInt()
				val a = if (seg.size > 3) (seg[3].trim().toFloat() * 255f).toInt() else 255
				Color.argb(a.coerceIn(0, 255), r, g, b)
			} catch (e: Exception) {
				0
			}
		}
		return 0
	}
}
