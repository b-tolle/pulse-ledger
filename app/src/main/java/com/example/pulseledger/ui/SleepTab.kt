package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SleepTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val sleepWeek = remember(ui.summaries) { vm.weekly { it.sleepMinutes?.toDouble() } }
    val lastSleep = ui.summaries.lastOrNull { it.sleepMinutes != null }?.sleepMinutes
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            StatHeader("LAST NIGHT",
                lastSleep?.let { "%d:%02d".format(it / 60, it % 60) }, "hrs", PL.Sleep,
                listOf("Deep" to "", "REM" to "", "Light" to ""))
        }
        item {
            Card {
                SectionLabel("STAGE TIMELINE")
                Spacer(Modifier.height(10.dp))
                EmptyChartSlot(160, "Sleep stages (deep/REM/light) arrive with the Fitbit Air")
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
