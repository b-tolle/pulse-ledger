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
fun HeartTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val rhrWeek = remember(ui.summaries) { vm.weekly { it.restingHr?.toDouble() } }
    val todayBpm = ui.hrToday.map { it.first }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            StatHeader("HEART RATE", (ui.latestHr ?: ui.restingHr)?.toString(), "bpm", PL.Sys,
                listOf(
                    "Avg" to (todayBpm.takeIf { it.isNotEmpty() }?.average()?.toInt()?.toString() ?: ""),
                    "Max" to (todayBpm.maxOrNull()?.toString() ?: ""),
                    "Min" to (todayBpm.minOrNull()?.toString() ?: ""),
                ))
        }
        item {
            Card {
                SectionLabel("24-HOUR TIMELINE")
                Spacer(Modifier.height(10.dp))
                if (ui.hrToday.size >= 2) IntradayHrChart(ui.hrToday)
                else EmptyChartSlot(160, "No heart-rate samples yet today — wear and sync your Fitbit Air")
            }
        }
        item {
            Card {
                SectionLabel("HRV")
                Spacer(Modifier.height(8.dp))
                if (ui.hrvLatest != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Text("%.0f".format(ui.hrvLatest), color = PL.Sleep, fontSize = 38.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Spacer(Modifier.width(6.dp))
                        Text("ms rmssd", color = PL.Soft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Measured overnight. Higher generally means better recovery — judge against your own baseline, not others'.",
                        color = PL.Dim, fontSize = 11.5.sp, lineHeight = 16.sp)
                    if (ui.hrvWeek.count { it != null } >= 2) {
                        Spacer(Modifier.height(12.dp))
                        Text("LAST 7 NIGHTS", color = PL.Soft, fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(6.dp))
                        WeekBars(ui.hrvWeek, PL.Sleep, 44)
                    }
                } else {
                    Text("HRV is measured during sleep — wear the Air overnight and it appears here after the morning sync.",
                        color = PL.Soft, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    EmptyChartSlot(60, "HRV — after your first tracked night")
                }
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
        item { PeerCard(heartRanges(ui)) }
        if (ui.hrSampleCount > 0) item {
            Card {
                SectionLabel("SOURCES")
                Spacer(Modifier.height(6.dp))
                Text("${ui.hrSampleCount} samples this month from " +
                    ui.hrSources.joinToString(", ") { it.substringAfterLast('.') },
                    color = PL.Dim, fontSize = 11.5.sp)
            }
        }
    }
}
