package com.titan.agent.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ApiClient(private var baseUrl: String) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Las tareas agenticas medidas tardan 87-148s, y cargar un modelo en VRAM
        // puede anadir otro minuto antes del primer token. 120s se quedaba corto.
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/models").build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful.also { resp.close() }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/models").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            resp.close()
            val obj = JsonParser.parseString(body).asJsonObject
            val models = obj.getAsJsonArray("models") ?: return@withContext emptyList()
            models.map { OllamaModel(it.asJsonObject.get("name").asString) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Estado de la GPU del servidor. Devuelve null si no hay nvidia-smi o falla la conexion. */
    suspend fun getGpu(): GpuStatus? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/gpu").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext null
            val ok = resp.isSuccessful
            resp.close()
            if (!ok) return@withContext null
            val o = JsonParser.parseString(body).asJsonObject
            if (o.has("error")) return@withContext null
            GpuStatus(
                name = o.get("name")?.asString ?: "GPU",
                load = o.get("gpuLoad")?.asInt ?: 0,
                vramUsed = o.get("vramUsed")?.asInt ?: 0,
                vramTotal = o.get("vramTotal")?.asInt ?: 0,
                temp = o.get("temp")?.asInt ?: 0,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Reinicia el servicio Ollama en el servidor. Devuelve null si fue bien, o el error. */
    suspend fun restartOllama(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/ollama/restart")
                .post(ByteArray(0).toRequestBody(json))
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val obj = JsonParser.parseString(body).asJsonObject
            if (obj.get("ok")?.asBoolean == true) null
            else obj.get("error")?.asString ?: "Error desconocido"
        } catch (e: Exception) {
            e.localizedMessage ?: "Error de conexion"
        }
    }

    suspend fun syncSshConnections(connections: List<SshConnection>) = withContext(Dispatchers.IO) {
        try {
            val payload = gson.toJson(mapOf("connections" to connections))
            val req = Request.Builder()
                .url("$baseUrl/api/ssh/save")
                .post(payload.toRequestBody(json))
                .build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    suspend fun streamAgent(
        model: String,
        messages: List<ChatMessage>,
        sshConnections: List<SshConnection>,
        onEvent: (StreamEvent) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
        try {
            val payload = gson.toJson(mapOf(
                "model" to model,
                "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
                "sshConnections" to sshConnections,
            ))
            val req = Request.Builder()
                .url("$baseUrl/api/agent")
                .post(payload.toRequestBody(json))
                .build()

            val resp = client.newCall(req).execute()
            val reader = BufferedReader(InputStreamReader(resp.body?.byteStream() ?: return@withContext))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ")
                if (data == "[DONE]") {
                    withContext(Dispatchers.Main) { onEvent(StreamEvent.Done) }
                    continue
                }
                try {
                    val obj = JsonParser.parseString(data).asJsonObject
                    val type = obj.get("type")?.asString ?: continue
                    val event: StreamEvent? = when (type) {
                        "text" -> StreamEvent.Text(obj.get("content").asString)
                        "thinking" -> StreamEvent.Thinking(obj.get("content").asString)
                        "exec_start" -> StreamEvent.ExecStart(
                            obj.get("host").asString,
                            obj.get("command").asString
                        )
                        "exec_result" -> StreamEvent.ExecResult(
                            obj.get("host").asString,
                            obj.get("command").asString,
                            obj.get("output").asString
                        )
                        "exec_error" -> StreamEvent.ExecError(
                            obj.get("host").asString,
                            obj.get("error")?.asString ?: "Error desconocido"
                        )
                        "error" -> StreamEvent.Error(obj.get("text")?.asString ?: "Error")
                        else -> null
                    }
                    if (event != null) {
                        withContext(Dispatchers.Main) { onEvent(event) }
                    }
                } catch (_: Exception) {}
            }
            reader.close()
            resp.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onEvent(StreamEvent.Error("Error de conexion: ${e.localizedMessage}"))
            }
        }
        }
    }
}
