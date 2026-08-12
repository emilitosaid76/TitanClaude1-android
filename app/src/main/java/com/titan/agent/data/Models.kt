package com.titan.agent.data

data class ChatMessage(
    val role: String,
    val content: String,
)

data class Chat(
    val id: String = System.currentTimeMillis().toString(),
    var title: String = "Nuevo chat",
    val messages: MutableList<ChatMessage> = mutableListOf(),
)

data class SshConnection(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String,
)

data class OllamaModel(
    val name: String,
)

/** Estado de la GPU del servidor, tal como lo reporta nvidia-smi. */
data class GpuStatus(
    val name: String,
    val load: Int,
    val vramUsed: Int,
    val vramTotal: Int,
    val temp: Int,
) {
    val vramPercent: Int get() = if (vramTotal > 0) vramUsed * 100 / vramTotal else 0
    val vramUsedGb: Double get() = vramUsed / 1024.0
    val vramTotalGb: Double get() = vramTotal / 1024.0
}

/** Archivo adjuntado al mensaje. Su contenido se inyecta en el texto que ve el modelo. */
data class AttachedFile(
    val name: String,
    val content: String,
    val isImage: Boolean = false,
)

sealed class StreamEvent {
    data class Text(val content: String) : StreamEvent()
    // Los modelos de razonamiento (gemma4) emiten su analisis aparte del contenido.
    // Sin esto el usuario ve varios segundos de silencio mientras el modelo piensa.
    data class Thinking(val content: String) : StreamEvent()
    data class ExecStart(val host: String, val command: String) : StreamEvent()
    data class ExecResult(val host: String, val command: String, val output: String) : StreamEvent()
    data class ExecError(val host: String, val error: String) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

data class ExecBlock(
    val host: String,
    val command: String,
    var output: String? = null,
    var error: String? = null,
    var running: Boolean = true,
)

data class Metrics(
    val elapsedMs: Long = 0,
    val tokens: Int = 0,
    val speed: Double = 0.0,
    val execCount: Int = 0,
)
