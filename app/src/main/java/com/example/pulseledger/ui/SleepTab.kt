package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SleepTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val sleepWeek = remember(ui.summaries) { vm.weekly { it.sleepMinutes?.toDouble() } }
    val night = ui.sleepNight
    val stageMin: (Set<Int>) -> Int = { types ->
        night?.stages?.filter { it.type in types }?.sumOf { (it.end - it.start) / 60_000 }?.toInt() ?: 0
    }
    val deep = stageMin(setOf(5)); val rem = stageMin(setOf(6)); val light = stageMin(setOf(2, 4, 0))
    val totalMin = night?.let { ((it.end - it.start) / 60_000).toInt() }
        ?: ui.summaries.lastOrNull { it.sleepMinutes != null }?.sleepMinutes

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            StatHeader("LAST NIGHT",
                totalMin?.let { "%d:%02d".format(it / 60, it % 60) }, "hrs", PL.Sleep,
                listOf(
                    "Deep" to (if (deep > 0) "%dh%02dm".format(deep / 60, deep % 60) else ""),
                    "REM" to (if (rem > 0) "%dh%02dm".format(rem / 60, rem % 60) else ""),
                    "Light" to (if (light > 0) "%dh%02dm".format(light / 60, light % 60) else ""),
                ))
        }
        item {
            Card {
                SectionLabel("STAGE TIMELINE")
                Spacer(Modifier.height(10.dp))
                if (night != null && night.stages.isNotEmpty()) Hypnogram(night)
                else EmptyChartSlot(150, "Sleep stages appear after a night wearing the Fitbit Air")
            }
        }
        item {
            Card {
                SectionLabel("SLEEP · LAST 7 DAYS")
                Spacer(Modifier.height(10.dp))
                if (sleepWeek.any { it != null }) WeekBars(sleepWeek, PL.Sleep, 60)
                else EmptyChartSlot(60, "No sleep data yet")
            }
        }
    }
}
