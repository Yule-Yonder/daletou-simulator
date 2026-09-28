package com.yy.lottosim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yy.lottosim.lotto.LottoRules
import com.yy.lottosim.lotto.SimMode
import com.yy.lottosim.lotto.SimulationEngine
import com.yy.lottosim.lotto.TrackRecord

/**
 * 持续跟踪页：开启后每期开奖自动比对（打开 APP 时静默同步+比对）
 */
@Composable
fun TrackScreen(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    mode: SimMode,
    fixedFront: Set<Int>,
    fixedBack: Set<Int>,
    notes: Int,
    records: List<TrackRecord>,
    msg: String?,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onClear: () -> Unit,
    engine: SimulationEngine
) {
    val summary = if (records.isNotEmpty()) engine.trackSummary(records) else null
    val recent = records.sortedByDescending { it.num }.take(30)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("持续跟踪", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "从今天起每期开奖自动比对一次，见证汪苏蕊坚持买下去的真实轨迹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 开关 + 当前策略
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("开启持续跟踪", fontWeight = FontWeight.Bold)
                            Text(
                                "打开 APP 时自动同步最新开奖并比对",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = onEnabledChange)
                    }
                    val strategy = if (mode == SimMode.FIXED) {
                        val ok = fixedFront.size == 5 && fixedBack.size == 2
                        "守号 " + (if (ok) LottoRules.formatNote(fixedFront, fixedBack) else "（未选满，按机选）")
                    } else "机选"
                    Text(
                        "当前策略：每期 $notes 注 · $strategy（在「回放」页修改）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    msg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSyncNow, enabled = !syncing) {
                            Text(if (syncing) "同步比对中…" else "立即同步并比对")
                        }
                        OutlinedButton(onClick = onClear, enabled = records.isNotEmpty()) {
                            Text("清空记录")
                        }
                    }
                }
            }
        }

        // 累计统计
        if (summary != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("汪苏蕊的累计战绩", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (summary.net >= 0) "净赚 ${formatMoney(summary.net)}" else "净亏 ${formatMoney(summary.net)}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.net >= 0) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("已跟 ${summary.totalDraws} 期 · ${formatCount(summary.totalNotes)} 注")
                            Text("投入 ${formatMoney(summary.totalCost)}")
                        }
                        Text(
                            "税前 ${formatMoney(summary.grossWin)} · 代扣个税 -${formatMoney(summary.totalTax)} · 到手 ${formatMoney(summary.totalWin)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (summary.bigWins.isNotEmpty()) {
                            Text(
                                "🎉 大奖：${summary.bigWins.joinToString { "${LottoRules.levelName(it.level)}×${it.count}" }}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (enabled) "跟踪已开启：等下一期开奖（每周一/三/六 21:25 后），汪苏蕊打开 APP 即自动比对"
                        else "开启开关，开始记录汪苏蕊的每一期；或先去「回放」页体验 10 年快进",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 每期明细
        if (recent.isNotEmpty()) {
            item {
                Text("每期明细（最近 ${recent.size} 期）", style = MaterialTheme.typography.titleMedium)
            }
            items(recent) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "第 ${r.num} 期 · ${r.time}",
                                fontWeight = FontWeight.Bold
                            )
                            val netThis = r.win - r.cost
                            Text(
                                if (netThis >= 0) "+${formatMoney(netThis)}" else formatMoney(netThis),
                                color = if (netThis >= 0) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "开奖：" + r.drawFront.joinToString(" ") { it.toString().padStart(2, '0') } +
                                " + " + r.drawBack.joinToString(" ") { it.toString().padStart(2, '0') },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (r.wonNotes.isEmpty()) "${r.notes} 注全空（投入 ${formatMoney(r.cost)}）"
                            else r.wonNotes.joinToString("；") { w ->
                                "${LottoRules.levelName(w.level)}" +
                                    (if (r.mode == SimMode.FIXED) " ×${r.notes} 注" else "") +
                                    " " + LottoRules.formatNote(w.front.toSet(), w.back.toSet()) +
                                    " 到手 +${formatMoney(LottoRules.netOfTax(w.amount))}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
