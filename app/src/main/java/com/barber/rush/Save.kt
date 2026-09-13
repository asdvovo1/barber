package com.barber.rush

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/* Mirrors the original localStorage save (KEY = "bsr_v3", DEF defaults). */
object Save {

	private const val PREF = "bsr_v3"
	private const val FIELD = "state"
	private var prefs: SharedPreferences? = null

	var coins = 150
	var level = 1
	var deco = 1
	var dye = 1
	var vip = 0
	var music = 1
	var sound = 1

	val stars = HashMap<Int, Int>()
	val own = HashMap<String, Int>()
	val up = HashMap<String, Int>()
	val ownDeco = HashMap<Int, Int>()
	val ownDye = HashMap<Int, Int>()

	fun load(ctx: Context) {
		val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
		prefs = p
		defaults()
		val raw = p.getString(FIELD, null) ?: return
		try {
			val o = JSONObject(raw)
			coins = o.optInt("coins", coins)
			level = o.optInt("level", level)
			deco = o.optInt("deco", deco)
			dye = o.optInt("dye", dye)
			vip = o.optInt("vip", vip)
			music = o.optInt("music", music)
			sound = o.optInt("sound", sound)
			Data.lang = o.optString("lang", Data.lang)
			readInt(o.optJSONObject("stars"), stars)
			readStr(o.optJSONObject("own"), own)
			readStr(o.optJSONObject("up"), up)
			readInt(o.optJSONObject("ownDeco"), ownDeco)
			readInt(o.optJSONObject("ownDye"), ownDye)
		} catch (e: Exception) {
			defaults()
		}
	}

	fun defaults() {
		coins = 150
		level = 1
		deco = 1
		dye = 1
		vip = 0
		music = 1
		sound = 1
		stars.clear()
		own.clear()
		up.clear()
		up["sharp"] = 1
		ownDeco.clear()
		ownDeco[1] = 1
		ownDye.clear()
		ownDye[1] = 1
	}

	private fun readInt(o: JSONObject?, into: HashMap<Int, Int>) {
		into.clear()
		if (o == null) return
		val it = o.keys()
		while (it.hasNext()) {
			val k = it.next()
			val n = k.toIntOrNull() ?: continue
			into[n] = o.optInt(k, 0)
		}
	}

	private fun readStr(o: JSONObject?, into: HashMap<String, Int>) {
		into.clear()
		if (o == null) return
		val it = o.keys()
		while (it.hasNext()) {
			val k = it.next()
			into[k] = o.optInt(k, 0)
		}
	}

	fun save() {
		val p = prefs ?: return
		val o = JSONObject()
		o.put("coins", coins)
		o.put("level", level)
		o.put("deco", deco)
		o.put("dye", dye)
		o.put("vip", vip)
		o.put("music", music)
		o.put("sound", sound)
		o.put("lang", Data.lang)
		o.put("stars", jsonOfInt(stars))
		o.put("own", jsonOfStr(own))
		o.put("up", jsonOfStr(up))
		o.put("ownDeco", jsonOfInt(ownDeco))
		o.put("ownDye", jsonOfInt(ownDye))
		p.edit().putString(FIELD, o.toString()).apply()
	}

	private fun jsonOfInt(m: HashMap<Int, Int>): JSONObject {
		val o = JSONObject()
		for ((k, v) in m) o.put(k.toString(), v)
		return o
	}

	private fun jsonOfStr(m: HashMap<String, Int>): JSONObject {
		val o = JSONObject()
		for ((k, v) in m) o.put(k, v)
		return o
	}

	fun owned(id: String): Boolean {
		if (Data.toolById[id]?.free == true) return true
		return (own[id] ?: 0) > 0
	}

	fun upg(id: String): Int = up[id] ?: 0

	fun starsAt(n: Int): Int = stars[n] ?: 0

	fun starsTotal(): Int {
		var s = 0
		for (v in stars.values) s += v
		return s
	}

	fun addCoins(n: Int) {
		coins += n
		if (coins < 0) coins = 0
		save()
	}

	fun spend(n: Int): Boolean {
		if (coins < n) return false
		coins -= n
		save()
		return true
	}
}
