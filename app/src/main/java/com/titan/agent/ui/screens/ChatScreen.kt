package com.titan.agent.ui.screens

import android.widget.TextView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.titan.agent.data.*
import com.titan.agent.ui.theme.*
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chats: List<Chat>,
    currentChat: Chat?,
    models: List<OllamaModel>,
    selectedModel: String,
    sshConnections: List<SshConnection>,
    metrics: Metrics,
    isStreaming: Boolean,
    execBlocks: List<ExecBlock>,
    thinkingText: String = "",
    gpu: GpuStatus? = null,
    attachedFiles: List<AttachedFile> = emptyList(),
    onPickFiles: () -> Unit = {},
    onRemoveAttachment: (AttachedFile) -> Unit = {},
    onRestartOllama: () -> Unit = {},
    onSelectModel: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (Chat) -> Unit,
    onDeleteChat: (Chat) -> Unit,
    onAddSsh: (SshConnection) -> Unit,
    onRemoveSsh: (SshConnection) -> Unit,
    onDisconnect: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var showSshDialog by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }

    // Reiniciar Ollama corta el modelo cargado: conviene confirmarlo.
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            containerColor = Card,
            title = { Text("Reiniciar Ollama", color = TextPrimary) },
            text = { Text("Se cortara cualquier respuesta en curso y el modelo tendra que cargarse de nuevo.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { showRestartConfirm = false; onRestartOllama() }) {
                    Text("Reiniciar", color = Yellow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Cancelar", color = Muted) }
            },
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll on new messages
    val messageCount = currentChat?.messages?.size ?: 0
    LaunchedEffect(messageCount, isStreaming) {
        if (messageCount > 0) listState.animateScrollToItem(messageCount - 1)
    }

    if (showSshDialog) {
        SshDialog(onDismiss = { showSshDialog = false }, onAdd = { onAddSsh(it); showSshDialog = false })
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        // Sidebar (tablet-width)
        AnimatedVisibility(visible = drawerOpen, enter = slideInHorizontally(), exit = slideOutHorizontally()) {
            SidePanel(
                chats = chats,
                currentChat = currentChat,
                models = models,
                selectedModel = selectedModel,
                sshConnections = sshConnections,
                metrics = metrics,
                gpu = gpu,
                onSelectModel = onSelectModel,
                onNewChat = onNewChat,
                onSelectChat = { onSelectChat(it); drawerOpen = false },
                onDeleteChat = onDeleteChat,
                onAddSsh = { showSshDialog = true },
                onRemoveSsh = onRemoveSsh,
                onDisconnect = onDisconnect,
            )
        }

        // Main chat area
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Top bar
            Surface(color = Card, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { drawerOpen = !drawerOpen }) {
                        Icon(Icons.Default.Menu, "Menu", tint = Yellow)
                    }
                    IconButton(
                        onClick = { showRestartConfirm = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Refresh, "Reiniciar Ollama", tint = Muted, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        currentChat?.title ?: "TITAN AGENT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                    )
                    // Compact metrics
                    if (metrics.tokens > 0) {
                        MetricChip("${formatTime(metrics.elapsedMs)}", Yellow)
                        Spacer(Modifier.width(6.dp))
                        MetricChip("${metrics.tokens} tok", Accent)
                        Spacer(Modifier.width(6.dp))
                        MetricChip("%.1f t/s".format(metrics.speed), Ok)
                        if (metrics.execCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            MetricChip("${metrics.execCount} cmd", Warn)
                        }
                    }
                }
            }

            Divider(color = Border, thickness = 1.dp)

            // Messages
            if (currentChat == null || currentChat.messages.isEmpty()) {
                WelcomeView(Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(currentChat.messages) { msg ->
                        MessageBubble(msg)
                    }
                    // Show exec blocks during streaming
                    if (execBlocks.isNotEmpty()) {
                        items(execBlocks) { block ->
                            ExecBlockView(block)
                        }
                    }
                    // Thinking indicator
                    if (isStreaming) {
                        item { ThinkingIndicator(thinkingText) }
                    }
                }
            }

            // Input bar
            Surface(color = Card, tonalElevation = 0.dp) {
              Column(Modifier.fillMaxWidth()) {
                // Adjuntos pendientes de enviar
                if (attachedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        attachedFiles.forEach { f ->
                            AssistChip(
                                onClick = { onRemoveAttachment(f) },
                                label = { Text(f.name.take(24), fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, "Quitar", Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Card2,
                                    labelColor = TextPrimary,
                                    trailingIconContentColor = Muted,
                                ),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = onPickFiles,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.AttachFile, "Adjuntar archivos", tint = Muted)
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe tu mensaje...", color = Muted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yellow,
                            unfocusedBorderColor = Border,
                            cursorColor = Yellow,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if ((inputText.isNotBlank() || attachedFiles.isNotEmpty()) && !isStreaming) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            // Con adjuntos se puede enviar sin escribir nada
                            if ((inputText.isNotBlank() || attachedFiles.isNotEmpty()) && !isStreaming) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        containerColor = if (isStreaming) YellowDark.copy(alpha = 0.4f) else Yellow,
                        contentColor = Color.Black,
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Send, "Enviar")
                    }
                }
              }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidePanel(
    chats: List<Chat>,
    currentChat: Chat?,
    models: List<OllamaModel>,
    selectedModel: String,
    sshConnections: List<SshConnection>,
    metrics: Metrics,
    gpu: GpuStatus?,
    onSelectModel: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (Chat) -> Unit,
    onDeleteChat: (Chat) -> Unit,
    onAddSsh: () -> Unit,
    onRemoveSsh: (SshConnection) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.width(280.dp).fillMaxHeight().background(Card).border(
            width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp)
        )
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Yellow),
                contentAlignment = Alignment.Center,
            ) {
                Text("T", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("TITAN AGENT", fontWeight = FontWeight.Bold, color = Yellow, fontSize = 14.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDisconnect, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Logout, "Desconectar", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }

        Divider(color = Border)

        // New chat
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("+ NUEVO CHAT", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        // Chat list
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            chats.forEach { chat ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (chat.id == currentChat?.id) Card2 else Color.Transparent)
                        .clickable { onSelectChat(chat) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chat.id == currentChat?.id) {
                        Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Yellow))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        chat.title,
                        fontSize = 13.sp,
                        color = if (chat.id == currentChat?.id) TextPrimary else Muted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteChat(chat) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Borrar", tint = Danger.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Divider(color = Border)

        // Metricas de la ultima ejecucion. La seccion ya no se oculta sin datos:
        // se muestra en cero, para que no parezca que ha desaparecido.
        CollapsibleSection(
            title = "ULTIMA EJECUCION",
            prefsKey = "sec_metrics",
            trailing = if (metrics.tokens > 0) "${metrics.tokens} tok" else null,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Tiempo", formatTime(metrics.elapsedMs), Modifier.weight(1f))
                MiniMetric("Tokens", "${metrics.tokens}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("Velocidad", "%.1f t/s".format(metrics.speed), Modifier.weight(1f))
                MiniMetric("Comandos", "${metrics.execCount}", Modifier.weight(1f))
            }
        }
        Divider(color = Border)

        // SSH
        Column(Modifier.padding(12.dp)) {
            Text("SSH CONNECTIONS", fontSize = 11.sp, color = Yellow, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            sshConnections.forEach { conn ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Ok))
                    Spacer(Modifier.width(8.dp))
                    Text("${conn.name} (${conn.host})", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveSsh(conn) }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, "Desconectar", tint = Danger.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                    }
                }
            }
            OutlinedButton(
                onClick = onAddSsh,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("+ Agregar conexion", fontSize = 12.sp, color = Muted)
            }
        }

        Divider(color = Border)

        // Estado de la GPU del servidor
        gpu?.let { g ->
            CollapsibleSection(
                title = "GPU",
                prefsKey = "sec_gpu",
                trailing = "${g.vramPercent}%",
            ) {
                Text(g.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                // La barra hace evidente de un vistazo si el modelo cabe o esta desbordando
                LinearProgressIndicator(
                    progress = g.vramPercent / 100f,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (g.vramPercent > 90) Danger else if (g.vramPercent > 75) Warn else Ok,
                    trackColor = Card2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "VRAM %.1f / %.1f GB (%d%%)".format(g.vramUsedGb, g.vramTotalGb, g.vramPercent),
                    fontSize = 11.sp, color = Muted,
                )
                Text("Carga ${g.load}%   ·   ${g.temp}°C", fontSize = 11.sp, color = Muted)
            }
            Divider(color = Border)
        }

        // Model selector
        Column(Modifier.padding(12.dp)) {
            Text("MODELO", fontSize = 11.sp, color = Yellow, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Border,
                        focusedBorderColor = Yellow,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name, fontSize = 13.sp) },
                            onClick = { onSelectModel(model.name); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    if (msg.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(UserBubble)
                    .padding(14.dp),
            ) {
                Text(msg.content, color = TextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Text("TITAN", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            // Render markdown
            val cleaned = msg.content.replace(Regex("```exec\\s*\\n[\\s\\S]*?```"), "")
            AndroidView(
                factory = { ctx ->
                    val markwon = Markwon.builder(ctx).build()
                    TextView(ctx).apply {
                        setTextColor(TextPrimary.toArgb())
                        textSize = 14f
                        setLineSpacing(8f, 1f)
                        markwon.setMarkdown(this, cleaned)
                    }
                },
                update = { tv ->
                    val markwon = Markwon.builder(context).build()
                    val cleaned2 = msg.content.replace(Regex("```exec\\s*\\n[\\s\\S]*?```"), "")
                    markwon.setMarkdown(tv, cleaned2)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExecBlockView(block: ExecBlock) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(CodeBg).padding(8.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Terminal, "exec", tint = Ok, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ejecutando en ", fontSize = 12.sp, color = Muted)
            Text(block.host, fontSize = 12.sp, color = Yellow, fontWeight = FontWeight.SemiBold)
        }
        // Command
        Text(
            "$ ${block.command}",
            fontSize = 13.sp,
            color = Warn,
            fontFamily = Mono,
            modifier = Modifier.fillMaxWidth().background(Card).padding(10.dp),
        )
        Divider(color = Border, thickness = 1.dp)
        // Output or spinner
        if (block.running) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⟳ Ejecutando...", fontSize = 12.sp, color = Yellow)
            }
        } else if (block.error != null) {
            Text(
                block.error!!,
                fontSize = 12.sp,
                color = Danger,
                fontFamily = Mono,
                modifier = Modifier.fillMaxWidth().background(Card).padding(10.dp),
            )
        } else if (block.output != null) {
            Text(
                block.output!!,
                fontSize = 12.sp,
                color = TextPrimary.copy(alpha = 0.85f),
                fontFamily = Mono,
                modifier = Modifier.fillMaxWidth().background(Card).padding(10.dp)
                    .heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun WelcomeView(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Yellow),
            contentAlignment = Alignment.Center,
        ) {
            Text("T", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text("TITAN AGENT", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Yellow, letterSpacing = 2.sp)
        Text("IA local + SSH remoto", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("Chat IA", "Modelos locales\nvia Ollama")
            FeatureCard("Terminal SSH", "Ejecuta en\ntus maquinas")
            FeatureCard("Agente", "Comandos\nautomaticos")
        }
    }
}

@Composable
private fun FeatureCard(title: String, desc: String) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .background(Card)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 13.sp, color = Yellow, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 12.sp, color = Muted, lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MetricChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

/**
 * Seccion del panel lateral que se puede plegar tocando su titulo.
 * El titulo permanece siempre visible: antes la seccion de metricas desaparecia
 * entera cuando no habia datos y parecia que se hubiera perdido.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    prefsKey: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // rememberSaveable para que el plegado sobreviva a girar la pantalla
    var expanded by rememberSaveable(prefsKey) { mutableStateOf(true) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 11.sp,
                color = Yellow,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            // Resumen visible aun con la seccion plegada
            if (trailing != null && !expanded) {
                Text(trailing, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(end = 6.dp))
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "Ocultar" else "Mostrar",
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Card2)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(label, fontSize = 10.sp, color = Muted, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Yellow, fontFamily = Mono)
    }
}

@Composable
private fun ThinkingIndicator(thinkingText: String = "") {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = i * 200),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(Yellow.copy(alpha = alpha)))
            }
        }
        // El razonamiento del modelo, atenuado: sin esto son segundos de silencio
        // en los que el usuario no sabe si el agente esta trabajando o colgado.
        if (thinkingText.isNotBlank()) {
            Text(
                text = thinkingText.takeLast(400),
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshDialog(onDismiss: () -> Unit, onAdd: (SshConnection) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var quickInput by remember { mutableStateOf("") }

    fun parseQuickInput(input: String) {
        val cleaned = input.trim().removePrefix("ssh").trim()
        if (cleaned.contains("@")) {
            val parts = cleaned.split("@", limit = 2)
            user = parts[0]
            val hostPart = parts[1]
            if (hostPart.contains(":")) {
                host = hostPart.substringBefore(":")
                port = hostPart.substringAfter(":")
            } else {
                host = hostPart
            }
            name = "$user@$host"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Nueva conexion SSH", color = Yellow, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tfColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Yellow, unfocusedBorderColor = Border,
                    focusedLabelColor = Yellow, unfocusedLabelColor = Muted, cursorColor = Yellow,
                )
                OutlinedTextField(
                    quickInput, { quickInput = it; parseQuickInput(it) },
                    label = { Text("Conexion rapida") },
                    placeholder = { Text("ssh admin@10.0.0.10") },
                    singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth(),
                )
                Divider(color = Border, modifier = Modifier.padding(vertical = 4.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, colors = tfColors, placeholder = { Text("10.0.0.x") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("Puerto") }, singleLine = true, colors = tfColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(SshConnection(name = name.ifBlank { "Server" }, host = host, port = port.toIntOrNull() ?: 22, username = user, password = pass)) },
                enabled = host.isNotBlank() && user.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black),
            ) { Text("CONECTAR", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) }
        },
    )
}

private fun formatTime(ms: Long): String {
    return if (ms >= 60000) "%.1fm".format(ms / 60000.0) else "%.1fs".format(ms / 1000.0)
}
