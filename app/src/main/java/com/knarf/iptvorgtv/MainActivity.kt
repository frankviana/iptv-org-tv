package com.knarf.iptvorgtv

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val group: String, val name: String, val url: String)

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var recycler: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var searchBox: EditText
    private var allChannels = listOf<Channel>()
    private var filtered = listOf<Channel>()
    private var currentMode = "country"
    private var selectedGroup = "Brazil"
    private var currentUrl = ""

    private val sources = mapOf(
        "country" to "https://github.io",
        "genre" to "https://github.io",
        "language" to "https://github.io"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        val leftPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f)
            setPadding(16, 16, 16, 16)
        }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Pais" to "country", "Categoria" to "genre", "Idioma" to "language").forEach { (label, key) ->
            btnRow.addView(Button(this).apply {
                text = label; textSize = 12f; isFocusable = true
                setOnClickListener {
                    currentMode = key
                    selectedGroup = if (key == "country") "Brazil" else if (key == "language") "Portuguese" else "Music"
                    loadPlaylist(false)
                }
            })
        }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Button(this).apply { text = "Grupo"; textSize = 12f; setOnClickListener { showGroupPicker() } })
        actionRow.addView(Button(this).apply { text = "Player Ext"; textSize = 12f; setOnClickListener { openExternalPlayer() } })
        actionRow.addView(Button(this).apply { text = "Atualizar"; textSize = 12f; setOnClickListener { loadPlaylist(true) } })

        searchBox = EditText(this).apply {
            hint = "Buscar canal..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { applyFilter() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        statusText = TextView(this).apply { setTextColor(Color.parseColor("#FBBF24")); textSize = 12f; text = "Carregando..." }
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        leftPane.addView(btnRow); leftPane.addView(actionRow); leftPane.addView(searchBox); leftPane.addView(statusText); leftPane.addView(recycler)
        playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(leftPane); root.addView(playerView)
        setContentView(root)
        player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        loadPlaylist(false)
    }

    private fun loadPlaylist(forceRefresh: Boolean) {
        statusText.text = "Baixando lista ($currentMode)..."
        thread {
            try {
                val cacheFile = File(cacheDir, "iptvorg_$currentMode.m3u")
                val expired = !cacheFile.exists() || (System.currentTimeMillis() - cacheFile.lastModified() > 86400_000L)
                if (forceRefresh || expired) {
                    val conn = (URL(sources[currentMode]).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000; readTimeout = 15000
                    }
                    cacheFile.writeBytes(conn.inputStream.readBytes())
                }
                val parsed = mutableListOf<Channel>()
                var grp = ""; var name = ""
                cacheFile.forEachLine { line ->
                    when {
                        line.startsWith("#EXTINF:") -> {
                            name = line.substringAfterLast(",", "").trim()
                            grp = if (line.contains("group-title=\"")) line.substringAfter("group-title=\"").substringBefore("\"") else "Geral"
                        }
                        line.startsWith("http") && name.isNotEmpty() -> {
                            parsed.add(Channel(grp, name, line.trim())); name = ""
                        }
                    }
                }
                allChannels = parsed
                runOnUiThread {
                    if (allChannels.none { it.group.equals(selectedGroup, true) } && allChannels.isNotEmpty()) {
                        selectedGroup = allChannels.first().group
                    }
                    applyFilter()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Erro: ${e.message}" }
            }
        }
    }

    private fun applyFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        filtered = allChannels.filter {
            it.group.equals(selectedGroup, ignoreCase = true) && (q.isEmpty() || it.name.lowercase().contains(q))
        }
        statusText.text = "Grupo: $selectedGroup (${filtered.size} canais)"
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(12, 14, 12, 14); textSize = 15f; setTextColor(Color.WHITE); isFocusable = true
                    setOnFocusChangeListener { v, hasFocus -> v.setBackgroundColor(if (hasFocus) Color.parseColor("#2563EB") else Color.TRANSPARENT) }
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun getItemCount() = filtered.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = filtered[position]
                (holder.itemView as TextView).apply { text = ch.name; setOnClickListener { playChannel(ch) } }
            }
        }
    }

    private fun showGroupPicker() {
        val groups = allChannels.map { it.group }.distinct().sorted().toTypedArray()
        if (groups.isEmpty()) return
        android.app.AlertDialog.Builder(this).setTitle("Selecione ($currentMode)").setItems(groups) { _, w ->
            selectedGroup = groups[w]; applyFilter()
        }.show()
    }

    private fun playChannel(ch: Channel) {
        currentUrl = ch.url; statusText.text = "Tocando: ${ch.name}"
        player?.apply { setMediaItem(MediaItem.fromUri(ch.url)); prepare(); play() }
    }

    private fun openExternalPlayer() {
        if (currentUrl.isEmpty()) return
        player?.pause()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(currentUrl), "video/*") }, "Abrir com..."))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { playerView.requestFocus(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { super.onDestroy(); player?.release() }
}
