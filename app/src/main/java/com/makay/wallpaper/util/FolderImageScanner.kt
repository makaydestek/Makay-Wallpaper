package com.makay.wallpaper.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.ArrayDeque

/**
 * SAF / dosya yolu klasör taraması.
 * DocumentsContract cursor kullanır (DocumentFile.listFiles büyük klasörlerde takılır / eksik kalır).
 */
object FolderImageScanner {

    private const val TAG = "FolderScan"
    private val scanLock = Any()

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "webp", "bmp", "gif",
        "heic", "heif", "avif", "tif", "tiff", "dng", "raw"
    )

    /**
     * @param onProgress (bulunan, toplam) — toplam 1. geçişte hesaplanır; UI: «1200/7539»
     */
    fun scan(
        context: Context,
        treeUriOrPath: String,
        onProgress: ((found: Int, total: Int) -> Unit)? = null
    ): List<String> {
        if (treeUriOrPath.isBlank()) return emptyList()
        return synchronized(scanLock) {
            runCatching {
                when {
                    treeUriOrPath.startsWith("content:", ignoreCase = true) ->
                        scanTreeUri(context, Uri.parse(treeUriOrPath), onProgress)
                    treeUriOrPath.startsWith("file:", ignoreCase = true) ->
                        scanFilePath(
                            Uri.parse(treeUriOrPath).path ?: return@runCatching emptyList(),
                            onProgress
                        )
                    else -> scanFilePath(treeUriOrPath, onProgress)
                }
            }.onFailure {
                Log.e(TAG, "scan failed: $treeUriOrPath", it)
            }.getOrDefault(emptyList())
        }
    }

    private fun scanTreeUri(
        context: Context,
        treeUri: Uri,
        onProgress: ((Int, Int) -> Unit)?
    ): List<String> {
        // 1) Önce say — Wallpaper Changer tarzı xxxx/toplam
        val total = countTreeImages(context, treeUri)
        onProgress?.invoke(0, total.coerceAtLeast(0))
        if (total == 0) {
            // Cursor başarısız olabilir; DocumentFile yedek
            return scanTreeViaDocumentFile(context, treeUri, onProgress)
        }
        val out = ArrayList<String>(total.coerceAtLeast(16))
        var lastEmit = 0L
        walkTree(context, treeUri) { docUri, mime, name ->
            if (!isImage(mime, name)) return@walkTree
            out.add(docUri)
            val now = System.currentTimeMillis()
            if (onProgress != null && (out.size == total || out.size % 25 == 0 || now - lastEmit > 120)) {
                lastEmit = now
                onProgress(out.size, total)
            }
        }
        onProgress?.invoke(out.size, total.coerceAtLeast(out.size))
        // Cursor eksik kaldıysa DocumentFile ile tamamla
        if (out.size < total * 0.95 || (total > 0 && out.isEmpty())) {
            Log.w(TAG, "cursor incomplete ${out.size}/$total — DocumentFile merge")
            val fallback = scanTreeViaDocumentFile(context, treeUri, null)
            if (fallback.size > out.size) {
                val merged = LinkedHashSet<String>(out.size + fallback.size)
                merged.addAll(out)
                merged.addAll(fallback)
                onProgress?.invoke(merged.size, merged.size)
                return merged.toList()
            }
        }
        return out
    }

    private fun scanTreeViaDocumentFile(
        context: Context,
        treeUri: Uri,
        onProgress: ((Int, Int) -> Unit)?
    ): List<String> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<String>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth < 0) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (child in children) {
                when {
                    child.isDirectory -> walk(child, depth - 1)
                    child.isFile || child.type?.startsWith("image/") == true -> {
                        val name = child.name
                        val mime = child.type
                        if (isImage(mime, name)) {
                            out.add(child.uri.toString())
                            if (onProgress != null && out.size % 25 == 0) {
                                onProgress(out.size, out.size)
                            }
                        }
                    }
                }
            }
        }
        walk(root, 8)
        onProgress?.invoke(out.size, out.size)
        return out
    }

    private fun countTreeImages(context: Context, treeUri: Uri): Int {
        var count = 0
        walkTree(context, treeUri) { _, mime, name ->
            if (isImage(mime, name)) count++
        }
        return count
    }

    /**
     * Ağacı BFS ile gezer; her dosya için callback.
     */
    private inline fun walkTree(
        context: Context,
        treeUri: Uri,
        onFile: (docUri: String, mime: String?, name: String?) -> Unit
    ) {
        val cr = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        var depthGuard = 0
        val maxNodes = 200_000
        while (queue.isNotEmpty() && depthGuard < maxNodes) {
            depthGuard++
            val parentId = queue.removeFirst()
            val childrenUri = runCatching {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            }.getOrNull() ?: continue
            val cursor = runCatching {
                cr.query(childrenUri, projection, null, null, null)
            }.getOrNull() ?: continue
            cursor.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idIdx < 0) return@use
                while (c.moveToNext()) {
                    val docId = c.getString(idIdx) ?: continue
                    val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(docId)
                    } else {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        onFile(docUri.toString(), mime, name)
                    }
                }
            }
        }
    }

    private fun scanFilePath(
        path: String,
        onProgress: ((Int, Int) -> Unit)?
    ): List<String> {
        val root = File(path)
        if (!root.exists()) return emptyList()
        if (root.isFile) {
            return if (isImage(null, root.name)) {
                onProgress?.invoke(1, 1)
                listOf(Uri.fromFile(root).toString())
            } else emptyList()
        }
        val files = root.walkTopDown().maxDepth(10)
            .filter { it.isFile && isImage(null, it.name) }
            .toList()
        val total = files.size
        onProgress?.invoke(0, total)
        val out = ArrayList<String>(total)
        files.forEachIndexed { index, f ->
            out.add(Uri.fromFile(f).toString())
            if (onProgress != null && (index + 1 == total || (index + 1) % 25 == 0)) {
                onProgress(index + 1, total)
            }
        }
        onProgress?.invoke(out.size, total)
        return out
    }

    private fun isImage(mime: String?, name: String?): Boolean {
        if (!mime.isNullOrBlank()) {
            val m = mime.lowercase()
            if (m.startsWith("image/")) return true
            // Bazı OEM'ler HEIC'i farklı gösterir
            if (m.contains("heic") || m.contains("heif")) return true
        }
        return isImageName(name)
    }

    private fun isImageName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isEmpty()) return false
        return ext in IMAGE_EXT
    }
}
