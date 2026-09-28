package com.yy.lottosim.lotto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** 一期开奖数据（已归一化，前区/后区升序） */
data class Draw(
    val num: String,      // 期号，如 "26110"
    val time: String,     // 开奖日期 yyyy-MM-dd
    val front: List<Int>, // 前区 5 个升序
    val back: List<Int>,  // 后区 2 个升序
    val prize1: Long,     // 当期一等奖单注实际奖金（元）；接口缺数据时用兜底估算
    val prize2: Long      // 当期二等奖单注实际奖金（元）
)

/**
 * 体彩官方历史开奖仓库：联网拉取 + SharedPreferences 本地缓存
 *
 * 数据源（中国体彩网官方接口）：
 *   https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85
 *
 * 外部调用三要素：
 *   - 超时：连接 10 秒 / 读取 30 秒（本文件常量）
 *   - 重试：网络失败重试 2 次（退避 1s、3s），仅手动触发同步时重试
 *   - 兜底：拉取失败抛异常由 UI 提示；本地缓存永不清空，断网仍可回放/查看旧数据
 */
class DrawRepository(private val context: Context) {

    companion object {
        private const val API =
            "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry"
        private const val GAME_NO = "85" // 超级大乐透
        private const val PAGE_SIZE = 100
        private const val MAX_RETRY = 2
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val PREFS = "lotto_data"
        private const val KEY_DRAWS = "draw_history"
        private const val KEY_SYNC_TIME = "last_sync_time"
        /** 全量翻页保护上限（当前全库约 2928 期 = 30 页，留余量） */
        private const val MAX_PAGES_GUARD = 60
        /** 一/二等奖浮动奖缺失时的估算兜底（近年常见量级） */
        const val DEFAULT_PRIZE1 = 8_000_000L
        const val DEFAULT_PRIZE2 = 120_000L
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** 读取本地缓存（按期号升序，旧→新）；无缓存返回空列表 */
    fun loadCached(): List<Draw> {
        val raw = prefs.getString(KEY_DRAWS, null) ?: return emptyList()
        return runCatching { parseDrawsJson(raw) }.getOrDefault(emptyList())
    }

    fun lastSyncTime(): Long = prefs.getLong(KEY_SYNC_TIME, 0L)

    /** 清空本地缓存（数据异常时兜底，之后 sync 会重新全量拉取） */
    fun clearCache() {
        prefs.edit().remove(KEY_DRAWS).apply()
    }

    /**
     * 同步入口：本地无数据 → 全量拉取；有数据 → 只拉本地最新期号之后的新期
     * 返回本次新增期数；无网络且无缓存时抛异常由调用方提示
     */
    @Throws(Exception::class)
    fun sync(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val cached = loadCached()
        val localLatest = cached.maxOfOrNull { it.num } // 期号字符串单调递增，可直接比较
        var fetched = mutableListOf<Draw>()
        var pageNo = 1

        while (pageNo <= MAX_PAGES_GUARD) {
            val page = fetchPageWithRetry(pageNo)
            val list = page.first
            if (list.isEmpty()) break
            fetched.addAll(list)
            onProgress(fetched.size, page.second)

            if (localLatest == null) {
                // 全量模式：翻到最后一页
                if (pageNo >= page.second) break
            } else {
                // 增量模式：本页最旧一期已不新于本地最新期 → 增量结束
                val oldest = list.minOf { it.num }
                if (oldest <= localLatest) break
                if (pageNo >= page.second) break
            }
            pageNo++
            Thread.sleep(300) // 翻页间隔，避免高频请求触发限流
        }

        if (cached.isNotEmpty()) {
            // 增量合并去重（按期号）
            val known = cached.map { it.num }.toSet()
            fetched = fetched.filter { it.num !in known }.toMutableList()
        }
        if (fetched.isEmpty()) return 0

        val merged = (cached + fetched).distinctBy { it.num }.sortedBy { it.num }
        persist(merged)
        return fetched.size
    }

    /** 网络失败重试（退避 1s / 3s）后仍失败则抛最后一次异常 */
    private fun fetchPageWithRetry(pageNo: Int): Pair<List<Draw>, Int> {
        var lastError: Exception? = null
        repeat(MAX_RETRY + 1) { attempt ->
            try {
                return fetchPage(pageNo)
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRY) Thread.sleep((attempt + 1) * 2000L - 1000L)
            }
        }
        throw lastError ?: IllegalStateException("未知网络错误")
    }

    /** 拉取一页；返回 (本期列表, 总页数) */
    private fun fetchPage(pageNo: Int): Pair<List<Draw>, Int> {
        val url = URL("$API?gameNo=$GAME_NO&provinceId=0&pageSize=$PAGE_SIZE&isVerify=1&pageNo=$pageNo")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            conn.setRequestProperty("Referer", "https://www.sporttery.cn/")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) throw IllegalStateException("接口返回 HTTP ${conn.responseCode}")

            val body = BufferedReader(
                InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
            ).use { it.readText() }
            return parsePage(body)
        } finally {
            conn.disconnect()
        }
    }

    /** 解析接口返回：value.list[] → Draw 列表 + 总页数 */
    private fun parsePage(body: String): Pair<List<Draw>, Int> {
        val root = JSONObject(body)
        if (!root.optBoolean("success", false)) {
            throw IllegalStateException("接口返回失败：${root.optString("errorMessage")}")
        }
        val value = root.optJSONObject("value") ?: return emptyList<Draw>() to 0
        val pages = value.optInt("pages", 0)
        val list = value.optJSONArray("list") ?: return emptyList<Draw>() to pages
        val result = ArrayList<Draw>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val draw = parseDraw(item) ?: continue
            result.add(draw)
        }
        return result to pages
    }

    /** 单期解析：号码串 "03 24 25 26 35 07 09" + 各奖级实际单注奖金 */
    private fun parseDraw(item: JSONObject): Draw? {
        val num = item.optString("lotteryDrawNum")
        val time = item.optString("lotteryDrawTime")
        val raw = item.optString("lotteryDrawResult", "")
        val parts = raw.trim().split(Regex("\\s+"))
        if (num.isEmpty() || parts.size < 7) return null
        val front = parts.subList(0, 5).mapNotNull { it.toIntOrNull() }.sorted()
        val back = parts.subList(5, 7).mapNotNull { it.toIntOrNull() }.sorted()
        if (front.size != 5 || back.size != 2) return null

        var p1 = DEFAULT_PRIZE1
        var p2 = DEFAULT_PRIZE2
        val levels = item.optJSONArray("prizeLevelList")
        if (levels != null) {
            for (i in 0 until levels.length()) {
                val lv = levels.optJSONObject(i) ?: continue
                val name = lv.optString("prizeLevel")
                // 只取基本投注奖金（跳过"一等奖(追加)"等）
                if (name == "一等奖") p1 = lv.optString("stakeAmountFormat").toLongOrNull() ?: p1
                if (name == "二等奖") p2 = lv.optString("stakeAmountFormat").toLongOrNull() ?: p2
            }
        }
        return Draw(num, time, front, back, p1, p2)
    }

    private fun persist(draws: List<Draw>) {
        val arr = JSONArray()
        for (d in draws) {
            arr.put(
                JSONObject()
                    .put("n", d.num)
                    .put("t", d.time)
                    .put("f", JSONArray(d.front))
                    .put("b", JSONArray(d.back))
                    .put("p1", d.prize1)
                    .put("p2", d.prize2)
            )
        }
        prefs.edit()
            .putString(KEY_DRAWS, arr.toString())
            .putLong(KEY_SYNC_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun parseDrawsJson(raw: String): List<Draw> {
        val arr = JSONArray(raw)
        val result = ArrayList<Draw>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val front = o.optJSONArray("f")?.let { j ->
                (0 until j.length()).mapNotNull { j.optInt(it) }
            } ?: continue
            val back = o.optJSONArray("b")?.let { j ->
                (0 until j.length()).mapNotNull { j.optInt(it) }
            } ?: continue
            if (front.size != 5 || back.size != 2) continue
            result.add(
                Draw(
                    num = o.optString("n"),
                    time = o.optString("t"),
                    front = front, back = back,
                    prize1 = o.optLong("p1", DEFAULT_PRIZE1),
                    prize2 = o.optLong("p2", DEFAULT_PRIZE2)
                )
            )
        }
        return result.sortedBy { it.num }
    }

    /** 裁剪最近 N 年的期次（按开奖日期） */
    fun recentYears(draws: List<Draw>, years: Int): List<Draw> {
        if (draws.isEmpty()) return draws
        val newestTime = draws.maxOf { it.time }
        val cutoff = runCatching {
            java.time.LocalDate.parse(newestTime).minusYears(years.toLong()).toString()
        }.getOrDefault("")
        return if (cutoff.isEmpty()) draws else draws.filter { it.time >= cutoff }
    }
}
