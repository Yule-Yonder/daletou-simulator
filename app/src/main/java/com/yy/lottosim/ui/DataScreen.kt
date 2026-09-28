package com.yy.lottosim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yy.lottosim.lotto.Draw

/**
 * 数据页：同步状态管理 + 最新开奖浏览 + 说明
 */
@Composable
fun DataScreen(
    modifier: Modifier = Modifier,
    draws: List<Draw>,
    syncing: Boolean,
    syncMsg: String?,
    lastSync: Long,
    onSync: () -> Unit,
    onFullResync: () -> Unit
) {
    var showFullDialog by remember { mutableStateOf(false) }
    val newest = draws.sortedByDescending { it.num }.take(15)

    if (showFullDialog) {
        AlertDialog(
            onDismissRequest = { showFullDialog = false },
            title = { Text("全量重新同步？") },
            text = { Text("将清空本地缓存并重新下载全部约 2900 期历史（约 1 分钟），跟踪记录保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showFullDialog = false
                    onFullResync()
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showFullDialog = false }) { Text("取消") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("开奖数据", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "数据源：中国体彩网官方接口（超级大乐透）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val latest = draws.maxByOrNull { it.num }
                    Text(
                        "本地缓存 ${draws.size} 期" +
                            (latest?.let { " · 最新 第${it.num}期（${it.time}）" } ?: ""),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "最后同步：${formatTime(lastSync)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    syncMsg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSync, enabled = !syncing) {
                            Text(if (syncing) "同步中…" else "同步最新开奖")
                        }
                        OutlinedButton(onClick = { showFullDialog = true }, enabled = !syncing) {
                            Text("全量重新同步")
                        }
                    }
                }
            }
        }

        if (newest.isNotEmpty()) {
            item {
                Text("最新开奖（前 15 期）", style = MaterialTheme.typography.titleMedium)
            }
            items(newest) { d ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("第 ${d.num} 期", fontWeight = FontWeight.Bold)
                            Text(
                                d.time,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            d.front.joinToString(" ") { it.toString().padStart(2, '0') } +
                                "  +  " +
                                d.back.joinToString(" ") { it.toString().padStart(2, '0') },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "一等奖单注 ${formatMoney(d.prize1)} · 二等奖单注 ${formatMoney(d.prize2)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "· 一、二等奖为浮动奖金，统计使用当期实际单注奖金\n" +
                            "· 模拟不含追加投注（3 元/注玩法）\n" +
                            "· 本工具仅作概率模拟与娱乐，不构成任何购彩建议；理性购彩，量力而行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
