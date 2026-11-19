package com.kanka.videoindir

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var btnDownload: Button
    private lateinit var btnUpdate: Button
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Video İndir"
            textSize = 26f
        })
        input = EditText(this).apply {
            hint = "TikTok / Instagram / YouTube linki"
            setSingleLine(false)
            minLines = 2
        }
        root.addView(input)

        val btnPaste = Button(this).apply { text = "Panodan yapıştır" }
        btnPaste.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!t.isNullOrBlank()) input.setText(t)
        }
        root.addView(btnPaste)

        btnDownload = Button(this).apply { text = "İndir" }
        btnDownload.setOnClickListener { startDownload() }
        root.addView(btnDownload)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(bar)

        status = TextView(this).apply { text = "Başlatılıyor..." }
        root.addView(status)

        btnUpdate = Button(this).apply { text = "yt-dlp güncelle (çalışmazsa bas)" }
        btnUpdate.setOnClickListener { updateLib() }
        root.addView(btnUpdate)

        setContentView(root)

        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                ready = true
                ui("Hazır. Link yapıştır ve İndir'e bas.")
            } catch (e: Exception) {
                ui("Başlatma hatası: ${e.message}")
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { input.setText(it) }
        }
    }

    private fun ui(msg: String) = runOnUiThread { status.text = msg }

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    private fun startDownload() {
        val url = extractUrl(input.text.toString())
        if (url == null) { ui("Geçerli link bulunamadı"); return }
        if (!ready) { ui("Henüz hazır değil, biraz bekle"); return }
        btnDownload.isEnabled = false
        bar.progress = 0
        ui("Başlıyor...")
        thread {
            try {
                val tmp = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
                val req = YoutubeDLRequest(url)
                req.addOption("-o", tmp.absolutePath + "/%(id)s.%(ext)s")
                req.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b")
                req.addOption("--merge-output-format", "mp4")
                req.addOption("--no-playlist")
                YoutubeDL.getInstance().execute(req, null) { p, _, _ ->
                    val pr = p.toInt().coerceIn(0, 100)
                    runOnUiThread { bar.progress = pr; status.text = "İndiriliyor %$pr" }
                }
                val f = tmp.listFiles()?.firstOrNull() ?: throw Exception("Dosya bulunamadı")
                saveToGallery(f)
                ui("Bitti! Galeri > Movies > VideoIndir klasöründe.")
            } catch (e: Exception) {
                ui("Hata: ${e.message}")
            } finally {
                runOnUiThread { btnDownload.isEnabled = true }
            }
        }
    }

    private fun saveToGallery(f: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoIndir")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Galeriye kaydedilemedi")
        contentResolver.openOutputStream(uri)!!.use { out ->
            f.inputStream().use { it.copyTo(out) }
        }
        f.delete()
    }

    private fun updateLib() {
        if (!ready) { ui("Henüz hazır değil"); return }
        btnUpdate.isEnabled = false
        ui("Güncelleniyor...")
        thread {
            try {
                val r = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                ui("Güncelleme: $r")
            } catch (e: Exception) {
                ui("Güncelleme hatası: ${e.message}")
            } finally {
                runOnUiThread { btnUpdate.isEnabled = true }
            }
        }
    }
}
