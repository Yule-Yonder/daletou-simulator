package com.yy.lottosim.lotto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 模拟模式 */
enum class SimMode { RANDOM, FIXED }

/** 模拟配置 */
data class SimConfig(
    val years: Int = 10,           // 回放年限（跟踪模式忽略）
    val notesPerDraw: Int = 1000,  // 每期购买注数
    val mode: SimMode = SimMode.RANDOM,
    val fixedFront: Set<Int> = emptySet(), // 守号前区（mode=FIXED 时生效）
    val fixedBack: Set<Int> = emptySet()   // 守号后区
)

/** 一/二等奖命中明细 */
data class BigWin(
    val num: String,
    val time: String,
    val level: Int,
    val amountPerNote: Long, // 单注奖金
    val count: Int           // 当期命中注数
)

/** 模拟统计结果（回放与跟踪共用结构） */
data class SimResult(
    val totalDraws: Int,        // 参与期数
    val totalNotes: Long,       // 总注数
    val totalCost: Long,        // 总投入（元）
    val totalWin: Long,         // 总中奖（元）
    val prizeCounts: IntArray,  // 下标 1..9：各奖级命中注数
    val prizeAmounts: LongArray,// 下标 1..9：各奖级总奖金（元）
    val bigWins: List<BigWin>,  // 一/二等奖明细
    val elapsedMs: Long
) {
    val net: Long get() = totalWin - totalCost
    val returnRate: Double get() = if (totalCost > 0) totalWin.toDouble() / totalCost else 0.0

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** 中奖注明细（跟踪模式展示用；注数通常很少） */
data class WonNote(
    val front: List<Int>,
    val back: List<Int>,
    val level: Int,
    val amount: Long
)

/** 跟踪模式的单期记录 */
data class TrackRecord(
    val num: String,
    val time: String,
    val drawFront: List<Int>,
    val drawBack: List<Int>,
    val mode: SimMode,
    val notes: Int,             // 本期购买注数
    val cost: Long,
    val win: Long,
    val wonNotes: List<WonNote> // 本期全部中奖注
)

/**
 * 模拟引擎：历史回放（批量快速跑完）+ 持续跟踪（逐期比对真实开奖）
 */
class SimulationEngine(context: Context) {

    private val prefs = context.getSharedPreferences("lotto_sim", Context.MODE_PRIVATE)

    // ---------- 历史回放 ----------

    /**
     * 对给定开奖序列逐期模拟购买并统计。
     * 机选：每注独立随机；守号：同一注重复 notesPerDraw 次（判定一次 × 注数）。
     * 每 50 期回调一次进度（done/total），支持取消（抛 CancellationException）。
     */
    suspend fun replay(
        draws: List<Draw>,
        config: SimConfig,
        random: Random = Random.Default,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): SimResult {
        require(draws.isNotEmpty()) { "没有可用的开奖数据" }
        require(config.notesPerDraw in 1..100_000) { "每期注数超出合理范围" }
        if (config.mode == SimMode.FIXED) {
            require(config.fixedFront.size == 5 && config.fixedBack.size == 2) { "守号号码不完整" }
        }

        val start = System.currentTimeMillis()
        val counts = IntArray(10)
        val amounts = LongArray(10)
        val bigWins = ArrayList<BigWin>()
        var totalNotes = 0L
        var totalCost = 0L
        var totalWin = 0L

        for ((index, draw) in draws.withIndex()) {
            val drawFront = draw.front.toSet()
            val drawBack = draw.back.toSet()
            // 本期各奖级命中注数（大奖单独记录明细）
            val levelCountThisDraw = IntArray(10)

            if (config.mode == SimMode.FIXED) {
                val level = LottoRules.judge(config.fixedFront, config.fixedBack, drawFront, drawBack)
                if (level > 0) {
                    levelCountThisDraw[level] = config.notesPerDraw
                    val amount = prizeOf(level, draw) * config.notesPerDraw
                    counts[level] += config.notesPerDraw
                    amounts[level] += amount
                    totalWin += amount
                }
            } else {
                repeat(config.notesPerDraw) {
                    val (f, b) = LottoRules.randomNote(random)
                    val level = LottoRules.judge(f, b, drawFront, drawBack)
                    if (level > 0) {
                        levelCountThisDraw[level]++
                        val amount = prizeOf(level, draw)
                        counts[level]++
                        amounts[level] += amount
                        totalWin += amount
                    }
                }
            }

            // 一/二等奖按当期实际命中数记录明细
            for (level in 1..2) {
                val c = levelCountThisDraw[level]
                if (c > 0) {
                    bigWins.add(
                        BigWin(draw.num, draw.time, level, prizeOf(level, draw), c)
                    )
                }
            }

            totalNotes += config.notesPerDraw
            totalCost += config.notesPerDraw * LottoRules.NOTE_PRICE
            if (index % 50 == 49 || index == draws.lastIndex) {
                onProgress(index + 1, draws.size)
                kotlinx.coroutines.yield()
            }
        }

        val result = SimResult(
            totalDraws = draws.size,
            totalNotes = totalNotes,
            totalCost = totalCost,
            totalWin = totalWin,
            prizeCounts = counts,
            prizeAmounts = amounts,
            bigWins = bigWins,
            elapsedMs = System.currentTimeMillis() - start
        )
        saveLastReplay(result, config)
        return result
    }

    /** 奖级 → 单注奖金：一/二等用当期实际值，其余固定值 */
    private fun prizeOf(level: Int, draw: Draw): Long = when (level) {
        1 -> draw.prize1
        2 -> draw.prize2
        else -> LottoRules.fixedPrize(level)
    }

    private fun saveLastReplay(result: SimResult, config: SimConfig) {
        val o = JSONObject()
            .put("years", config.years)
            .put("notes", config.notesPerDraw)
            .put("mode", config.mode.name)
            .put("draws", result.totalDraws)
            .put("cost", result.totalCost)
            .put("win", result.totalWin)
            .put("ms", result.elapsedMs)
        prefs.edit().putString("last_replay", o.toString()).apply()
    }

    // ---------- 持续跟踪 ----------

    /** 跟踪配置 */
    data class TrackConfig(
        val enabled: Boolean = false,
        val mode: SimMode = SimMode.RANDOM,
        val fixedFront: Set<Int> = emptySet(),
        val fixedBack: Set<Int> = emptySet(),
        val notesPerDraw: Int = 1000
    )

    fun loadTrackConfig(): TrackConfig {
        val raw = prefs.getString("track_config", null) ?: return TrackConfig()
        return runCatching {
            val o = JSONObject(raw)
            TrackConfig(
                enabled = o.optBoolean("enabled"),
                mode = if (o.optString("mode") == "FIXED") SimMode.FIXED else SimMode.RANDOM,
                fixedFront = o.optJSONArray("f")?.toIntSet() ?: emptySet(),
                fixedBack = o.optJSONArray("b")?.toIntSet() ?: emptySet(),
                notesPerDraw = o.optInt("notes", 1000)
            )
        }.getOrDefault(TrackConfig())
    }

    fun saveTrackConfig(config: TrackConfig) {
        val o = JSONObject()
            .put("enabled", config.enabled)
            .put("mode", config.mode.name)
            .put("f", JSONArray(config.fixedFront.sorted()))
            .put("b", JSONArray(config.fixedBack.sorted()))
            .put("notes", config.notesPerDraw)
        prefs.edit().putString("track_config", o.toString()).apply()
    }

    /** 全部跟踪记录（升序） */
    fun loadTrackRecords(): List<TrackRecord> {
        val raw = prefs.getString("track_records", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val won = o.optJSONArray("won") ?: JSONArray()
                TrackRecord(
                    num = o.optString("n"),
                    time = o.optString("t"),
                    drawFront = o.optJSONArray("df")?.toIntList() ?: emptyList(),
                    drawBack = o.optJSONArray("db")?.toIntList() ?: emptyList(),
                    mode = if (o.optString("m") == "FIXED") SimMode.FIXED else SimMode.RANDOM,
                    notes = o.optInt("c"),
                    cost = o.optLong("cost"),
                    win = o.optLong("win"),
                    wonNotes = (0 until won.length()).mapNotNull { j ->
                        val w = won.optJSONObject(j) ?: return@mapNotNull null
                        WonNote(
                            front = w.optJSONArray("f")?.toIntList() ?: emptyList(),
                            back = w.optJSONArray("b")?.toIntList() ?: emptyList(),
                            level = w.optInt("lv"),
                            amount = w.optLong("amt")
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 持续跟踪比对：对本地开奖数据中"上次比对之后"的每一期模拟购买并追加记录。
     * 首次开启（无记录）从当前最新期之后开始，不回补历史。
     * 返回本次新增的期数；config.enabled=false 时直接返回 0。
     */
    fun trackNewDraws(draws: List<Draw>, config: TrackConfig): List<TrackRecord> {
        if (!config.enabled || draws.isEmpty()) return emptyList()
        val existing = loadTrackRecords()
        val lastCompared = existing.maxOfOrNull { it.num }
            ?: draws.maxOfOrNull { it.num }
            ?: return emptyList() // 首次开启：以当前最新期为基线，等下一期
        val pending = draws.filter { it.num > lastCompared }
        if (pending.isEmpty()) return emptyList()

        val random = Random.Default
        val newRecords = pending.map { draw ->
            simulateOneDraw(draw, config, random)
        }
        persistTrackRecords(existing + newRecords)
        return newRecords
    }

    /** 单期模拟购买（跟踪模式）：返回该期记录（含中奖注明细） */
    private fun simulateOneDraw(draw: Draw, config: TrackConfig, random: Random): TrackRecord {
        val drawFront = draw.front.toSet()
        val drawBack = draw.back.toSet()
        val won = ArrayList<WonNote>()
        var win = 0L

        if (config.mode == SimMode.FIXED && config.fixedFront.size == 5 && config.fixedBack.size == 2) {
            val level = LottoRules.judge(config.fixedFront, config.fixedBack, drawFront, drawBack)
            if (level > 0) {
                val amount = prizeOf(level, draw) * config.notesPerDraw
                win += amount
                won.add(WonNote(config.fixedFront.sorted(), config.fixedBack.sorted(), level, amount))
            }
        } else {
            repeat(config.notesPerDraw) {
                val (f, b) = LottoRules.randomNote(random)
                val level = LottoRules.judge(f, b, drawFront, drawBack)
                if (level > 0) {
                    val amount = prizeOf(level, draw)
                    win += amount
                    won.add(WonNote(f.sorted(), b.sorted(), level, amount))
                }
            }
        }
        return TrackRecord(
            num = draw.num, time = draw.time,
            drawFront = draw.front, drawBack = draw.back,
            mode = config.mode, notes = config.notesPerDraw,
            cost = config.notesPerDraw * LottoRules.NOTE_PRICE,
            win = win, wonNotes = won
        )
    }

    private fun persistTrackRecords(records: List<TrackRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject()
                    .put("n", r.num).put("t", r.time)
                    .put("df", JSONArray(r.drawFront)).put("db", JSONArray(r.drawBack))
                    .put("m", r.mode.name).put("c", r.notes)
                    .put("cost", r.cost).put("win", r.win)
                    .put("won", JSONArray().apply {
                        r.wonNotes.forEach { w ->
                            put(JSONObject()
                                .put("f", JSONArray(w.front))
                                .put("b", JSONArray(w.back))
                                .put("lv", w.level)
                                .put("amt", w.amount))
                        }
                    })
            )
        }
        prefs.edit().putString("track_records", arr.toString()).apply()
    }

    /** 清空跟踪记录（保留配置） */
    fun clearTrackRecords() {
        prefs.edit().putString("track_records", null).apply()
    }

    // ---------- 跟踪累计统计 ----------

    fun trackSummary(records: List<TrackRecord>): SimResult {
        val counts = IntArray(10)
        val amounts = LongArray(10)
        val bigWins = ArrayList<BigWin>()
        var notes = 0L
        var cost = 0L
        var win = 0L
        for (r in records) {
            notes += r.notes
            cost += r.cost
            win += r.win
            for (w in r.wonNotes) {
                // 守号模式一条 WonNote 代表整期 notes 注；机选模式一条代表 1 注
                val cnt = if (r.mode == SimMode.FIXED) r.notes else 1
                counts[w.level] += cnt
                amounts[w.level] += w.amount
                if (w.level <= 2) {
                    bigWins.add(BigWin(r.num, r.time, w.level, w.amount / cnt, cnt))
                }
            }
        }
        return SimResult(
            totalDraws = records.size, totalNotes = notes,
            totalCost = cost, totalWin = win,
            prizeCounts = counts, prizeAmounts = amounts,
            bigWins = bigWins, elapsedMs = 0
        )
    }

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).mapNotNull { optInt(it) }

    private fun JSONArray.toIntSet(): Set<Int> = toIntList().toSet()
}
