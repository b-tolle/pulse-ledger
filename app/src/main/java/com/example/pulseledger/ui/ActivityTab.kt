package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActivityTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val stepWeek = ui.stepWeekLive
    val exWeek = remember(ui.summaries) { vm.weekly { it.exerciseMin?.toDouble() } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            StatHeader("STEPS TODAY", ui.stepsToday?.let { "%,d".format(it) }, "", PL.Charge,
                listOf("7-day" to (ui.steps7dAvg?.let { "%,d".format(it) } ?: ""),
                       "Goal" to "8,000"))
        }
        item {
            Card {
                SectionLabel("STEPS · LAST 7 DAYS")
                Spacer(Modifier.height(10.dp))
                if (stepWeek.any { it != null }) WeekBars(stepWeek, PL.Charge, 72, labels = ui.weekLabels) else EmptyChartSlot(64)
            }
        }
        item {
            Card {
                SectionLabel("EXERCISE · LAST 7 DAYS")
                Spacer(Modifier.height(10.dp))
                if (exWeek.any { it != null }) WeekBars(exWeek, PL.Dia, 64)
                else EmptyChartSlot(64, "No workouts logged")
            }
        }
        if (ui.workouts.isNotEmpty()) {
            item { SectionLabel("RECENT WORKOUTS") }
            items(ui.workouts.size) { i ->
                val wk = ui.workouts[i]
                Card {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(wk.title, color = PL.Txt, fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            val mins = ((wk.end - wk.start) / 60_000).toInt()
                            Text(fmtWorkoutTime(wk.start) + " · ${mins} min",
                                color = PL.Soft, fontSize = 12.sp)
                        }
                        if (wk.avgHr != null) Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("${wk.avgHr} avg", color = PL.Sys, fontSize = 13.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            wk.maxHr?.let { Text("$it max", color = PL.Dim, fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                        }
                    }
                    if (wk.spark.size >= 2) {
                        Spacer(Modifier.height(10.dp))
                        AreaSpark(wk.spark, PL.Sys, 48)
                    }
                }
            }
        }
        item { PeerCard(activityRanges(ui)) }
    }
}


private fun fmtWorkoutTime(epoch: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("EEE MMM d · h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(epoch))
