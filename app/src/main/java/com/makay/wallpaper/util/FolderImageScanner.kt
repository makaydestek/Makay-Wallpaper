package com.makay.wallpaper.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FolderImageScanner {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    /**
     * Ağaç URI (SAF) veya dosya yolu verir; görüntü content/file URI listesi döner.
     */
    fun scan(context: Context, treeUriOrPath: String): List<String> {
        if (treeUriOrPath.isBlank()) return emptyList()
        return runCatching {
            when {
                treeUriOrPath.startsWith("content:", ignoreCase = true) ->
                    scanTreeUri(context, Uri.parse(treeUriOrPath))
                treeUriOrPath.startsWith("file:", ignoreCase = true) ->
                    scanFilePath(Uri.parse(treeUriOrPath).path ?: return emptyList())
                else -> scanFilePath(treeUriOrPath)
            }
        }.getOrDefault(emptyList())
    }

    private fun scanTreeUri(context: Context, treeUri: Uri): List<String> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<String>()
        collectDocumentImages(root, out, maxDepth = 4)
        return out
    }

    private fun collectDocumentImages(dir: DocumentFile, out: MutableList<String>, maxDepth: Int) {
        if (maxDepth < 0) return
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collectDocumentImages(child, out, maxDepth - 1)
                child.isFile && isImageName(child.name) -> {
                    child.uri.toString().let { out.add(it) }
                }
            }
        }
    }

    private fun scanFilePath(path: String): List<String> {
        val root = File(path)
        if (!root.exists()) return emptyList()
        val out = ArrayList<String>()
        if (root.isFile) {
            if (isImageName(root.name)) out.add(Uri.fromFile(root).toString())
            return out
        }
        root.walkTopDown().maxDepth(5).forEach { f ->
            if (f.isFile && isImageName(f.name)) {
                out.add(Uri.fromFile(f).toString())
            }
        }
        return out
    }

    private fun isImageName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }
}
