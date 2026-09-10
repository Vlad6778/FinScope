package com.example.finscope.ml

import android.content.Context
import android.util.Log
import com.example.finscope.model.Transaction
import com.example.finscope.viewmodel.TransactionTypes
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

class FinancialForecaster(private val context: Context) {

    private var expenseInterpreter: Interpreter? = null
    private var incomeInterpreter: Interpreter? = null

    private val WINDOW_SIZE = 30
    private val FEATURE_COUNT = 5


    private val EXPENSE_MIN_VAL = 0.0f
    private val EXPENSE_MAX_VAL = 2961.27f

    private val INCOME_MIN_LOG = 0.0f
    private val INCOME_MAX_LOG = 7.7372f

    init {
        try {
            expenseInterpreter = Interpreter(loadModelFile("expense_forecast_model.tflite"))
            incomeInterpreter = Interpreter(loadModelFile("income_forecast_model.tflite"))
        } catch (e: Exception) {
            Log.e("FinancialForecaster", "Error loading models", e)
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(fileName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
    }

    fun forecastNext30Days(transactions: List<Transaction>, type: String): List<Float> {
        val isExpense = type == TransactionTypes.EXPENSE
        val interpreter = if (isExpense) expenseInterpreter else incomeInterpreter
        if (interpreter == null) return emptyList()


        val globalMin = if (isExpense) EXPENSE_MIN_VAL else INCOME_MIN_LOG
        val globalMax = if (isExpense) EXPENSE_MAX_VAL else INCOME_MAX_LOG
        val globalRange = if (globalMax - globalMin == 0f) 1f else globalMax - globalMin

        val dailyMap = aggregateByDayMap(transactions, type)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -WINDOW_SIZE)

        val inputWindow = ArrayList<FloatArray>()

        for (i in 0 until WINDOW_SIZE) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val time = calendar.timeInMillis
            val rawAmount = dailyMap[time] ?: 0f

            val processedAmount = if (isExpense) {
                val clipped = if (rawAmount > globalMax) globalMax else rawAmount
                clipped
            } else {
                // Логарифм
                ln(rawAmount.toDouble() + 1.0).toFloat()
            }

            val normAmount = (processedAmount - globalMin) / globalRange
            val features = calculateCyclicFeatures(calendar)

            inputWindow.add(floatArrayOf(normAmount, features[0], features[1], features[2], features[3]))
        }

        val inputBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SIZE * FEATURE_COUNT * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (features in inputWindow) {
            for (v in features) { inputBuffer.putFloat(v) }
        }

        val predictions = ArrayList<Float>()
        val forecastCalendar = Calendar.getInstance()
        forecastCalendar.set(Calendar.HOUR_OF_DAY, 0)
        forecastCalendar.set(Calendar.MINUTE, 0)
        forecastCalendar.set(Calendar.SECOND, 0)
        forecastCalendar.set(Calendar.MILLISECOND, 0)

        for (i in 0 until 30) {
            forecastCalendar.add(Calendar.DAY_OF_YEAR, 1)

            val outputBuffer = ByteBuffer.allocateDirect(1 * 1 * 4)
            outputBuffer.order(ByteOrder.nativeOrder())
            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val predictedNorm = outputBuffer.float

            val predictedVal = (predictedNorm * globalRange) + globalMin

            val finalAmount = if (isExpense) {
                predictedVal
            } else {
                (exp(predictedVal) - 1.0).toFloat()
            }

            val sanitizedAmount = max(0.0f, finalAmount)
            predictions.add(sanitizedAmount)

            inputWindow.removeAt(0)
            val newFeatures = calculateCyclicFeatures(forecastCalendar)
            inputWindow.add(floatArrayOf(predictedNorm, newFeatures[0], newFeatures[1], newFeatures[2], newFeatures[3]))

            inputBuffer.rewind()
            for (features in inputWindow) {
                for (v in features) { inputBuffer.putFloat(v) }
            }
        }

        return applyRecurringEvents(predictions, transactions, type)
    }

    private fun aggregateByDayMap(transactions: List<Transaction>, type: String): Map<Long, Float> {
        val filtered = transactions.filter { it.type == type }
        val map = HashMap<Long, Float>()
        val cal = Calendar.getInstance()
        filtered.forEach { tx ->
            cal.time = tx.date
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val key = cal.timeInMillis
            map[key] = (map[key] ?: 0f) + tx.amount.toFloat()
        }
        return map
    }

    private fun calculateCyclicFeatures(cal: Calendar): FloatArray {
        val dayOfWeekJava = cal.get(Calendar.DAY_OF_WEEK)
        val dayOfWeek = if (dayOfWeekJava == Calendar.SUNDAY) 6 else dayOfWeekJava - 2
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val dowSin = sin(2 * Math.PI * dayOfWeek / 7).toFloat()
        val dowCos = cos(2 * Math.PI * dayOfWeek / 7).toFloat()
        val domSin = sin(2 * Math.PI * dayOfMonth / 31).toFloat()
        val domCos = cos(2 * Math.PI * dayOfMonth / 31).toFloat()
        return floatArrayOf(dowSin, dowCos, domSin, domCos)
    }

    private fun applyRecurringEvents(mlForecast: List<Float>, history: List<Transaction>, type: String): List<Float> {
        if (type == TransactionTypes.EXPENSE) return mlForecast
        val recurringEvents = detectIncomeDays(history)
        val finalForecast = ArrayList<Float>()
        val calendar = Calendar.getInstance()
        for (i in mlForecast.indices) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            var amount = mlForecast[i]
            if (recurringEvents.containsKey(dayOfMonth)) {
                val realSalary = recurringEvents[dayOfMonth]!!
                if (realSalary > amount) amount = realSalary
            }
            finalForecast.add(amount)
        }
        return finalForecast
    }

    private fun detectIncomeDays(history: List<Transaction>): Map<Int, Float> {
        val incomes = history.filter { it.type == TransactionTypes.INCOME }
        if (incomes.isEmpty()) return emptyMap()
        val calendar = Calendar.getInstance()
        val dayGroups = incomes.groupBy {
            calendar.time = it.date
            calendar.get(Calendar.DAY_OF_MONTH)
        }
        val result = mutableMapOf<Int, Float>()
        for ((day, txs) in dayGroups) {
            val avg = txs.map { it.amount.toDouble() }.average().toFloat()
            if (avg > 4000f) result[day] = avg
        }
        return result
    }
}