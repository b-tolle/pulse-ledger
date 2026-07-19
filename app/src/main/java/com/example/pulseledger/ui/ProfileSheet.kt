package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(onDismiss: () -> Unit) {
    var dob by remember { mutableStateOf(Profile.birth.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))) }
    var female by remember { mutableStateOf(Profile.female) }
    var heightFt by remember { mutableStateOf(Profile.heightIn?.let { (it / 12).toString() } ?: "") }
    var heightInch by remember { mutableStateOf(Profile.heightIn?.let { (it % 12).toString() } ?: "") }
    var waist by remember { mutableStateOf(Profile.waistIn?.let { "%.0f".format(it) } ?: "") }
    var goal by remember { mutableStateOf(Profile.goalLbs?.let { "%.0f".format(it) } ?: "") }
    var startTab by remember { mutableStateOf(Profile.startTab) }
    var stepGoal by remember { mutableStateOf(Profile.stepGoal.toString()) }
    var partner by remember { mutableStateOf(Profile.partnerCode ?: "") }
    var togetherBg by remember { mutableStateOf(Profile.togetherBackground) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PL.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile", color = PL.Txt, fontSize = 19.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Stays on this phone. Drives peer ranges, HR zones, BMI and body-shape metrics.",
                color = PL.Soft, fontSize = 12.sp, lineHeight = 17.sp)

            OutlinedTextField(dob, { dob = it }, label = { Text("Birthday (MM/DD/YYYY)") }, singleLine = true)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !female, onClick = { female = false }, label = { Text("Male") })
                FilterChip(selected = female, onClick = { female = true }, label = { Text("Female") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(heightFt, { heightFt = it.filter(Char::isDigit).take(1) },
                    label = { Text("Height ft") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(heightInch, { heightInch = it.filter(Char::isDigit).take(2) },
                    label = { Text("in") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(waist, { waist = it.filter(Char::isDigit).take(2) },
                label = { Text("Waist (inches, optional — enables BRI)") }, singleLine = true)
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(3) },
                label = { Text("Goal weight (lb, optional)") }, singleLine = true)
            OutlinedTextField(stepGoal, { stepGoal = it.filter(Char::isDigit).take(5) },
                label = { Text("Daily step goal") }, singleLine = true)

            OutlinedTextField(partner, { partner = it.take(20) },
                label = { Text("Partner code (same word on both phones)") }, singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = togetherBg, onCheckedChange = { togetherBg = it })
                Spacer(Modifier.width(10.dp))
                Text("Background together tracking\n(small persistent notification, ~1% battery/day)",
                    color = PL.Soft, fontSize = 12.sp, lineHeight = 16.sp)
            }

            Text("START ON", color = PL.Soft, fontSize = 11.sp, letterSpacing = 1.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Home", "Weight", "Pressure").forEachIndexed { i, l ->
                    FilterChip(selected = startTab == i, onClick = { startTab = i }, label = { Text(l, fontSize = 12.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Heart", "Sleep", "Data").forEachIndexed { i, l ->
                    FilterChip(selected = startTab == i + 3, onClick = { startTab = i + 3 }, label = { Text(l, fontSize = 12.sp) })
                }
            }

            Button(
                onClick = {
                    runCatching {
                        Profile.birth = LocalDate.parse(dob, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                    }
                    Profile.female = female
                    Profile.heightIn = (heightFt.toIntOrNull()?.times(12) ?: 0) + (heightInch.toIntOrNull() ?: 0)
                    Profile.waistIn = waist.toDoubleOrNull()
                    Profile.goalLbs = goal.toDoubleOrNull()
                    Profile.startTab = startTab
                    stepGoal.toIntOrNull()?.let { if (it in 1000..50000) Profile.stepGoal = it }
                    Profile.partnerCode = partner.trim().ifBlank { null }
                    Profile.togetherBackground = togetherBg
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PL.Charge),
            ) { Text("Save", color = PL.Bg) }
        }
    }
}
