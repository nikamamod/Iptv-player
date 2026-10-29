@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Red = Color(0xFFE50914)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val store = Store(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Color(0xFF0B0B0B),
                surface = Color(0xFF141414))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(store) }
            }
        }
    }
}

fun play(ctx: Context, store: Store, url: String, title: String) {
    if (store.external) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)
        ctx.startActivity(Intent.createChooser(i, "Play with"))
    } else ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra("url", url).putExtra("title", title))
}

@Composable
fun App(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var logged by remember { mutableStateOf(store.host.isNotBlank()) }
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(1) }
    var series by remember { mutableStateOf<Item?>(null) }

    fun load() = scope.launch {
        loading = true; error = null
        try { catalog = withContext(Dispatchers.IO) { Xtream(store.host, store.user, store.pass).loadAll() } }
        catch (e: Exception) { error = e.message ?: "Failed to load" }
        loading = false
    }
    LaunchedEffect(logged) { if (logged) load() }

    if (!logged) { Login(store) { logged = true }; return }
    val xt = remember(logged) { Xtream(store.host, store.user, store.pass) }
    val open: (Item) -> Unit = { if (it.type == Type.SERIES) series = it else play(ctx, store, xt.url(it), it.name) }

    Scaffold(bottomBar = {
        NavigationBar {
            listOf("📺" to "Live", "🎬" to "Movies", "🍿" to "Series", "♥" to "Favorites", "⚙" to "Settings")
                .forEachIndexed { i, (ic, l) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic) }, label = { Text(l, fontSize = 11.sp) })
                }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val c = catalog
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(error!!, color = Color.Red); Button(onClick = { load() }) { Text("Retry") }
                    TextButton(onClick = { store.logout(); logged = false }) { Text("Change login") }
                }
                c == null -> {}
                tab == 0 -> Browse(Type.LIVE, c.live, c.cats[Type.LIVE]!!, store, false, open)
                tab == 1 -> Browse(Type.MOVIE, c.movies, c.cats[Type.MOVIE]!!, store, false, open)
                tab == 2 -> Browse(Type.SERIES, c.series, c.cats[Type.SERIES]!!, store, false, open)
                tab == 3 -> Browse(null, c.live + c.movies + c.series, emptyMap(), store, true, open)
                else -> Settings(store, { load() }) { logged = false; catalog = null }
            }
        }
    }
    series?.let { s ->
        SeriesDialog(s, xt, { e -> play(ctx, store, xt.url(e), "${s.name} - ${e.title}") }) { series = null }
    }
}

@Composable
fun Login(store: Store, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(store.host) }
    var user by remember { mutableStateOf(store.user) }
    var pass by remember { mutableStateOf(store.pass) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("XTREAM TV", color = Red, fontSize = 32.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(host, { host = it }, label = { Text("Server URL (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy && host.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                busy = true; err = null
                try {
                    withContext(Dispatchers.IO) { Xtream(host, user, pass).login() }
                    store.host = host; store.user = user; store.pass = pass; onDone()
                } catch (e: Exception) { err = e.message ?: "Connection failed" }
                busy = false
            }
        }) { Text(if (busy) "Connecting…" else "Sign in") }
    }
}

enum class Sort(val label: String) { DEFAULT("Default"), AZ("A–Z"), ZA("Z–A"), NEW("Recently added"), RATING("Top rated") }

@Composable
fun Browse(type: Type?, items: List<Item>, cats: Map<String, String>, store: Store,
           favTab: Boolean, onOpen: (Item) -> Unit) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(Sort.DEFAULT) }
    var onlyFav by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val favs = store.favs
    val list = remember(items, q, cat, sort, onlyFav, favs.size) {
        items.filter {
            (!favTab && !onlyFav || it.key in favs) &&
            (cat == null || it.cat == cat) && (q.isBlank() || it.name.contains(q, true))
        }.let {
            when (sort) {
                Sort.DEFAULT -> it; Sort.AZ -> it.sortedBy { x -> x.name.lowercase() }
                Sort.ZA -> it.sortedByDescending { x -> x.name.lowercase() }
                Sort.NEW -> it.sortedByDescending { x -> x.added }; Sort.RATING -> it.sortedByDescending { x -> x.rating }
            }
        }
    }
    val grouped = remember(items) { items.groupBy { it.cat } }
    val rows = type != null && type != Type.LIVE && cat == null && q.isBlank() && !onlyFav && sort == Sort.DEFAULT

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search") },
            modifier = Modifier.fillMaxWidth().padding(8.dp))
        if (!favTab) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { menu = true }) { Text("Sort: ${sort.label}") }
                    DropdownMenu(menu, { menu = false }) {
                        Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; menu = false }) }
                    }
                }
                FilterChip(onlyFav, { onlyFav = !onlyFav }, label = { Text("♥ Favorites") })
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(cat == null, { cat = null }, label = { Text("All") }) }
                items(cats.entries.toList()) { e -> FilterChip(cat == e.key, { cat = e.key }, label = { Text(e.value) }) }
            }
        }
        if (rows) {
            LazyColumn(Modifier.fillMaxSize()) {
                val mine = items.filter { it.key in favs }
                if (mine.isNotEmpty()) item { PosterRow("My List", mine, store, onOpen) }
                cats.forEach { (id, name) ->
                    val g = grouped[id]
                    if (!g.isNullOrEmpty()) item(key = id) { PosterRow(name, g.take(40), store, onOpen) }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(if (type == Type.LIVE) 150.dp else 110.dp),
                contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.key }) { Poster(it, store, onOpen, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
fun PosterRow(title: String, items: List<Item>, store: Store, onOpen: (Item) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.key }) { Poster(it, store, onOpen, Modifier.width(120.dp)) }
        }
    }
}

@Composable
fun Poster(item: Item, store: Store, onOpen: (Item) -> Unit, modifier: Modifier) {
    val live = item.type == Type.LIVE
    val fav = item.key in store.favs
    Column(modifier.clickable { onOpen(item) }) {
        Box {
            AsyncImage(item.icon, item.name,
                contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(if (live) 1.6f else 0.67f)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xFF222222)))
            Text(if (fav) "♥" else "♡", color = if (fav) Red else Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { store.toggle(item.key) })
        }
        Text(item.name, maxLines = 2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SeriesDialog(item: Item, xt: Xtream, onPlay: (Episode) -> Unit, onClose: () -> Unit) {
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    LaunchedEffect(item) {
        eps = try { withContext(Dispatchers.IO) { xt.episodes(item.id) } } catch (e: Exception) { emptyList() }
    }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxHeight(0.8f)) {
            Column(Modifier.padding(12.dp)) {
                Text(item.name, fontSize = 18.sp)
                val e = eps
                if (e == null) CircularProgressIndicator(color = Red)
                else if (e.isEmpty()) Text("No episodes found", modifier = Modifier.padding(top = 12.dp))
                else LazyColumn {
                    e.groupBy { it.season }.forEach { (s, l) ->
                        item { Text("Season $s", color = Red, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                        items(l) { ep ->
                            Text("E${ep.num}  ${ep.title}", modifier = Modifier.fillMaxWidth()
                                .clickable { onPlay(ep) }.padding(vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Settings(store: Store, onRefresh: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Server: ${store.host}"); Text("User: ${store.user}")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(store.external, { store.setExternal(it) })
            Text("  Use external player (VLC, MX Player…)")
        }
        Text("Off = built-in Media3 player", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) { Text("Refresh playlist") }
        TextButton(onClick = { store.logout(); onLogout() }) { Text("Log out") }
    }
}
