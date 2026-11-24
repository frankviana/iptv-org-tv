package com.knarf.iptvorgtv

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Xml
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

data class Channel(val group: String, val name: String, val url: String, val tvgId: String)
data class EpgProg(val start: Long, val stop: Long, val title: String)

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var recycler: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var epgBanner: TextView
    private lateinit var searchBox: EditText
    private var allChannels = listOf<Channel>()
    private var filtered = listOf<Channel>()
    private var epgMap = mapOf<String, List<EpgProg>>()
    private var currentMode = "country"
    private var selectedGroup = "Brazil"
    private var currentChannel: Channel? = null

    private val epgHost = "https://" + "iptv-org" + ".github.io/epg/guides/"
    private val m3uHost = "https://" + "iptv-org" + ".github.io/iptv/"
    private val sources = mapOf("country" to "${m3uHost}index.country.m3u", "genre" to "${m3uHost}index.category.m3u", "language" to "${m3uHost}index.language.m3u")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#0F172A")) }
        val leftPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.40f); setPadding(14, 14, 14, 14) }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Pais" to "country", "Categoria" to "genre", "Idioma" to "language").forEach { (label, key) ->
            btnRow.addView(Button(this).apply { text = label; textSize = 11f; isFocusable = true; setOnClickListener { currentMode = key; selectedGroup = if (key == "country") "Brazil" else if (key == "language") "Portuguese" else "Music"; loadPlaylist(false) } })
        }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Button(this).apply { text = "Grupo"; textSize = 11f; setOnClickListener { showGroupPicker() } })
        actionRow.addView(Button(this).apply { text = "Player Ext"; textSize = 11f; setOnClickListener { openExternalPlayer() } })
        actionRow.addView(Button(this).apply { text = "Atualizar"; textSize = 11f; setOnClickListener { loadPlaylist(true) } })
        searchBox = EditText(this).apply {
            hint = "Buscar canal..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { applyFilter() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        statusText = TextView(this).apply { setTextColor(Color.parseColor("#FBBF24")); textSize = 12f; text = "Carregando..." }
        recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        leftPane.addView(btnRow); leftPane.addView(actionRow); leftPane.addView(searchBox); leftPane.addView(statusText); leftPane.addView(recycler)
        val rightPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.60f); setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        epgBanner = TextView(this).apply { setBackgroundColor(Color.parseColor("#1E293B")); setTextColor(Color.parseColor("#E2E8F0")); setPadding(16, 10, 16, 10); textSize = 13f; text = "Selecione um canal para assistir (EPG automatico)" }
        rightPane.addView(playerView); rightPane.addView(epgBanner); root.addView(leftPane); root.addView(rightPane); setContentView(root)
        player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        loadPlaylist(false)
    }
    private fun epgUrlForGroup(grp: String): String {
        val g = grp.lowercase()
        return when {
            g.contains("brazil") || g.contains("portuguese") -> "${epgHost}br/mi.tv.epg.xml"
            g.contains("portugal") -> "${epgHost}pt/meo.pt.epg.xml"
            g.contains("spain") || g.contains("spanish") -> "${epgHost}es/movistarplus.es.epg.xml"
            g.contains("argentina") -> "${epgHost}ar/mi.tv.epg.xml"
            g.contains("mexico") -> "${epgHost}mx/gatotv.com.epg.xml"
            else -> "${epgHost}us/tvguide.com.epg.xml"
        }
    }

    private fun loadPlaylist(forceRefresh: Boolean) {
        statusText.text = "Baixando lista ($currentMode)..."
        thread {
            try {
                val cacheFile = File(cacheDir, "iptvorg_$currentMode.m3u")
                if (forceRefresh || !cacheFile.exists() || (System.currentTimeMillis() - cacheFile.lastModified() > 86400_000L)) {
                    val conn = (URL(sources[currentMode]).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 15000 }
                    cacheFile.writeBytes(conn.inputStream.readBytes())
                }
                val parsed = mutableListOf<Channel>()
                var grp = ""; var name = ""; var tid = ""
                cacheFile.forEachLine { line ->
                    when {
                        line.startsWith("#EXTINF:") -> {
                            name = line.substringAfterLast(",", "").trim()
                            grp = if (line.contains("group-title=\"")) line.substringAfter("group-title=\"").substringBefore("\"") else "Geral"
                            tid = if (line.contains("tvg-id=\"")) line.substringAfter("tvg-id=\"").substringBefore("\"").trim() else ""
                        }
                        line.startsWith("http") && name.isNotEmpty() -> { parsed.add(Channel(grp, name, line.trim(), tid)); name = "" }
                    }
                }
                allChannels = parsed
                runOnUiThread {
                    if (allChannels.none { it.group.equals(selectedGroup, true) } && allChannels.isNotEmpty()) selectedGroup = allChannels.first().group
                    applyFilter(); loadEpg(forceRefresh)
                }
            } catch (e: Exception) { runOnUiThread { statusText.text = "Erro: ${e.message}" } }
        }
    }

    private fun loadEpg(forceRefresh: Boolean) {
        val url = epgUrlForGroup(selectedGroup)
        thread {
            try {
                val epgFile = File(cacheDir, "epg_${url.hashCode()}.xml")
                if (forceRefresh || !epgFile.exists() || (System.currentTimeMillis() - epgFile.lastModified() > 21600_000L)) {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 20000 }
                    epgFile.writeBytes(conn.inputStream.readBytes())
                }
                val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                val map = mutableMapOf<String, MutableList<EpgProg>>()
                val now = System.currentTimeMillis()
                val parser = Xml.newPullParser()
                epgFile.inputStream().use { input ->
                    parser.setInput(input, "UTF-8")
                    var event = parser.eventType; var chId = ""; var startMs = 0L; var stopMs = 0L; var title = ""
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "programme" -> { chId = parser.getAttributeValue(null, "channel") ?: ""; startMs = try { fmt.parse(parser.getAttributeValue(null, "start") ?: "")?.time ?: 0L } catch (_: Exception) { 0L }; stopMs = try { fmt.parse(parser.getAttributeValue(null, "stop") ?: "")?.time ?: 0L } catch (_: Exception) { 0L }; title = "" }
                                "title" -> if (chId.isNotEmpty() && stopMs > now - 3600_000L && startMs < now + 43200_000L) title = parser.nextText().trim()
                            }
                        } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                            if (chId.isNotEmpty() && title.isNotEmpty() && stopMs > now) map.getOrPut(chId) { mutableListOf() }.add(EpgProg(startMs, stopMs, title))
                            chId = ""
                        }
                        event = parser.next()
                    }
                }
                map.values.forEach { it.sortBy { p -> p.start } }
                epgMap = map
                runOnUiThread { recycler.adapter?.notifyDataSetChanged(); currentChannel?.let { updateBanner(it) } }
            } catch (_: Exception) { }
        }
    }

    private fun nowNextText(tvgId: String): Pair<String, String> {
        val list = epgMap[tvgId] ?: return "" to ""
        val now = System.currentTimeMillis(); val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cur = list.firstOrNull { now in it.start..it.stop }?.let { "${hhmm.format(it.start)} ${it.title}" } ?: ""
        val nxt = list.firstOrNull { it.start > now }?.let { "${hhmm.format(it.start)} ${it.title}" } ?: ""
        return cur to nxt
    }

    private fun updateBanner(ch: Channel) {
        val (cur, nxt) = nowNextText(ch.tvgId)
        epgBanner.text = when {
            cur.isNotEmpty() && nxt.isNotEmpty() -> "${ch.name}  |  Agora: $cur  |  A Seguir: $nxt"
            cur.isNotEmpty() -> "${ch.name}  |  Agora: $cur"
            nxt.isNotEmpty() -> "${ch.name}  |  A Seguir: $nxt"
            else -> "${ch.name}  |  Sem guia EPG disponivel"
        }
    }

    private fun applyFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        filtered = allChannels.filter { it.group.equals(selectedGroup, true) && (q.isEmpty() || it.name.lowercase().contains(q)) }
        statusText.text = "Grupo: $selectedGroup (${filtered.size} canais)"
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val box = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); isFocusable = true; setOnFocusChangeListener { v, f -> v.setBackgroundColor(if (f) Color.parseColor("#2563EB") else Color.TRANSPARENT) } }
                box.addView(TextView(p.context).apply { textSize = 14f; setTextColor(Color.WHITE) })
                box.addView(TextView(p.context).apply { textSize = 11f; setTextColor(Color.parseColor("#38BDF8")) })
                return object : RecyclerView.ViewHolder(box) {}
            }
            override fun getItemCount() = filtered.size
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val ch = filtered[pos]; val box = h.itemView as LinearLayout
                (box.getChildAt(0) as TextView).text = ch.name
                val (cur, _) = nowNextText(ch.tvgId)
                (box.getChildAt(1) as TextView).text = if (cur.isNotEmpty()) "Agora: $cur" else ""
                box.setOnClickListener { playChannel(ch) }
            }
        }
    }

    private fun showGroupPicker() {
        val groups = allChannels.map { it.group }.distinct().sorted().toTypedArray()
        if (groups.isNotEmpty()) android.app.AlertDialog.Builder(this).setTitle("Selecione ($currentMode)").setItems(groups) { _, w -> selectedGroup = groups[w]; applyFilter(); loadEpg(false) }.show()
    }

    private fun playChannel(ch: Channel) { currentChannel = ch; statusText.text = "Tocando: ${ch.name}"; updateBanner(ch); player?.apply { setMediaItem(MediaItem.fromUri(ch.url)); prepare(); play() } }
    private fun openExternalPlayer() { val u = currentChannel?.url ?: return; player?.pause(); startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(u), "video/*") }, "Abrir com...")) }
    override fun onKeyDown(k: Int, e: KeyEvent?): Boolean { if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { playerView.requestFocus(); return true }; return super.onKeyDown(k, e) }
    override fun onDestroy() { super.onDestroy(); player?.release() }
}
