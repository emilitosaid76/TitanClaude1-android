package com.titan.agent.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Lee archivos elegidos con el selector del sistema para adjuntarlos al chat.
 * Mantiene el mismo formato y limite que la interfaz web, para que el modelo
 * reciba exactamente lo mismo venga de donde venga.
 */
object FileReader {

    private const val MAX_BYTES = 10 * 1024 * 1024  // 10 MB, igual que la web

    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

    /** Nombre visible del documento; si el proveedor no lo da, se usa el ultimo segmento del Uri. */
    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
            }
        }
        return -1
    }

    /**
     * Devuelve el archivo leido, o un [Result] fallido con un motivo legible
     * (demasiado grande, binario, ilegible) para poder mostrarselo al usuario.
     */
    fun read(context: Context, uri: Uri): Result<AttachedFile> {
        val name = displayName(context, uri)
        val size = sizeOf(context, uri)

        if (size > MAX_BYTES) {
            val mb = size / 1024.0 / 1024.0
            return Result.failure(Exception("$name es muy grande (${"%.1f".format(mb)} MB, max 10 MB)"))
        }

        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in IMAGE_EXT) {
            // Los modelos actuales no reciben imagenes por esta via: se anota la
            // referencia igual que hace la web, sin cargar los bytes.
            return Result.success(AttachedFile(name, "", isImage = true))
        }

        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("No se pudo abrir $name")
            if (bytes.size > MAX_BYTES) throw Exception("$name es muy grande (max 10 MB)")
            // Un archivo binario inyectado como texto solo gasta contexto y confunde al modelo.
            if (bytes.take(8000).any { it == 0.toByte() }) {
                throw Exception("$name parece binario, no se puede adjuntar como texto")
            }
            AttachedFile(name, String(bytes, Charsets.UTF_8))
        }
    }

    /** Construye el texto final que se envia al modelo, con los adjuntos incrustados. */
    fun buildContent(text: String, files: List<AttachedFile>): String {
        if (files.isEmpty()) return text
        val sb = StringBuilder(text)
        for (f in files) {
            if (f.isImage) {
                sb.append("\n\n[Imagen adjunta: ${f.name}]")
            } else {
                sb.append("\n\n--- Archivo: ${f.name} ---\n${f.content}\n--- Fin: ${f.name} ---")
            }
        }
        return sb.toString()
    }
}
