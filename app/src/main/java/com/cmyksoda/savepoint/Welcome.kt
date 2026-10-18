package com.cmyksoda.savepoint

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First launch asks what someone plays on, so adding a game only offers systems they own. */
@Composable
fun WelcomeScreen(onDone: (Set<String>) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(emptySet<String>()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.icon_handheld), null, Modifier.size(84.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("what do you play on?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose as many as you like. You can change this any time in settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRowChoices(chosen) { chosen = it }
                PinkButton(
                    onClick = { onDone(chosen) },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("continue")
                }
            }
        }
    }
}

@Composable
private fun FlowRowChoices(chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        systemFamilies.forEach { family ->
            val on = family.key in chosen
            AeroChip(on, { onChange(if (on) chosen - family.key else chosen + family.key) }) {
                Text(family.label)
            }
        }
    }
}
