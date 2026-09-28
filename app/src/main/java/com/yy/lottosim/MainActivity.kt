package com.yy.lottosim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yy.lottosim.lotto.Draw
import com.yy.lottosim.lotto.DrawRepository
import com.yy.lottosim.lotto.SimConfig
import com.yy.lottosim.lotto.SimMode
import com.yy.lottosim.lotto.SimulationEngine
import com.yy.lottosim.lotto.TrackRecord
import com.yy.lottosim.ui.DataScreen
import com.yy.lottosim.ui.LottoTheme
import com.yy.lottosim.ui.ReplayScreen
import com.yy.lottosim.ui.TrackScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LottoTheme {
                LottoApp()
            }
        }
    }
}

private val TABS = listOf("回放", "跟踪", "数据")

@Composable
fun LottoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { DrawRepository(context) }
    val engine = remember { SimulationEngine(context) }

    // ---- 全局数据状态 ----
    var draws by remember { mutableStateOf<List<Draw>>(emptyList()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    // ---- 共享购彩策略（回放与跟踪共用）----
    var years by remember { mutableIntStateOf(10) }
    var notes by remember { mutableIntStateOf(1000) }
    var mode by remember { mutableStateOf(SimMode.RANDOM) }
    var fixedFront by remember { mutableStateOf(setOf<Int>()) }
    var fixedBack by remember { mutableStateOf(setOf<Int>()) }

    // ---- 回放状态 ----
    var replayRunning by remember { mutableStateOf(false) }
    var replayProgress by remember { mutableFloatStateOf(0f) }
    var replayResult by remember { mutableStateOf<com.yy.lottosim.lotto.SimResult?>(null) }
    var replayError by remember { mutableStateOf<String?>(null) }

    // ---- 跟踪状态 ----
    var trackEnabled by remember { mutableStateOf(false) }
    var trackRecords by remember { mutableStateOf<List<TrackRecord>>(emptyList()) }
    var trackMsg by remember { mutableStateOf<String?>(null) }

    /** 同步开奖数据（增量/全量），成功后按需自动比对跟踪 */
    fun syncData(full: Boolean = false, silent: Boolean = false, thenTrack: Boolean = true) {
        if (syncing) return
        scope.launch {
            syncing = true
            if (!silent) syncMsg = null
            try {
                val added = withContext(Dispatchers.IO) {
                    if (full) repo.clearCache()
                    repo.sync()
                }
                draws = repo.loadCached()
                if (!silent) syncMsg = "同步成功，新增 $added 期（本地共 ${draws.size} 期）"
                // 自动比对跟踪：使用持久化的跟踪配置（避免内存默认值覆盖用户守号策略）
                val cfg = engine.loadTrackConfig()
                if (thenTrack && cfg.enabled && draws.isNotEmpty()) {
                    val newRecords = withContext(Dispatchers.Default) {
                        engine.trackNewDraws(draws, cfg)
                    }
                    if (newRecords.isNotEmpty()) {
                        trackRecords = engine.loadTrackRecords()
                        trackMsg = "已比对 ${newRecords.size} 期最新开奖"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                draws = repo.loadCached() // 兜底：断网/失败仍展示本地缓存
                syncMsg = "同步失败：${e.message ?: "网络异常"}，已回退本地缓存（${draws.size} 期）"
            } finally {
                syncing = false
            }
        }
    }

    fun saveTrackConfig(enabled: Boolean = trackEnabled) {
        engine.saveTrackConfig(
            SimulationEngine.TrackConfig(
                enabled = enabled, mode = mode,
                fixedFront = fixedFront, fixedBack = fixedBack,
                notesPerDraw = notes
            )
        )
    }

    fun runReplay() {
        if (draws.isEmpty()) {
            replayError = "还没有开奖数据，请先到「数据」页同步"
            return
        }
        if (mode == SimMode.FIXED && (fixedFront.size != 5 || fixedBack.size != 2)) {
            replayError = "守号模式需要选满前区 5 个 + 后区 2 个号码"
            return
        }
        scope.launch {
            replayRunning = true
            replayProgress = 0f
            replayResult = null
            replayError = null
            try {
                val subset = repo.recentYears(draws, years)
                val config = SimConfig(years, notes, mode, fixedFront, fixedBack)
                replayResult = withContext(Dispatchers.Default) {
                    engine.replay(subset, config) { done, total ->
                        replayProgress = done.toFloat() / total
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                replayError = "模拟失败：${e.message}"
            } finally {
                replayRunning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        draws = repo.loadCached()
        trackEnabled = engine.loadTrackConfig().enabled
        trackRecords = engine.loadTrackRecords()
        // 首次打开：无缓存则自动全量同步；有缓存则静默增量 + 自动比对跟踪
        launch { syncData(full = draws.isEmpty(), silent = draws.isNotEmpty()) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            when (i) {
                                0 -> Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                1 -> Icon(Icons.Filled.List, contentDescription = null)
                                else -> Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> ReplayScreen(
                modifier = Modifier.padding(padding),
                drawsCount = draws.size,
                years = years, onYearsChange = { years = it },
                notes = notes, onNotesChange = { notes = it },
                mode = mode, onModeChange = { mode = it },
                fixedFront = fixedFront, fixedBack = fixedBack,
                onFixedChange = { f, b -> fixedFront = f; fixedBack = b },
                running = replayRunning, progress = replayProgress,
                result = replayResult, error = replayError,
                onRun = { runReplay() }
            )
            1 -> TrackScreen(
                modifier = Modifier.padding(padding),
                enabled = trackEnabled,
                onEnabledChange = { en ->
                    trackEnabled = en
                    if (en && (mode == SimMode.FIXED && (fixedFront.size != 5 || fixedBack.size != 2))) {
                        trackMsg = "守号未选满，跟踪将按机选模式比对"
                    }
                    saveTrackConfig(en)
                },
                mode = mode,
                fixedFront = fixedFront, fixedBack = fixedBack,
                notes = notes,
                records = trackRecords,
                msg = trackMsg,
                syncing = syncing,
                onSyncNow = {
                    trackMsg = null
                    syncData(thenTrack = true)
                },
                onClear = {
                    engine.clearTrackRecords()
                    trackRecords = emptyList()
                    trackMsg = "已清空跟踪记录"
                },
                engine = engine
            )
            else -> DataScreen(
                modifier = Modifier.padding(padding),
                draws = draws,
                syncing = syncing,
                syncMsg = syncMsg,
                lastSync = repo.lastSyncTime(),
                onSync = { syncData(full = false, silent = false) },
                onFullResync = { syncData(full = true) }
            )
        }
    }
}
