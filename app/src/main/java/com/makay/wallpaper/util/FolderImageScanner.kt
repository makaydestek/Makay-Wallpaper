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

    data class ScanResult(val images: List<String>, val completed: Boolean)

    private const val TAG = "FolderScan"
    private val scanLock = Any()

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "webp", "bmp", "gif",
        "heic", "heif", "avif", "tif", "tiff", "dng", "raw"
    )

    /** Progress reports (found, -1) while the total is unknown, then (total, total). */
    fun scan(
        context: Context,
        treeUriOrPath: String,
        onProgress: ((found: Int, total: Int) -> Unit)? = null
    ): List<String> = scanWithStatus(context, treeUriOrPath, onProgress).images

    /** Distinguishes a successfully scanned empty folder from an unexpected scan failure. */
    fun scanWithStatus(
        context: Context,
        treeUriOrPath: String,
        onProgress: ((found: Int, total: Int) -> Unit)? = null
    ): ScanResult {
        if (treeUriOrPath.isBlank()) return ScanResult(emptyList(), completed = false)
        return synchronized(scanLock) {
            try {
                val images = when {
                    treeUriOrPath.startsWith("content:", ignoreCase = true) ->
                        scanTreeUri(context, Uri.parse(treeUriOrPath), onProgress)
                    treeUriOrPath.startsWith("file:", ignoreCase = true) -> {
                        val path = Uri.parse(treeUriOrPath).path
                            ?: return@synchronized ScanResult(emptyList(), completed = false)
                        scanFilePath(path, onProgress)
                    }
                    else -> scanFilePath(treeUriOrPath, onProgress)
                }
                ScanResult(images, completed = true)
            } catch (e: Exception) {
                Log.e(TAG, "scan failed: $treeUriOrPath", e)
                ScanResult(emptyList(), completed = false)
            }
        }
    }

    private fun scanTreeUri(
        context: Context,
        treeUri: Uri,
        onProgress: ((Int, Int) -> Unit)?
    ): List<String> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.exists() || !root.canRead()) {
            throw SecurityException("Folder permission is missing or the folder is unavailable")
        }
        val out = ArrayList<String>()
        var lastEmit = 0L
        try {
            walkTree(context, treeUri) { docUri, mime, name ->
                if (!isImage(mime, name)) return@walkTree
                out.add(docUri)
                val now = System.currentTimeMillis()
                if (onProgress != null && (out.size % 25 == 0 || now - lastEmit > 120)) {
                    lastEmit = now
                    onProgress(out.size, -1)
                }
            }
        } catch (e: Exception) {
            val fallback = scanTreeViaDocumentFile(context, treeUri, onProgress)
            if (fallback.isEmpty()) throw e
            onProgress?.invoke(fallback.size, fallback.size)
            return fallback
        }
        onProgress?.invoke(out.size, out.size)
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
            if (depth < 0) throw IllegalStateException("Folder exceeds fallback scan depth")
            val children = dir.listFiles()
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
            ?: throw SecurityException("Unable to read selected folder")
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
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = cr.query(childrenUri, projection, null, null, null)
                ?: throw IllegalStateException("Folder provider returned no listing")
            cursor.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idIdx < 0) throw IllegalStateException("Folder listing has no document ID")
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
        if (queue.isNotEmpty()) throw IllegalStateException("Folder exceeds scan limit")
    }

    private fun scanFilePath(
        path: String,
        onProgress: ((Int, Int) -> Unit)?
    ): List<String> {
        val root = File(path)
        if (!root.exists()) return emptyList()
        if (!root.canRead()) throw SecurityException("Folder is not readable")
        if (root.isFile) {
            return if (isImage(null, root.name)) {
                onProgress?.invoke(1, 1)
                listOf(Uri.fromFile(root).toString())
            } else emptyList()
        }
        val out = ArrayList<String>()
        root.walkTopDown().maxDepth(10).onFail { _, error -> throw error }.forEach { file ->
            if (file.isFile && isImage(null, file.name)) {
                out.add(Uri.fromFile(file).toString())
                if (onProgress != null && out.size % 25 == 0) {
                    onProgress(out.size, -1)
                }
            }
        }
        onProgress?.invoke(out.size, out.size)
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
