package com.drinktoday.data

import android.content.Context
import com.drinktoday.util.DateX
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 全部数据都保存在单个 SharedPreferences 键里（JSON）。
 *
 * 选择这种方案而非 Room 的原因：
 *  - 单日记录规模极小（每天几条），读入内存只是一次性几 KB，常驻内存几乎可忽略；
 *  - 免去数据库进程、连接池与编译期注解处理，安装包与运行时开销更低；
 *  - 写入是整体覆盖，数据量小（按每天 10 条、十年也仅数百 KB），apply() 异步落盘不阻塞主线程。
 */
class DrinkStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** epochDay -> 当日记录（按时间升序） */
    private val days = HashMap<Long, ArrayList<DrinkRecord>>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var goalMl: Int = DEFAULT_GOAL
        private set

    /** 上次记录的水量，补录时作为默认值 */
    var lastAmountMl: Int = DEFAULT_AMOUNT
        private set

    /** 首页 4 个快捷按钮的水量，用户可分别调整 */
    var quickAmounts: IntArray = DEFAULT_QUICK_AMOUNTS.copyOf()
        private set

    init {
        load()
    }

    // ---------------------------------------------------------------- 读取

    fun records(epochDay: Long): List<DrinkRecord> = days[epochDay] ?: emptyList()

    fun total(epochDay: Long): Int {
        val list = days[epochDay] ?: return 0
        var sum = 0
        for (r in list) sum += r.amountMl
        return sum
    }

    fun count(epochDay: Long): Int = days[epochDay]?.size ?: 0

    fun hasRecord(epochDay: Long): Boolean = !days[epochDay].isNullOrEmpty()

    /** 全部记录条数（导出提示用） */
    fun recordCount(): Int = days.values.sumOf { it.size }

    fun summary(epochDay: Long, today: Long = DateX.todayEpochDay()): DaySummary =
        DaySummary(epochDay, total(epochDay), goalMl, today)

    /** 某月每天的饮水量，仅包含有记录的日期 */
    fun totalsOfMonth(year: Int, month: Int): Map<Long, Int> {
        val result = HashMap<Long, Int>()
        for ((day, list) in days) {
            val date = DateX.toDate(day)
            if (date.year == year && date.monthValue == month) {
                var sum = 0
                for (r in list) sum += r.amountMl
                if (sum > 0) result[day] = sum
            }
        }
        return result
    }

    /** 连续达标天数：今天未达标时从昨天开始数，避免清晨清零带来的挫败感 */
    fun currentStreak(today: Long = DateX.todayEpochDay()): Int {
        var day = if (total(today) >= goalMl) today else today - 1
        var streak = 0
        while (streak < MAX_STREAK_SCAN && total(day) >= goalMl) {
            streak++
            day--
        }
        return streak
    }

    // ---------------------------------------------------------------- 写入

    fun add(epochDay: Long, timeMillis: Long, amountMl: Int): DrinkRecord {
        val amount = amountMl.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        val record = DrinkRecord(
            id = System.currentTimeMillis() * 1000 + (idSeed++ % 1000),
            timeMillis = timeMillis,
            amountMl = amount
        )
        val list = days.getOrPut(epochDay) { ArrayList(4) }
        list.add(record)
        list.sortBy { it.timeMillis }
        lastAmountMl = amount
        persist()
        notifyChanged()
        return record
    }

    fun remove(epochDay: Long, id: Long): Boolean {
        val list = days[epochDay] ?: return false
        val removed = list.removeAll { it.id == id }
        if (list.isEmpty()) days.remove(epochDay)
        if (removed) {
            persist()
            notifyChanged()
        }
        return removed
    }

    fun setQuickAmount(index: Int, amountMl: Int) {
        if (index !in quickAmounts.indices) return
        val normalized = amountMl.coerceIn(MIN_QUICK_AMOUNT, MAX_QUICK_AMOUNT)
        if (quickAmounts[index] == normalized) return
        quickAmounts[index] = normalized
        persist()
        notifyChanged()
    }

    fun setGoal(ml: Int) {
        val normalized = ml.coerceIn(MIN_GOAL, MAX_GOAL)
        if (normalized == goalMl) return
        goalMl = normalized
        persist()
        notifyChanged()
    }

    // ---------------------------------------------------------------- 备份 / 同步

    /** 导出为与本地存储完全一致的 JSON，便于备份或云端同步。 */
    fun exportJson(): String = buildJson()

    /**
     * 合并导入：按记录 id 去重，不删除本地已有数据。
     * [applySettings] 为 true 时同时采用文件中的目标与快捷水量（用于「恢复备份」）；
     * 云端同步传 false，避免旧备份覆盖当前设置。
     */
    fun importJson(raw: String, applySettings: Boolean): ImportResult {
        val root = JSONObject(raw)
        if (applySettings) {
            goalMl = root.optInt(KEY_GOAL, goalMl).coerceIn(MIN_GOAL, MAX_GOAL)
            lastAmountMl = root.optInt(KEY_LAST_AMOUNT, lastAmountMl).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
            root.optJSONArray(KEY_QUICK)?.let { array ->
                for (i in 0 until QUICK_SLOT_COUNT) {
                    quickAmounts[i] = array.optInt(i, quickAmounts[i])
                        .coerceIn(MIN_QUICK_AMOUNT, MAX_QUICK_AMOUNT)
                }
            }
        }

        var added = 0
        var skipped = 0
        val dayJson = root.optJSONObject(KEY_DAYS)
        if (dayJson != null) {
            val keys = dayJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val epochDay = key.toLongOrNull() ?: continue
                val array = dayJson.optJSONArray(key) ?: continue

                val target = days[epochDay] ?: ArrayList(4)
                val knownIds = target.mapTo(HashSet(target.size)) { it.id }
                var changed = false
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val time = obj.optLong("t", 0L)
                    val amount = obj.optInt("a", 0)
                    if (id == 0L || time <= 0L || amount <= 0) {
                        skipped++
                        continue
                    }
                    if (!knownIds.add(id)) {
                        skipped++
                        continue
                    }
                    target.add(DrinkRecord(id, time, amount))
                    added++
                    changed = true
                }
                if (changed) {
                    target.sortBy { it.timeMillis }
                    days[epochDay] = target
                }
            }
        }

        if (added > 0 || applySettings) {
            persist()
            notifyChanged()
        }
        return ImportResult(added, skipped)
    }

    // ---------------------------------------------------------------- 监听

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        for (l in listeners) l()
    }

    // ---------------------------------------------------------------- 持久化

    private fun load() {
        val raw = prefs.getString(KEY_DATA, null) ?: return
        try {
            val root = JSONObject(raw)
            goalMl = root.optInt(KEY_GOAL, DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL)
            lastAmountMl = root.optInt(KEY_LAST_AMOUNT, DEFAULT_AMOUNT).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
            root.optJSONArray(KEY_QUICK)?.let { array ->
                val restored = IntArray(QUICK_SLOT_COUNT)
                for (i in 0 until QUICK_SLOT_COUNT) {
                    restored[i] = array.optInt(i, DEFAULT_QUICK_AMOUNTS[i])
                        .coerceIn(MIN_QUICK_AMOUNT, MAX_QUICK_AMOUNT)
                }
                quickAmounts = restored
            }
            val dayJson = root.optJSONObject(KEY_DAYS) ?: return
            val keys = dayJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val epochDay = key.toLongOrNull() ?: continue
                val array = dayJson.optJSONArray(key) ?: continue
                val list = ArrayList<DrinkRecord>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val amount = obj.optInt("a", 0)
                    val time = obj.optLong("t", 0L)
                    if (amount > 0 && time > 0L) {
                        list.add(DrinkRecord(obj.optLong("id", i.toLong()), time, amount))
                    }
                }
                if (list.isNotEmpty()) {
                    list.sortBy { it.timeMillis }
                    days[epochDay] = list
                }
            }
        } catch (e: Exception) {
            // 数据损坏时保持空状态而不是崩溃；随后任意写入都会覆盖为合法 JSON。
            days.clear()
        }
    }

    private fun buildJson(): String {
        val root = JSONObject()
        root.put(KEY_GOAL, goalMl)
        root.put(KEY_LAST_AMOUNT, lastAmountMl)
        val quickJson = JSONArray()
        for (value in quickAmounts) quickJson.put(value)
        root.put(KEY_QUICK, quickJson)
        val dayJson = JSONObject()
        for ((day, list) in days) {
            val array = JSONArray()
            for (r in list) {
                array.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("t", r.timeMillis)
                        .put("a", r.amountMl)
                )
            }
            if (array.length() > 0) dayJson.put(day.toString(), array)
        }
        root.put(KEY_DAYS, dayJson)
        return root.toString()
    }

    private fun persist() {
        prefs.edit().putString(KEY_DATA, buildJson()).apply()
    }

    companion object {
        const val MIN_GOAL = 500
        const val MAX_GOAL = 5000
        const val DEFAULT_GOAL = 2000
        const val MIN_AMOUNT = 10
        const val MAX_AMOUNT = 3000
        const val DEFAULT_AMOUNT = 200
        const val MIN_QUICK_AMOUNT = 50
        const val MAX_QUICK_AMOUNT = 1000
        const val QUICK_SLOT_COUNT = 4
        val DEFAULT_QUICK_AMOUNTS = intArrayOf(150, 200, 300, 500)
        private const val MAX_STREAK_SCAN = 3650

        private const val PREF_NAME = "drink_today_store"
        private const val KEY_DATA = "data"
        private const val KEY_GOAL = "goal"
        private const val KEY_LAST_AMOUNT = "lastAmount"
        private const val KEY_QUICK = "quick"
        private const val KEY_DAYS = "days"

        @Volatile
        private var instance: DrinkStore? = null

        fun get(context: Context): DrinkStore =
            instance ?: synchronized(this) {
                instance ?: DrinkStore(context).also { instance = it }
            }
    }

    private var idSeed = 0L
}
