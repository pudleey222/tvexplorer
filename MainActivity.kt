package com.exemplo.tvfiles

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import java.io.File

class Entry(val label: String, val file: File?, val volume: StorageVolume? = null)

class MainActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var title: TextView
    private var current: File? = null
    private var entries: List<Entry> = emptyList()
    private var roots: List<File> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        title = TextView(this).apply { textSize = 28f; setPadding(40, 30, 40, 10); setTextColor(Color.WHITE) }
        list = ListView(this).apply {
            setPadding(40, 10, 40, 10)
            selector = ColorDrawable(0xFF1E88E5.toInt())
            divider = null
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            addView(title)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(TextView(context).apply {
                text = "OK: abrir  |  Segurar OK: opções (apagar/formatar)  |  Voltar: pasta acima"
                textSize = 16f; setTextColor(Color.GRAY); setPadding(40, 10, 40, 20)
            })
        }
        setContentView(root)

        list.setOnItemClickListener { _, _, i, _ -> open(entries[i]) }
        list.setOnItemLongClickListener { _, _, i, _ -> options(entries[i]); true }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        } else show(null)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) show(null)
        else Toast.makeText(this, "Sem permissão de armazenamento", Toast.LENGTH_LONG).show()
    }

    private fun volumeDir(v: StorageVolume): File? {
        if (v.isPrimary) return Environment.getExternalStorageDirectory()
        return try {
            File(v.javaClass.getMethod("getPath").invoke(v) as String)
        } catch (e: Exception) {
            v.uuid?.let { File("/storage/$it") }
        }
    }

    private fun show(dir: File?) {
        current = dir
        if (dir == null) {
            val sm = getSystemService(StorageManager::class.java)
            val list2 = mutableListOf<Entry>()
            for (v in sm.storageVolumes) {
                val d = volumeDir(v) ?: continue
                list2.add(Entry("${v.getDescription(this)}  [${fsType(d)}]  ${if (v.isRemovable) "(USB/SD)" else ""}", d, v))
            }
            entries = list2
            roots = list2.mapNotNull { it.file }
            title.text = "Dispositivos"
        } else {
            val files = (dir.listFiles() ?: emptyArray())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            entries = files.map {
                Entry(if (it.isDirectory) "▸ ${it.name}" else "   ${it.name}   (${size(it.length())})", it)
            }
            title.text = dir.path
        }
        list.adapter = object : ArrayAdapter<Entry>(this, 0, entries) {
            override fun getView(p: Int, v: android.view.View?, g: ViewGroup): android.view.View =
                TextView(context).apply {
                    text = entries[p].label; textSize = 24f; setTextColor(Color.WHITE)
                    setPadding(20, 22, 20, 22); gravity = Gravity.START
                }
        }
        if (entries.isEmpty()) Toast.makeText(this, "Vazio", Toast.LENGTH_SHORT).show()
    }

    private fun open(e: Entry) {
        val f = e.file ?: return
        if (f.isDirectory) { show(f); return }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), mime))
        } catch (x: Exception) {
            Toast.makeText(this, "Nenhum app para abrir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun options(e: Entry) {
        val v = e.volume
        if (v != null) {
            if (!v.isRemovable) return
            AlertDialog.Builder(this).setTitle(e.label)
                .setItems(arrayOf("Formatar", "Cancelar")) { _, w -> if (w == 0) confirmFormat(v) }.show()
        } else {
            val f = e.file ?: return
            AlertDialog.Builder(this).setTitle(f.name)
                .setItems(arrayOf("Apagar", "Cancelar")) { _, w ->
                    if (w == 0) AlertDialog.Builder(this).setTitle("Apagar ${f.name}?")
                        .setPositiveButton("Sim") { _, _ ->
                            val ok = f.deleteRecursively()
                            Toast.makeText(this, if (ok) "Apagado" else "Falhou", Toast.LENGTH_SHORT).show()
                            show(current)
                        }.setNegativeButton("Não", null).show()
                }.show()
        }
    }

    private fun confirmFormat(v: StorageVolume) {
        AlertDialog.Builder(this).setTitle("FORMATAR? Tudo será apagado!")
            .setPositiveButton("Formatar") { _, _ -> format(v) }
            .setNegativeButton("Cancelar", null).show()
    }

    // Formata via root (sm partition). Sem root, abre a tela de armazenamento do sistema.
    private fun format(v: StorageVolume) {
        val disk = try {
            "disk:" + (v.javaClass.getMethod("getId").invoke(v) as String).removePrefix("public:")
        } catch (e: Exception) { "" }
        Thread {
            val out = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "sm partition $disk public"))
                p.waitFor(); p.exitValue()
            } catch (e: Exception) { -1 }
            runOnUiThread {
                if (disk.isNotEmpty() && out == 0) { Toast.makeText(this, "Formatado", Toast.LENGTH_LONG).show(); show(null) }
                else {
                    Toast.makeText(this, "Sem root: abrindo configurações de armazenamento", Toast.LENGTH_LONG).show()
                    try { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) } catch (e: Exception) {}
                }
            }
        }.start()
    }

    private fun fsType(d: File): String = try {
        val id = d.name
        val t = File("/proc/mounts").readLines().firstOrNull { it.contains(id) || it.contains(d.path) }
            ?.split(" ")?.get(2) ?: "?"
        if (t == "fuseblk") "ntfs" else t
    } catch (e: Exception) { "?" }

    private fun size(b: Long): String = when {
        b > 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
        b > 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
        else -> "${b / 1024} KB"
    }

    @Deprecated("ok")
    override fun onBackPressed() {
        val c = current
        if (c == null) { super.onBackPressed(); return }
        show(if (roots.any { it.path == c.path }) null else c.parentFile)
    }
}
