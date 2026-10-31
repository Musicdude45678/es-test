package com.reps.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reps.app.data.model.WeightUnit
import com.reps.app.domain.Calc

/** Options for the bar section shown when editing the weight of a bar exercise. */
data class BarOptions(
    val unit: WeightUnit,
    val barKg: Double,
    val choicesKg: List<Double>,
    val onBarChanged: (Double) -> Unit
)

/**
 * Bottom-sheet numeric keypad. Works for weights (decimals allowed) and reps (integers).
 * [initial] is in display units; the result is returned in display units.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPadSheet(
    title: String,
    initial: Double,
    unitLabel: String?,
    allowDecimal: Boolean,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    bar: BarOptions? = null,
    quickValues: List<Double> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(Calc.formatNumber(initial)) }
    var fresh by remember { mutableStateOf(true) } // first keystroke replaces the current value

    fun press(key: String) {
        when (key) {
            "⌫" -> { text = if (fresh) "" else text.dropLast(1); fresh = false }
            "." -> { if (allowDecimal && !text.contains('.')) { text = (if (fresh || text.isEmpty()) "0" else text) + "."; fresh = false } }
            else -> {
                text = if (fresh) key else if (text == "0") key else text + key
                fresh = false
                if (text.length > 7) text = text.take(7)
            }
        }
    }

    val value = text.toDoubleOrNull() ?: 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text.ifEmpty { "0" },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    color = if (fresh) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (unitLabel != null) {
                    Text(" $unitLabel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
            }

            if (bar != null) {
                BarSection(bar = bar, totalDisplay = value, onBarOnly = { text = Calc.formatNumber(bar.unit.fromKg(bar.barKg)); fresh = true })
                Spacer(Modifier.height(8.dp))
            } else if (quickValues.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickValues) { q ->
                        FilterChip(selected = value == q, onClick = { text = Calc.formatNumber(q); fresh = true }, label = { Text(Calc.formatNumber(q)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(if (allowDecimal) "." else "", "0", "⌫")
            )
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (k == "⌫") MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { press(k) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                                else Text(k, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onConfirm(value) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Done", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun BarSection(bar: BarOptions, totalDisplay: Double, onBarOnly: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bar.choicesKg) { choice ->
                    FilterChip(
                        selected = kotlin.math.abs(choice - bar.barKg) < 0.001,
                        onClick = { bar.onBarChanged(choice) },
                        label = { Text(if (choice == 0.0) "None" else Calc.formatWeight(choice, bar.unit)) }
                    )
                }
                item {
                    FilterChip(selected = false, onClick = onBarOnly, label = { Text("Bar only") })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        PlateBreakdown(totalKg = bar.unit.toKg(totalDisplay), barKg = bar.barKg, unit = bar.unit)
    }
}
