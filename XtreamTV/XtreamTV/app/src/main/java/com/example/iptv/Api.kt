package com.example.iptv

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Type { LIVE, MOVIE, SERIES }
data class Item(val id: String, val name: String, val icon: String?, val cat: String,
                val type: Type, val ext: String, val rating: Double, val added: Long) {
    val key get() = "${type.name}:$id"
}
data class Episode(val id: String, val title: String, val season: Int, val num: Int, val ext: String)
data class Catalog(val live: List<Item>, val movies: List<Item>, val series: List<Item>,
                   val cats: Map<Type, Map<String, String>>)

private fun JSONObject.s(k: String) = if (isNull(k)) "" else optString(k)
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

class Xtream(host: String, val user: String, val pass: String) {
    val base = host.trim().let { if (it.startsWith("http")) it else "http://$it" }.trimEnd('/')

    private fun get(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        val c = URL("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}$a$extra")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 90000
        c.setRequestProperty("User-Agent", "IPTVPlayer/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun login() {
        val u = JSONObject(get()).optJSONObject("user_info") ?: error("Invalid server response")
        if (u.s("auth") != "1") error("Login failed: check username / password")
        if (u.s("status").let { it.isNotEmpty() && it != "Active" }) error("Account status: ${u.s("status")}")
    }

    private fun cats(action: String): Map<String, String> {
        val a = JSONArray(get(action)); val m = LinkedHashMap<String, String>()
        for (i in 0 until a.length()) a.getJSONObject(i).let { m[it.s("category_id")] = it.s("category_name") }
        return m
    }

    private fun items(type: Type): List<Item> {
        val action = when (type) { Type.LIVE -> "get_live_streams"; Type.MOVIE -> "get_vod_streams"; Type.SERIES -> "get_series" }
        val a = JSONArray(get(action)); val out = ArrayList<Item>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out += Item(
                id = o.s(if (type == Type.SERIES) "series_id" else "stream_id"),
                name = o.s("name"),
                icon = o.s(if (type == Type.SERIES) "cover" else "stream_icon").ifBlank { null },
                cat = o.s("category_id"), type = type,
                ext = o.s("container_extension").ifBlank { "mp4" },
                rating = o.s("rating").toDoubleOrNull() ?: 0.0,
                added = o.s("added").toLongOrNull() ?: o.s("last_modified").toLongOrNull() ?: 0L)
        }
        return out
    }

    fun loadAll(): Catalog {
        login()
        return Catalog(items(Type.LIVE), items(Type.MOVIE), items(Type.SERIES), mapOf(
            Type.LIVE to cats("get_live_categories"),
            Type.MOVIE to cats("get_vod_categories"),
            Type.SERIES to cats("get_series_categories")))
    }

    fun episodes(seriesId: String): List<Episode> {
        val eps = JSONObject(get("get_series_info", "&series_id=$seriesId")).optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        for (k in eps.keys()) {
            val arr = eps.getJSONArray(k)
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                out += Episode(it.s("id"), it.s("title").ifBlank { "Episode" },
                    it.s("season").toIntOrNull() ?: k.toIntOrNull() ?: 1,
                    it.s("episode_num").toIntOrNull() ?: 0, it.s("container_extension").ifBlank { "mp4" })
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.num }))
    }

    // Live: change m3u8 -> ts if your provider prefers it
    fun url(i: Item) = when (i.type) {
        Type.LIVE -> "$base/live/$user/$pass/${i.id}.m3u8"
        Type.MOVIE -> "$base/movie/$user/$pass/${i.id}.${i.ext}"
        Type.SERIES -> ""
    }
    fun url(e: Episode) = "$base/series/$user/$pass/${e.id}.${e.ext}"
}
