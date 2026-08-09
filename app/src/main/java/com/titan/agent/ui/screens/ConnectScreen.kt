package com.titan.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titan.agent.ui.theme.*

@Composable
fun ConnectScreen(
    recentServers: List<String>,
    onConnect: (ip: String, port: String) -> Unit,
    isConnecting: Boolean,
    errorMessage: String?,
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3000") }

    Box(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Yellow),
                contentAlignment = Alignment.Center,
            ) {
                Text("T", fontSize = 32.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.Black)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "TITAN AGENT",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Yellow,
                letterSpacing = 2.sp,
            )

            Text(
                "IA local + SSH remoto",
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )

            // IP field
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del servidor (Tailscale)") },
                placeholder = { Text("100.x.x.x") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Yellow,
                    focusedLabelColor = Yellow,
                    cursorColor = Yellow,
                    unfocusedBorderColor = Border,
                    unfocusedLabelColor = Muted,
                ),
            )

            Spacer(Modifier.height(12.dp))

            // Port field
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Puerto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Yellow,
                    focusedLabelColor = Yellow,
                    cursorColor = Yellow,
                    unfocusedBorderColor = Border,
                    unfocusedLabelColor = Muted,
                ),
            )

            Spacer(Modifier.height(24.dp))

            // Connect button
            Button(
                onClick = { onConnect(ip.trim(), port.trim().ifEmpty { "3000" }) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = ip.isNotBlank() && !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Yellow,
                    contentColor = androidx.compose.ui.graphics.Color.Black,
                    disabledContainerColor = YellowDark.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isConnecting) {
                    Text("CONECTANDO...", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                } else {
                    Text("CONECTAR", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 15.sp)
                }
            }

            // Error
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = Danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // Recent servers
            if (recentServers.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "RECIENTES",
                    fontSize = 11.sp,
                    color = Muted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                recentServers.forEach { server ->
                    val parts = server.split(":")
                    val sIp = parts.dropLast(1).joinToString(":")
                    val sPort = parts.lastOrNull() ?: "3000"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Card2)
                            .clickable {
                                ip = sIp
                                port = sPort
                                onConnect(sIp, sPort)
                            }
                            .padding(12.dp),
                    ) {
                        Text(server, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
