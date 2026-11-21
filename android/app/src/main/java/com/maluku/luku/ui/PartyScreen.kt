package com.maluku.luku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.luku.App

/** Samen kijken: party maken of meedoen met een code. */
@Composable
fun PartyScreen(nav: Nav) {
    val app = App.instance
    val party = app.party
    var name by remember { mutableStateOf(app.prefs.partyName) }
    var joinCode by remember { mutableStateOf("") }
    var ownCode by remember { mutableStateOf("") }
    var chatText by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { TopBar(nav, "Samen kijken") }
        if (!party.active) {
            item {
                Text(
                    "Maak een party en stuur de code naar je vrienden. Daarna ziet iedereen hetzelfde: " +
                        "kiest iemand een film, dan opent hij bij iedereen. Pauzeert iemand, dan pauzeert het bij iedereen.",
                    color = Z.dim, fontSize = 14.sp,
                )
            }
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) },
                    label = { Text("Jouw naam") }, singleLine = true, colors = lukuFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionTitle("Nieuwe party")
                OutlinedTextField(
                    value = ownCode, onValueChange = { v -> ownCode = v.filter { it.isLetterOrDigit() }.take(12) },
                    label = { Text("Eigen code (mag leeg, bijv. PAPA)") }, singleLine = true, colors = lukuFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LukuButton(if (party.connecting) "Verbinden…" else "🎉  Party maken", modifier = Modifier.fillMaxWidth()) {
                    app.prefs.partyName = name
                    party.create(name, ownCode)
                }
            }
            item {
                SectionTitle("Meedoen met een code")
                OutlinedTextField(
                    value = joinCode, onValueChange = { joinCode = it.take(12) },
                    label = { Text("Code, bijv. B4QD of PAPA") }, singleLine = true, colors = lukuFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LukuButton("Meedoen", primary = false, modifier = Modifier.fillMaxWidth()) {
                    if (joinCode.isNotBlank()) {
                        app.prefs.partyName = name
                        party.join(joinCode, name)
                    }
                }
            }
            party.error?.let { item { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)) } }
            if (party.connecting) item {
                Text("Verbinden met de party-server… De eerste keer kan dit tot 30 seconden duren (de server wordt dan wakker).", color = Z.dim, fontSize = 13.sp)
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Z.panel, RoundedCornerShape(14.dp)).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Party-code", color = Z.dim, fontSize = 13.sp)
                    Text(party.code ?: "", color = Z.accent, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (party.connected) "Stuur deze code naar je vrienden" else "Opnieuw verbinden…",
                        color = Z.dim, fontSize = 13.sp,
                    )
                }
            }
            item { SectionTitle("In de party (${party.members.size})") }
            items(party.members.size) { i ->
                val m = party.members[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (m.host) "👑 " else "👤 ") + m.name + (if (m.id == party.myId) " (jij)" else ""), color = Z.text, fontSize = 16.sp)
                }
            }
            if (party.amHost) item {
                SectionTitle("Bediening")
                Row {
                    Chip("Iedereen bedient", !party.hostOnly) { party.changeHostOnly(false) }
                    Spacer(Modifier.width(8.dp))
                    Chip("Alleen ik", party.hostOnly) { party.changeHostOnly(true) }
                }
            } else if (party.hostOnly) item {
                Text("De host bedient nu alleen. Jij kijkt mee.", color = Z.dim, fontSize = 13.sp)
            }
            item {
                SectionTitle("Hoe werkt het?")
                Text(
                    "Ga gewoon naar Home of Ontdekken en kies iets. Wat jij opent, opent bij iedereen. " +
                        "Pauze, play en spoelen gaan ook bij iedereen. Iedereen haalt de film zelf binnen, " +
                        "dus wie trager internet heeft, kan even achterlopen; de app trekt het daarna gelijk.",
                    color = Z.dim, fontSize = 13.sp,
                )
            }
            item {
                SectionTitle("Chat")
                party.chat.takeLast(8).forEach { Text(it, color = Z.text, fontSize = 14.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = chatText, onValueChange = { chatText = it.take(300) },
                        label = { Text("Bericht") }, singleLine = true, colors = lukuFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    LukuButton("Stuur") { party.sendChat(chatText); chatText = "" }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                LukuButton("Party verlaten", primary = false, modifier = Modifier.fillMaxWidth()) { party.leave() }
            }
        }
    }
}
