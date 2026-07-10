package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeartTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val rhrWeek = remember(ui.summaries) { vm.weekly { it.restingHr?.toDouble() } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            StatHeader("RESTING", ui.restingHr?.toString(), "bpm", PL.Sys,
                listOf("Avg" to "", "Max" to "", "Min" to ""))
        }
        item {
            Card {
                SectionLabel("24-HOUR TIMELINE")
                Spacer(Modifier.height(10.dp))
                EmptyChartSlot(160, "Intraday heart rate arrives with the Fitbit Air")
            }
        }
        item {
            Card {
                SectionLabel("RESTING HR · LAST 7 DAYS")
                Spacer(Modifier.height(10.dp))
                if (rhrWeek.any { it != null }) WeekBars(rhrWeek, PL.Sys, 60)
                else EmptyChartSlot(60, "No resting HR yet")
            }
        }
        item {
            Card {
                SectionLabel("HRV")
                Spacer(Modifier.height(8.dp))
                Text("Heart-rate variability needs the Fitbit Air's overnight sensor. This card will fill in once it's syncing.",
                    color = PL.Soft, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                EmptyChartSlot(70, "HRV — waiting on wearable")
            }
        }
    }
}
