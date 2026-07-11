package com.example.pulseledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, color = PL.Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = modifier)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth()
        .background(PL.Card, RoundedCornerShape(16.dp))
        .border(1.dp, PL.Line, RoundedCornerShape(16.dp))
        .padding(16.dp),
    content = content,
)

/** Big-number stat header: hero value left, secondary stats stacked right. */
@Composable
fun StatHeader(
    label: String,
    heroValue: String?,
    heroUnit: String,
    heroColor: Color,
    secondary: List<Pair<String, String>>,   // (label, value) — value "" shows as em-dash
) {
    Card {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SectionLabel(label)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(heroValue ?: "—", color = if (heroValue != null) heroColor else PL.Dim,
                        fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    if (heroValue != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(heroUnit, color = PL.Soft, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                secondary.forEach { (l, v) ->
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(l, color = PL.Dim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(v.ifEmpty { "—" }, color = if (v.isEmpty()) PL.Dim else PL.Txt,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/** Compact metric card for the Home grid: icon dot, title, big value, optional sub. */
@Composable
fun MetricCard(
    title: String,
    value: String?,
    unit: String,
    accent: Color,
    sub: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chart: (@Composable () -> Unit)? = null,
) {
    Card(if (onClick != null) modifier.clickable { onClick() } else modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, color = PL.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", color = if (value != null) PL.Txt else PL.Dim,
                fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (value != null && unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = PL.Soft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = PL.Dim, fontSize = 11.sp)
        }
        if (chart != null) { Spacer(Modifier.height(8.dp)); chart() }
    }
}

/** Placeholder that HOLDS ITS SPACE but shows no fabricated data. */
@Composable
fun EmptyChartSlot(heightDp: Int = 120, message: String = "No data yet") {
    Box(
        Modifier.fillMaxWidth().height(heightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PL.CardUp.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = PL.Dim, fontSize = 12.sp)
    }
}
