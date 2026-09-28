package com.yy.lottosim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yy.lottosim.lotto.LottoRules
import com.yy.lottosim.lotto.SimMode
import com.yy.lottosim.lotto.SimResult
import kotlin.random.Random

/**
 * 历史回放页：配置策略 → 快速跑完 N 年真实开奖 → 出统计报告
 */
@Composable
fun ReplayScreen(
    modifier: Modifier = Modifier,
    drawsCount: Int,
    years: Int,
    onYearsChange: (Int) -> Unit,
    notes: Int,
    onNotesChange: (Int) -> Unit,
    mode: SimMode,
    onModeChange: (SimMode) -> Unit,
    fixedFront: Set<Int>,
    fixedBack: Set<Int>,
    onFixedChange: (Set<Int>, Set<Int>) -> Unit,
    running: Boolean,
    progress: Float,
    result: SimResult?,
    error: String?,
    onRun: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("历史回放", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "用真实历史开奖号码，快速跑完你设定的购彩策略，看看十年下来到底是赚是亏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- 策略配置 ----
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("回放年限", style = MaterialTheme.typography.titleMedium)
                    ChipRow(
                        options = listOf(1, 3, 5, 10),
                        selected = years,
                        label = { "$it 年" },
                        enabled = !running
                    ) { onYearsChange(it) }

                    Text("每期购买注数", style = MaterialTheme.typography.titleMedium)
                    ChipRow(
                        options = listOf(100, 500, 1000, 2000),
                        selected = notes,
                        label = { "$it 注" },
                        enabled = !running
                    ) { onNotesChange(it) }

                    Text("选号方式", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == SimMode.RANDOM,
                            onClick = { if (!running) onModeChange(SimMode.RANDOM) },
                            label = { Text("机选（每注随机）") },
                            enabled = !running
                        )
                        FilterChip(
                            selected = mode == SimMode.FIXED,
                            onClick = { if (!running) onModeChange(SimMode.FIXED) },
                            label = { Text("守号（固定一组）") },
                            enabled = !running
                        )
                    }

                    if (mode == SimMode.FIXED) {
                        NumberPicker(
                            fixedFront = fixedFront,
                            fixedBack = fixedBack,
                            enabled = !running,
                            onChange = onFixedChange
                        )
                    }

                    Text(
                        "每注 2 元 · 每期投入 ${notes * 2} 元 · 本地开奖数据 $drawsCount 期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- 运行 ----
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onRun,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  模拟中 ${(progress * 100).toInt()}%")
                    } else {
                        Text("开始模拟 $years 年 × 每期 $notes 注")
                    }
                }
                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ---- 报告 ----
        if (result != null) {
            item { ReportCard(result, mode, fixedFront, fixedBack) }
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { if (enabled) onSelect(opt) },
                label = { Text(label(opt)) },
                enabled = enabled
            )
        }
    }
}

/** 守号号码选择：前区 35 球 + 后区 12 球 + 机选一注 */
@Composable
private fun NumberPicker(
    fixedFront: Set<Int>,
    fixedBack: Set<Int>,
    enabled: Boolean,
    onChange: (Set<Int>, Set<Int>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "我的守号（前区选 ${fixedFront.size}/5，后区选 ${fixedBack.size}/2）",
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    val (f, b) = LottoRules.randomNote(Random(System.currentTimeMillis()))
                    onChange(f, b)
                },
                enabled = enabled
            ) { Text("机选一注") }
        }
        BallGrid(
            range = 1..35,
            maxPick = 5,
            picked = fixedFront,
            enabled = enabled,
            ballColor = BallColor.FRONT
        ) { newSet -> onChange(newSet, fixedBack) }
        BallGrid(
            range = 1..12,
            maxPick = 2,
            picked = fixedBack,
            enabled = enabled,
            ballColor = BallColor.BACK
        ) { newSet -> onChange(fixedFront, newSet) }
    }
}

private enum class BallColor { FRONT, BACK }

/** 号码球网格：手动按 7 个一行排版（不依赖 LazyGrid，避免嵌套滚动问题） */
@Composable
private fun BallGrid(
    range: IntRange,
    maxPick: Int,
    picked: Set<Int>,
    enabled: Boolean,
    ballColor: BallColor,
    onPickChange: (Set<Int>) -> Unit
) {
    val chunks = range.toList().chunked(7)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chunks.forEach { rowNums ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowNums.forEach { n ->
                    val isPicked = n in picked
                    val base = when (ballColor) {
                        BallColor.FRONT -> MaterialTheme.colorScheme.primary
                        BallColor.BACK -> MaterialTheme.colorScheme.secondary
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isPicked) base else Color.Transparent,
                                CircleShape
                            )
                            .border(1.5.dp, base, CircleShape)
                            .clickable(enabled = enabled) {
                                val next = when {
                                    n in picked -> picked - n
                                    picked.size < maxPick -> picked + n
                                    else -> picked
                                }
                                onPickChange(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            n.toString().padStart(2, '0'),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPicked) {
                                if (ballColor == BallColor.FRONT) Color.White
                                else Color(0xFF3E2723)
                            } else base
                        )
                    }
                }
            }
        }
    }
}

/** 模拟结果报告卡 */
@Composable
fun ReportCard(
    result: SimResult,
    mode: SimMode,
    fixedFront: Set<Int>,
    fixedBack: Set<Int>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("模拟报告", style = MaterialTheme.typography.titleLarge)
            Text(
                if (mode == SimMode.FIXED)
                    "守号 ${LottoRules.formatNote(fixedFront, fixedBack)}"
                else "机选模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 大结论
            val net = result.net
            Text(
                text = if (net >= 0) "净赚 ${formatMoney(net)}！" else "净亏 ${formatMoney(net)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (net >= 0) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error
            )

            // 概览
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("参与期数", "${result.totalDraws} 期")
                StatCell("总购买", "${formatCount(result.totalNotes)} 注")
                StatCell("总投入", formatMoney(result.totalCost))
                StatCell("总中奖", formatMoney(result.totalWin))
            }
            Text(
                "回报率 ${(result.returnRate * 100).toFixed(1)}% · 耗时 ${(result.elapsedMs / 1000.0).toFixed(1)} 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // 奖级明细
            Text("中奖明细", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (level in 1..9) {
                    val cnt = result.prizeCounts[level]
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            LottoRules.levelName(level),
                            color = if (cnt > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (cnt > 0) "${cnt} 注 · ${formatMoney(result.prizeAmounts[level])}"
                            else "—",
                            color = if (cnt > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 大奖清单
            if (result.bigWins.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "🎉 中大奖了（一/二等奖）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                result.bigWins.forEach { bw ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${bw.num} 期（${bw.time}）",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${LottoRules.levelName(bw.level)} ×${bw.count} 注 · ${formatMoney(bw.amountPerNote * bw.count)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                HorizontalDivider()
                Text(
                    "💥 ${result.totalDraws} 期 × ${formatCount(result.totalNotes)} 注，一次一/二等奖都没摸到。\n" +
                        "（一等奖单注概率 1/${formatCount(LottoRules.TOTAL_COMBINATIONS)}，这是常态）",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

private fun Double.toFixed(digits: Int): String = String.format("%.${digits}f", this)
