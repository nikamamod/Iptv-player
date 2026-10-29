package com.example.iptv

import android.content.Context
import androidx.compose.runtime.*

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("iptv", 0)
    var host: String get() = p.getString("host", "") ?: ""; set(v) = p.edit().putString("host", v).apply()
    var user: String get() = p.getString("user", "") ?: ""; set(v) = p.edit().putString("user", v).apply()
    var pass: String get() = p.getString("pass", "") ?: ""; set(v) = p.edit().putString("pass", v).apply()
    var external by mutableStateOf(p.getBoolean("ext", false))
    fun setExternal(v: Boolean) { external = v; p.edit().putBoolean("ext", v).apply() }
    val favs = mutableStateListOf<String>().apply { addAll(p.getStringSet("favs", emptySet()) ?: emptySet()) }
    fun toggle(k: String) {
        if (!favs.remove(k)) favs.add(k)
        p.edit().putStringSet("favs", favs.toSet()).apply()
    }
    fun logout() { p.edit().remove("host").remove("user").remove("pass").apply() }
}
