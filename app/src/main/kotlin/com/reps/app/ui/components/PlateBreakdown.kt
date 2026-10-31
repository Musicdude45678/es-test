package com.reps.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reps.app.data.model.WeightUnit
import com.reps.app.domain.Calc

private fun plateColor(plate: Double, unit: WeightUnit): Color = if (unit == WeightUnit.KG) when (plate) {
    25.0 -> Color(0xFFE53935)
    20.0 -> Color(0xFF1E88E5)
    15.0 -> Color(0xFFFDD835)
    10.0 -> Color(0xFF43A047)
    5.0 -> Color(0xFFF5F5F5)
    2.5 -> Color(0xFF8E8E8E)
    else -> Color(0xFFB0BEC5)
} else when (plate) {
    45.0 -> Color(0xFF1E88E5)
    35.0 -> Color(0xFFFDD835)
    25.0 -> Color(0xFF43A047)
    10.0 -> Color(0xFFF5F5F5)
    5.0 -> Color(0xFF8E8E8E)
    else -> Color(0xFFB0BEC5)
}

private fun plateHeight(plate: Double, unit: WeightUnit): Int {
    val max = if (unit == WeightUnit.KG) 25.0 else 45.0
    return (16 + 22 * (plate / max)).toInt()
}

/** Shows the plates to load on each side of the bar. */
@Composable
fun PlateBreakdown(totalKg: Double, barKg: Double, unit: WeightUnit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val plates = Calc.platesPerSide(totalKg, barKg, unit)
    val belowBar = totalKg < barKg - 0.0001
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val label = when {
            belowBar -> "Less than the bar"
            plates.isEmpty() -> "Bar only"
            else -> "Per side: " + plates.groupBy { it }.entries.joinToString("  ") { (p, list) -> (if (list.size > 1) "${list.size}×" else "") + Calc.formatNumber(p) }
        }
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (!compact && plates.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(10.dp).height(5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                plates.forEach { p ->
                    Box(
                        Modifier
                            .width(if (p >= 10) 9.dp else 6.dp)
                            .height(plateHeight(p, unit).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(plateColor(p, unit)),
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }
        }
    }
}

@Composable
fun PlateLegend(plate: Double, unit: WeightUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(plateColor(plate, unit)))
        Text(" ${Calc.formatNumber(plate)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
    }
}
