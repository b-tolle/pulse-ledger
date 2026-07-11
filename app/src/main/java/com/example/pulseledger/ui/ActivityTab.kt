package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    }
}
