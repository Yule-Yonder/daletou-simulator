package com.yy.lottosim.lotto

import kotlin.random.Random

/**
 * 大乐透规则与判奖核心（纯函数，无 Android 依赖）
 *
 * 规则：前区 5 个号码（01-35），后区 2 个号码（01-12），单注 2 元
 * 奖级（2026 现行规则，追加投注本工具不模拟）：
 *   一等奖 5+2 浮动 / 二等奖 5+1 浮动 / 三等奖 5+0 固定 10000
 *   四等奖 4+2 固定 3000 / 五等奖 4+1 固定 300 / 六等奖 3+2 固定 200
 *   七等奖 4+0 固定 100 / 八等奖 3+1 或 2+2 固定 15
 *   九等奖 3+0、2+1、1+2、0+2 固定 5
 */
object LottoRules {
    const val FRONT_MAX = 35
    const val BACK_MAX = 12
    const val FRONT_PICK = 5
    const val BACK_PICK = 2
    const val NOTE_PRICE = 2L

    /** 全部号码组合数：C(35,5) * C(12,2)，一等奖概率的倒数 */
    const val TOTAL_COMBINATIONS = 324_632L * 66L // 21,425,712

    /**
     * 中奖个税（偶然所得）：单注奖金 ≤ 10000 元免税（含），超过则全额按 20% 计税。
     * 口径依据：电脑彩票以单注奖金为一次中奖收入（财税〔1998〕12号及2024年四部门公告），
     * 兑奖机构代扣代缴，到手为税后。
     */
    const val TAX_RATE = 0.20
    const val TAX_FREE_LIMIT = 10_000L

    /** 单注奖金的个税额（免税返回 0） */
    fun taxOf(amountPerNote: Long): Long =
        if (amountPerNote > TAX_FREE_LIMIT) amountPerNote / 5 else 0L

    /** 单注奖金的税后到手金额 */
    fun netOfTax(amountPerNote: Long): Long = amountPerNote - taxOf(amountPerNote)

    /** 机选一注：前区 5 个（1-35 不重复）+ 后区 2 个（1-12 不重复） */
    fun randomNote(random: Random = Random.Default): Pair<Set<Int>, Set<Int>> {
        val front = (1..FRONT_MAX).shuffled(random).take(FRONT_PICK).toSet()
        val back = (1..BACK_MAX).shuffled(random).take(BACK_PICK).toSet()
        return front to back
    }

    /** 按命中数判定奖级：返回 0（未中奖）到 9（九等奖） */
    fun prizeLevel(frontHits: Int, backHits: Int): Int = when {
        frontHits == 5 && backHits == 2 -> 1
        frontHits == 5 && backHits == 1 -> 2
        frontHits == 5 -> 3
        frontHits == 4 && backHits == 2 -> 4
        frontHits == 4 && backHits == 1 -> 5
        frontHits == 3 && backHits == 2 -> 6
        frontHits == 4 -> 7
        (frontHits == 3 && backHits == 1) || (frontHits == 2 && backHits == 2) -> 8
        (frontHits == 3 && backHits == 0) ||
            (frontHits == 2 && backHits == 1) ||
            (frontHits == 1 && backHits == 2) ||
            (frontHits == 0 && backHits == 2) -> 9
        else -> 0
    }

    /** 三~九等奖固定单注奖金（元）；一、二等奖为浮动奖，取当期实际值 */
    fun fixedPrize(level: Int): Long = when (level) {
        3 -> 10_000L
        4 -> 3_000L
        5 -> 300L
        6 -> 200L
        7 -> 100L
        8 -> 15L
        9 -> 5L
        else -> 0L
    }

    /** 奖级中文名 */
    fun levelName(level: Int): String = when (level) {
        1 -> "一等奖"
        2 -> "二等奖"
        3 -> "三等奖"
        4 -> "四等奖"
        5 -> "五等奖"
        6 -> "六等奖"
        7 -> "七等奖"
        8 -> "八等奖"
        9 -> "九等奖"
        else -> "未中奖"
    }

    /** 号码格式化："03 24 25 26 35 + 07 09" */
    fun formatNote(front: Set<Int>, back: Set<Int>): String =
        front.sorted().joinToString(" ") { it.toString().padStart(2, '0') } +
            " + " +
            back.sorted().joinToString(" ") { it.toString().padStart(2, '0') }

    /**
     * 单注判奖：返回奖级 0-9
     */
    fun judge(noteFront: Set<Int>, noteBack: Set<Int>, drawFront: Set<Int>, drawBack: Set<Int>): Int {
        val f = noteFront.count { it in drawFront }
        val b = noteBack.count { it in drawBack }
        return prizeLevel(f, b)
    }
}
