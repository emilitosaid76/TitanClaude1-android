package com.titan.agent

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.titan.agent.data.*
import com.titan.agent.ui.screens.ChatScreen
import com.titan.agent.ui.screens.ConnectScreen
import com.titan.agent.ui.theme.TitanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TitanTheme {
                TitanApp(this)
            }
        }
    }
}

@Composable
fun TitanApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("titan", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    // Connection state
    var connected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    val apiClient = remember { ApiClient("") }

    // App state
    var chats by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("chats", null)
                if (raw != null) gson.fromJson<List<Chat>>(raw, object : TypeToken<List<Chat>>() {}.type)
                else listOf(Chat())
            } catch (_: Exception) { listOf(Chat()) }
        )
    }
    var currentChatId by remember { mutableStateOf(chats.firstOrNull()?.id ?: "") }
    var models by remember { mutableStateOf(listOf<OllamaModel>()) }
    var selectedModel by remember { mutableStateOf(prefs.getString("model", "") ?: "") }
    var sshConnections by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("ssh", null)
                if (raw != null) gson.fromJson<List<SshConnection>>(raw, object : TypeToken<List<SshConnection>>() {}.type)
                else emptyList()
            } catch (_: Exception) { emptyList() }
        )
    }
    var metrics by remember { mutableStateOf(Metrics()) }
    var isStreaming by remember { mutableStateOf(false) }
    var execBlocks by remember { mutableStateOf(listOf<ExecBlock>()) }

    val currentChat = chats.find { it.id == currentChatId }

    // Recent servers
    val recentServers = remember {
        val raw = prefs.getString("recent_servers", "") ?: ""
        if (raw.isEmpty()) emptyList() else raw.split("|")
    }

    fun saveChats() {
        prefs.edit().putString("chats", gson.toJson(chats)).apply()
    }

    fun saveSSH() {
        prefs.edit().putString("ssh", gson.toJson(sshConnections)).apply()
        scope.launch { apiClient.syncSshConnections(sshConnections) }
    }

    fun saveRecentServer(ip: String, port: String) {
        val entry = "$ip:$port"
        val recents = recentServers.toMutableList()
        recents.remove(entry)
        recents.add(0, entry)
        if (recents.size > 5) recents.removeAt(recents.size - 1)
        prefs.edit()
            .putString("recent_servers", recents.joinToString("|"))
            .putString("last_ip", ip)
            .putString("last_port", port)
            .apply()
    }

    if (!connected) {
        ConnectScreen(
            recentServers = recentServers,
            isConnecting = isConnecting,
            errorMessage = connectError,
            onConnect = { ip, port ->
                isConnecting = true
                connectError = null
                val cleanIp = ip.replace("http://", "").replace("https://", "").trimEnd('/')
                val effectivePort = if (cleanIp.contains(":")) cleanIp.substringAfter(":") else port
                val effectiveIp = if (cleanIp.contains(":")) cleanIp.substringBefore(":") else cleanIp
                val fullUrl = "http://$effectiveIp:$effectivePort"
                apiClient.setBaseUrl(fullUrl)
                scope.launch {
                    try {
                        val ok = apiClient.testConnection()
                        if (ok) {
                            saveRecentServer(effectiveIp, effectivePort)
                            models = apiClient.getModels()
                            if (selectedModel.isEmpty() && models.isNotEmpty()) {
                                selectedModel = models.first().name
                                prefs.edit().putString("model", selectedModel).apply()
                            }
                            apiClient.syncSshConnections(sshConnections)
                            connected = true
                        } else {
                            connectError = "No se pudo conectar a $fullUrl"
                        }
                    } catch (e: Exception) {
                        connectError = "Error: ${e.localizedMessage ?: "Conexión fallida"}"
                    }
                    isConnecting = false
                }
            }
        )
    } else {
        ChatScreen(
            chats = chats,
            currentChat = currentChat,
            models = models,
            selectedModel = selectedModel,
            sshConnections = sshConnections,
            metrics = metrics,
            isStreaming = isStreaming,
            execBlocks = execBlocks,
            onSelectModel = { m ->
                selectedModel = m
                prefs.edit().putString("model", m).apply()
            },
            onSendMessage = { text ->
                val chat = chats.find { it.id == currentChatId } ?: return@ChatScreen
                chat.messages.add(ChatMessage("user", text))
                if (chat.messages.size == 1) {
                    chat.title = text.take(40) + if (text.length > 40) "..." else ""
                }
                chats = chats.toList() // trigger recompose
                saveChats()

                isStreaming = true
                execBlocks = emptyList()
                val startTime = System.currentTimeMillis()
                var tokenCount = 0
                var cmdCount = 0
                var fullText = ""

                scope.launch {
                    apiClient.streamAgent(
                        model = selectedModel,
                        messages = chat.messages.toList(),
                        sshConnections = sshConnections,
                    ) { event ->
                        when (event) {
                            is StreamEvent.Text -> {
                                fullText += event.content
                                tokenCount++
                                val elapsed = System.currentTimeMillis() - startTime
                                val speed = if (elapsed > 0) tokenCount / (elapsed / 1000.0) else 0.0
                                metrics = Metrics(elapsed, tokenCount, speed, cmdCount)

                                // Update last assistant message or add new
                                val msgs = chat.messages
                                if (msgs.lastOrNull()?.role == "assistant") {
                                    msgs[msgs.lastIndex] = ChatMessage("assistant", fullText)
                                } else {
                                    msgs.add(ChatMessage("assistant", fullText))
                                }
                                chats = chats.toList()
                            }
                            is StreamEvent.ExecStart -> {
                                cmdCount++
                                execBlocks = execBlocks + ExecBlock(event.host, event.command)
                                val elapsed = System.currentTimeMillis() - startTime
                                metrics = metrics.copy(execCount = cmdCount, elapsedMs = elapsed)
                            }
                            is StreamEvent.ExecResult -> {
                                execBlocks = execBlocks.map {
                                    if (it.running && it.host == event.host) it.copy(output = event.output, running = false)
                                    else it
                                }
                            }
                            is StreamEvent.ExecError -> {
                                execBlocks = execBlocks.map {
                                    if (it.running && it.host == event.host) it.copy(error = event.error, running = false)
                                    else it
                                }
                            }
                            is StreamEvent.Done -> {
                                val elapsed = System.currentTimeMillis() - startTime
                                val speed = if (elapsed > 0) tokenCount / (elapsed / 1000.0) else 0.0
                                metrics = Metrics(elapsed, tokenCount, speed, cmdCount)
                            }
                            is StreamEvent.Error -> {
                                val msgs = chat.messages
                                if (msgs.lastOrNull()?.role == "assistant") {
                                    msgs[msgs.lastIndex] = ChatMessage("assistant", fullText + "\n\nError: ${event.message}")
                                } else {
                                    msgs.add(ChatMessage("assistant", "Error: ${event.message}"))
                                }
                                chats = chats.toList()
                            }
                        }
                    }
                    isStreaming = false
                    execBlocks = emptyList()
                    saveChats()
                }
            },
            onNewChat = {
                val newChat = Chat()
                chats = listOf(newChat) + chats
                currentChatId = newChat.id
                saveChats()
            },
            onSelectChat = { chat ->
                currentChatId = chat.id
            },
            onDeleteChat = { chat ->
                chats = chats.filter { it.id != chat.id }
                if (currentChatId == chat.id) {
                    currentChatId = chats.firstOrNull()?.id ?: ""
                    if (chats.isEmpty()) {
                        val newChat = Chat()
                        chats = listOf(newChat)
                        currentChatId = newChat.id
                    }
                }
                saveChats()
            },
            onAddSsh = { conn ->
                sshConnections = sshConnections + conn
                saveSSH()
            },
            onRemoveSsh = { conn ->
                sshConnections = sshConnections.filter { it.id != conn.id }
                saveSSH()
            },
            onDisconnect = {
                connected = false
            },
        )
    }
}
