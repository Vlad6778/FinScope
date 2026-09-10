package com.example.finscope

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.finscope.databinding.FragmentAnalyticsBinding
import com.example.finscope.ml.FinancialForecaster
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import com.example.finscope.viewmodel.TransactionTypes
import com.example.finscope.viewmodel.TrendData
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.renderer.CombinedChartRenderer
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs

class AnalyticsFragment : Fragment(), OnChartValueSelectedListener {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }

    private lateinit var forecaster: FinancialForecaster

    private var startDate: Date = Date()
    private var endDate: Date = Date()
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var categoryData: Map<String, Int> = emptyMap()
    private var allCategoriesList: List<Category> = emptyList()

    private var currentPeriodName = "місяця"
    private var currentUserBalance: Float = 0f

    private val OTHER_CATEGORY_LABEL = "Інше (дрібні)"

    private var currentTrendData: TrendData? = null

    // Сохраняем последние данные прогноза
    private var lastForecastExp: Float? = null
    private var lastProjectedBalance: Float? = null
    private var lastForecastEndDate: Date? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        forecaster = FinancialForecaster(requireContext())

        setupChartsStyles()
        setupEventHandlers()

        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
            allCategoriesList = categories
        }

        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                currentUserBalance = user.balance.toFloat()
                if (binding.combinedChart.data != null) {
                    loadData()
                }
            }
        }

        observeViewModel()

        binding.toggleGroupPeriod.check(R.id.btnMonth)
        setPeriodMonth()
    }

    private fun setupEventHandlers() {
        binding.toggleGroupPeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnWeek -> setPeriodWeek()
                    R.id.btnMonth -> setPeriodMonth()
                    R.id.btnYear -> setPeriodYear()
                }
            }
        }

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }
        binding.pieChartExpenses.setOnChartValueSelectedListener(this)
        binding.combinedChart.setOnChartValueSelectedListener(this)
    }

    private fun observeViewModel() {
        viewModel.expenseTrend.observe(viewLifecycleOwner) { trendData ->
            currentTrendData = trendData
            updateSummaryText(null, null, null, onlyUpdateTrend = true)
        }
    }

    private fun loadData() {
        binding.tvSelectedDateRange.text = "${dateFormatter.format(startDate)} - ${dateFormatter.format(endDate)}"

        currentTrendData = null
        lastForecastExp = null
        lastProjectedBalance = null
        lastForecastEndDate = null

        binding.tvTrendSummary.visibility = View.GONE

        viewModel.getTransactionsByPeriod(startDate, endDate).observe(viewLifecycleOwner) { transactions ->
            updatePieChart(transactions)
            updateSmartInsights(transactions)

            val today = Calendar.getInstance()
            clearTime(today)
            val isHistorical = endDate.before(today.time)
            val isLongPeriod = currentPeriodName == "року" ||
                    TimeUnit.MILLISECONDS.toDays(endDate.time - startDate.time) > 40

            viewModel.allTransactionsForHistory.observe(viewLifecycleOwner) { allHistory ->
                if (allHistory.isNullOrEmpty() || isHistorical) {
                    updateCombinedChart(transactions, emptyList(), emptyList(), isLongPeriod)
                    updateSummaryText(null, null, null)
                } else {
                    if (isLongPeriod) {
                        updateCombinedChart(transactions, emptyList(), emptyList(), isLongPeriod)
                        updateSummaryText(null, null, null)
                    } else {
                        lifecycleScope.launch(Dispatchers.Default) {
                            val predictedExpense = forecaster.forecastNext30Days(allHistory, TransactionTypes.EXPENSE)
                            val predictedIncome = forecaster.forecastNext30Days(allHistory, TransactionTypes.INCOME)

                            var forecastSumExp = 0f
                            var forecastSumInc = 0f
                            val calcCal = Calendar.getInstance()
                            clearTime(calcCal)

                            for (i in predictedExpense.indices) {
                                calcCal.add(Calendar.DAY_OF_YEAR, 1)
                                if (calcCal.time.after(endDate)) break
                                forecastSumExp += predictedExpense[i]
                                forecastSumInc += predictedIncome[i]
                            }

                            val projectedBalance = currentUserBalance + forecastSumInc - forecastSumExp

                            withContext(Dispatchers.Main) {
                                updateCombinedChart(transactions, predictedExpense, predictedIncome, isLongPeriod)

                                lastForecastExp = forecastSumExp
                                lastProjectedBalance = projectedBalance
                                lastForecastEndDate = endDate

                                updateSummaryText(forecastSumExp, projectedBalance, endDate)
                            }
                        }
                    }
                }
            }
        }

        viewModel.calculateExpenseTrend(startDate, endDate)
    }

    private fun updateSummaryText(
        forecastExp: Float?,
        projectedBalance: Float?,
        forecastEndDate: Date?,
        onlyUpdateTrend: Boolean = false
    ) {
        val fExp = if (onlyUpdateTrend) lastForecastExp else forecastExp
        val pBal = if (onlyUpdateTrend) lastProjectedBalance else projectedBalance
        val fEnd = if (onlyUpdateTrend) lastForecastEndDate else forecastEndDate

        val sb = SpannableStringBuilder()

        // 1. ТРЕНД
        if (currentTrendData != null && currentTrendData!!.percentageChange.isFinite()) {
            val data = currentTrendData!!
            val arrow = if (data.isIncrease) "⬆" else "⬇"
            val colorRes = if (data.isIncrease) R.color.red else R.color.green
            val colorInt = try {
                ContextCompat.getColor(requireContext(), colorRes)
            } catch (e: Exception) {
                if (data.isIncrease) Color.RED else Color.GREEN
            }

            val startIdx = sb.length
            val formatPattern = if (currentPeriodName == "року") "dd.MM.yyyy" else "dd.MM"
            val dateLabel = SimpleDateFormat(formatPattern, Locale.getDefault())

            val rangeStr = " (vs ${dateLabel.format(data.dateStart)} - ${dateLabel.format(data.dateEnd)})"

            sb.append(String.format("%s %.1f%% %s", arrow, data.percentageChange, rangeStr))

            sb.setSpan(ForegroundColorSpan(colorInt), startIdx, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), startIdx, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 2. ПРОГНОЗ
        if (fExp != null && fExp > 0 && fEnd != null) {
            if (sb.isNotEmpty()) sb.append("\n\n")

            val greyStart = sb.length

            val periodLabel = when(currentPeriodName) {
                "тижня" -> "до кінця тижня"
                "місяця" -> "до кінця місяця"
                else -> "до ${dateFormatter.format(fEnd)}"
            }

            sb.append("Прогноз витрат $periodLabel:\n")
            sb.append("~${String.format("%.0f", fExp)} ₴")

            sb.setSpan(ForegroundColorSpan(Color.DKGRAY), greyStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (pBal != null) {
                sb.append("\n\n")
                val balanceSectionStart = sb.length

                sb.append("Очікуваний баланс:\n")
                sb.setSpan(ForegroundColorSpan(Color.DKGRAY), balanceSectionStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                val balanceValStart = sb.length
                sb.append("~${String.format("%.0f", pBal)} ₴")
                sb.setSpan(ForegroundColorSpan(Color.BLACK), balanceValStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), balanceValStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                val diff = pBal - currentUserBalance
                if (abs(diff) >= 1) {
                    sb.append(" ")
                    val diffStart = sb.length

                    val sign = if (diff > 0) "+" else ""
                    val diffStr = String.format("(%s%.0f ₴)", sign, diff)
                    sb.append(diffStr)

                    val diffColor = if (diff >= 0) ContextCompat.getColor(requireContext(), R.color.green) else Color.RED
                    sb.setSpan(ForegroundColorSpan(diffColor), diffStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        if (sb.isNotEmpty()) {
            binding.tvTrendSummary.text = sb
            binding.tvTrendSummary.visibility = View.VISIBLE
        } else {
            binding.tvTrendSummary.visibility = View.GONE
        }
    }

    private fun updateCombinedChart(
        transactions: List<Transaction>,
        forecastExpense: List<Float>,
        forecastIncome: List<Float>,
        isLongPeriod: Boolean
    ) {
        if (transactions.isEmpty() && forecastExpense.isEmpty()) {
            binding.combinedChart.clear()
            binding.combinedChart.invalidate()
            return
        }

        val groupingPattern = if (isLongPeriod) "MM.yyyy" else "dd.MM.yyyy"
        val labelPattern = if (isLongPeriod) "LLL" else "dd.MM"
        val groupingFormat = SimpleDateFormat(groupingPattern, Locale.getDefault())
        val labelFormat = SimpleDateFormat(labelPattern, Locale("uk", "UA"))

        val groupedData = transactions.groupBy { groupingFormat.format(it.date) }
        val sortedKeys = groupedData.keys.sortedBy {
            try { groupingFormat.parse(it)?.time ?: 0L } catch (e: Exception) { 0L }
        }

        val labels = ArrayList<String>()
        val barEntriesIncome = ArrayList<BarEntry>()
        val barEntriesExpense = ArrayList<BarEntry>()
        val lineEntries = ArrayList<Entry>()
        var index = 0f

        var changeInPeriod = 0f
        val todayCal = Calendar.getInstance()
        clearTime(todayCal)
        val tomorrow = todayCal.time.time + 86400000

        transactions.forEach { tx ->
            if (tx.date.time < tomorrow) {
                changeInPeriod += if (tx.type == TransactionTypes.INCOME) tx.amount.toFloat() else -tx.amount.toFloat()
            }
        }
        var runningBalance = currentUserBalance - changeInPeriod

        // Історія
        sortedKeys.forEach { dateKey ->
            var labelText = dateKey
            try {
                val dateObj = groupingFormat.parse(dateKey)
                if (dateObj != null) labelText = labelFormat.format(dateObj)
            } catch (e: Exception) { }
            labels.add(labelText.replaceFirstChar { it.uppercase() })

            val txs = groupedData[dateKey] ?: emptyList()
            val income = txs.filter { it.type == TransactionTypes.INCOME }.sumOf { it.amount.toDouble() }.toFloat()
            val expense = txs.filter { it.type == TransactionTypes.EXPENSE }.sumOf { it.amount.toDouble() }.toFloat()

            barEntriesIncome.add(BarEntry(index, income))
            barEntriesExpense.add(BarEntry(index, expense))
            runningBalance += (income - expense)
            index++
        }

        // Прогноз
        if (!isLongPeriod && endDate.after(todayCal.time)) {
            if (index > 0) lineEntries.add(Entry(index - 0.5f, runningBalance))
            else lineEntries.add(Entry(0f, runningBalance))

            val forecastCal = Calendar.getInstance()
            clearTime(forecastCal)

            for (i in 0 until forecastExpense.size) {
                forecastCal.add(Calendar.DAY_OF_YEAR, 1)
                if (forecastCal.time.after(endDate)) break

                labels.add(labelFormat.format(forecastCal.time))
                val pInc = if (i < forecastIncome.size) forecastIncome[i] else 0f
                val pExp = if (i < forecastExpense.size) forecastExpense[i] else 0f
                runningBalance += (pInc - pExp)

                lineEntries.add(Entry(index, runningBalance))
                barEntriesIncome.add(BarEntry(index, 0f))
                barEntriesExpense.add(BarEntry(index, 0f))
                index++
            }
        }

        val combinedData = CombinedData()
        val setIncome = BarDataSet(barEntriesIncome, "Доходи").apply {
            color = Color.parseColor("#43A047")
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.LEFT
        }
        val setExpense = BarDataSet(barEntriesExpense, "Витрати").apply {
            color = Color.parseColor("#E53935")
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.LEFT
        }
        val barData = BarData(setIncome, setExpense)
        barData.barWidth = 0.35f
        combinedData.setData(barData)

        val showForecast = lineEntries.size > 1
        binding.combinedChart.axisRight.isEnabled = showForecast

        if (showForecast) {
            val setLine = LineDataSet(lineEntries, "Прогноз балансу").apply {
                color = Color.parseColor("#5C6BC0")
                lineWidth = 2.2f
                setDrawCircles(true)
                setCircleColor(Color.parseColor("#5C6BC0"))
                circleRadius = 3f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                enableDashedLine(15f, 10f, 0f)
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            combinedData.setData(LineData(setLine))
        }

        binding.combinedChart.data = combinedData
        binding.combinedChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        val mv = CustomMarkerView(requireContext(), R.layout.view_marker, labels)
        mv.chartView = binding.combinedChart
        binding.combinedChart.marker = mv

        val groupSpace = 0.2f
        val barSpace = 0.05f
        binding.combinedChart.xAxis.axisMinimum = 0f
        binding.combinedChart.xAxis.axisMaximum = labels.size.toFloat()
        barData.groupBars(0f, groupSpace, barSpace)

        if (labels.size > 8) {
            binding.combinedChart.setVisibleXRangeMaximum(8f)
            binding.combinedChart.moveViewToX(labels.size.toFloat())
        } else {
            binding.combinedChart.fitScreen()
        }

        val l1 = LegendEntry("Доходи", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#43A047"))
        val l2 = LegendEntry("Витрати", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#E53935"))
        val legendEntries = mutableListOf(l1, l2)
        if (showForecast) {
            val l3 = LegendEntry("Прогноз", Legend.LegendForm.LINE, 10f, 2f, null, Color.parseColor("#5C6BC0"))
            legendEntries.add(l3)
        }
        binding.combinedChart.legend.setCustom(legendEntries)
        binding.combinedChart.notifyDataSetChanged()
        binding.combinedChart.invalidate()
    }

    private fun updatePieChart(transactions: List<Transaction>) {
        val expenseTransactions = transactions.filter { it.type == TransactionTypes.EXPENSE }
        binding.llLegendContainer.removeAllViews()

        if (expenseTransactions.isEmpty()) {
            binding.pieChartExpenses.clear()
            binding.pieChartExpenses.centerText = "Немає даних"
            binding.pieChartExpenses.invalidate()
            return
        }
        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
            val categoryNameMap = categories.associate { it.category_id to it.name }
            categoryData = categories.associate { it.name to it.category_id }
            val expensesByCategory = expenseTransactions
                .groupBy { it.category_id }
                .mapValues { entry -> entry.value.sumOf { it.amount.toDouble() } }
                .toList().sortedByDescending { it.second }
            val totalRealAmount = expensesByCategory.sumOf { it.second }
            val entries = ArrayList<PieEntry>()
            val minChartValue = totalRealAmount * 0.02

            for ((catId, realAmount) in expensesByCategory) {
                if (realAmount > 0) {
                    val name = categoryNameMap[catId] ?: "Без назви"
                    val displayAmount = if (realAmount < minChartValue) minChartValue else realAmount
                    val entry = PieEntry(displayAmount.toFloat(), name)
                    entry.data = realAmount
                    entries.add(entry)
                }
            }
            val colorsList = ColorTemplate.MATERIAL_COLORS.toList() +
                    ColorTemplate.JOYFUL_COLORS.toList() +
                    ColorTemplate.LIBERTY_COLORS.toList() +
                    ColorTemplate.PASTEL_COLORS.toList()
            val dataSet = PieDataSet(entries, "").apply {
                colors = colorsList
                sliceSpace = 2f
                selectionShift = 5f
                setDrawValues(false)
                setDrawIcons(false)
            }
            binding.pieChartExpenses.data = PieData(dataSet)
            binding.pieChartExpenses.centerText = "Всього\n${String.format("%.0f", totalRealAmount)} ₴"
            binding.pieChartExpenses.invalidate()

            val inflater = LayoutInflater.from(requireContext())
            entries.forEachIndexed { index, pieEntry ->
                val color = colorsList[index % colorsList.size]
                val realAmount = pieEntry.data as Double
                val percent = (realAmount / totalRealAmount) * 100
                val itemView = inflater.inflate(R.layout.item_chart_legend, binding.llLegendContainer, false)
                val vColor = itemView.findViewById<View>(R.id.vColorIndicator)
                val tvName = itemView.findViewById<TextView>(R.id.tvCategoryName)
                val tvAmount = itemView.findViewById<TextView>(R.id.tvCategoryAmount)
                val tvPercent = itemView.findViewById<TextView>(R.id.tvCategoryPercent)
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                drawable.setColor(color)
                vColor.background = drawable
                tvName.text = pieEntry.label
                tvAmount.text = String.format("%.0f ₴", realAmount)
                tvPercent.text = if (percent < 0.1) "<0.1%" else String.format("%.1f%%", percent)
                itemView.setOnClickListener { handleCategoryClick(pieEntry.label, realAmount.toFloat()) }
                binding.llLegendContainer.addView(itemView)
            }
        }
    }

    private fun updateSmartInsights(transactions: List<Transaction>) {
        val expenseTransactions = transactions.filter { it.type == TransactionTypes.EXPENSE }
        if (expenseTransactions.isEmpty()) {
            binding.tvAvgDailySpend.text = "0 ₴ / день"
            binding.tvTopCategory.text = "—"
            return
        }

        val now = Date()
        val effectiveEndDate = if (endDate.after(now)) now else endDate
        val diffTime = effectiveEndDate.time - startDate.time
        val daysPassed = (TimeUnit.MILLISECONDS.toDays(diffTime) + 1).toInt().coerceAtLeast(1)
        val totalExpense = expenseTransactions.sumOf { it.amount.toDouble() }

        if (daysPassed < 7) {
            val avg = totalExpense / daysPassed
            binding.tvAvgDailySpend.text = String.format("%.0f ₴ / день", avg)
        } else if (daysPassed in 7..60) {
            val weeks = daysPassed / 7.0
            val avg = totalExpense / weeks
            binding.tvAvgDailySpend.text = String.format("%.0f ₴ / тиждень", avg)
        } else {
            val months = daysPassed / 30.0
            val avg = totalExpense / months
            binding.tvAvgDailySpend.text = String.format("%.0f ₴ / міс", avg)
        }

        val topCategoryEntry = expenseTransactions
            .groupBy { it.category_id }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount.toDouble() } }
        if (topCategoryEntry != null) {
            val topCatId = topCategoryEntry.key
            val categoryName = allCategoriesList.find { it.category_id == topCatId }?.name ?: "Інше"
            val catSum = topCategoryEntry.value.sumOf { it.amount.toDouble() }
            val percent = (catSum / totalExpense) * 100
            binding.tvTopCategory.text = "$categoryName (${String.format("%.0f", percent)}%)"
        } else {
            binding.tvTopCategory.text = "—"
        }
    }

    private fun updateTrendUI(trendData: TrendData?) {}

    private fun handleCategoryClick(categoryName: String, value: Float) {
        if (categoryName == OTHER_CATEGORY_LABEL) return
        val categoryId = categoryData[categoryName]
        if (categoryId != null) {
            TransactionListBottomSheet.newInstance(
                categoryId, categoryName, value, startDate, endDate
            ).show(parentFragmentManager, "TransactionListBottomSheet")
        }
    }

    private fun clearTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun setPeriodWeek() {
        currentPeriodName = "тижня"
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        startDate = calendar.time
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        endDate = calendar.time
        loadData()
    }

    private fun setPeriodMonth() {
        currentPeriodName = "місяця"
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        startDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        endDate = calendar.time
        loadData()
    }

    private fun setPeriodYear() {
        currentPeriodName = "року"
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        startDate = calendar.time
        calendar.set(Calendar.MONTH, 11)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        endDate = calendar.time
        loadData()
    }

    private fun showDateRangePicker() {
        currentPeriodName = "періоду"
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Оберіть період")
            .setSelection(Pair(startDate.time, endDate.time))
            .build()
        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            startDate = Date(selection.first)
            endDate = Date(selection.second)
            binding.toggleGroupPeriod.clearChecked()
            loadData()
        }
        dateRangePicker.show(parentFragmentManager, "date_range_picker")
    }

    private fun setupChartsStyles() {
        binding.pieChartExpenses.apply {
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 65f
            transparentCircleRadius = 70f
            setDrawCenterText(true)
            setCenterTextSize(16f)
            setCenterTextColor(Color.BLACK)
            setDrawEntryLabels(false)
            animateY(1000)
            setExtraOffsets(0f, 0f, 0f, 0f)
        }
        binding.combinedChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            extraBottomOffset = 20f
            setPinchZoom(false)
            setScaleEnabled(false)
            isDragEnabled = true
            drawOrder = arrayOf(CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)
            xAxis.axisLineColor = Color.parseColor("#E0E0E0")
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true
            xAxis.setCenterAxisLabels(true)
            xAxis.textColor = Color.DKGRAY
            xAxis.textSize = 12f
            xAxis.yOffset = 10f
            axisLeft.isEnabled = true
            axisLeft.setDrawAxisLine(false)
            axisLeft.setDrawLabels(true)
            axisLeft.textColor = Color.parseColor("#757575")
            axisLeft.textSize = 11f
            axisLeft.axisMinimum = 0f
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = Color.parseColor("#EEEEEE")
            axisLeft.enableGridDashedLine(10f, 10f, 0f)
            axisRight.isEnabled = true
            axisRight.setDrawGridLines(false)
            axisRight.setDrawAxisLine(false)
            axisRight.textColor = Color.parseColor("#5C6BC0")
            axisRight.textSize = 11f
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            legend.form = Legend.LegendForm.CIRCLE
            legend.textSize = 12f
            legend.textColor = Color.DKGRAY
            legend.xEntrySpace = 16f
            setNoDataText("Немає даних за цей період")
            renderer = RoundedCombinedChartRenderer(this, animator, viewPortHandler, 20f)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {
        if (e == null) return
        if (e is PieEntry) {
            val realValue = (e.data as? Double)?.toFloat() ?: e.value
            handleCategoryClick(e.label, realValue)
        }
    }

    override fun onNothingSelected() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class RoundedCombinedChartRenderer(
    chart: CombinedChart,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val cornerRadius: Float
) : CombinedChartRenderer(chart, animator, viewPortHandler) {

    override fun createRenderers() {
        mRenderers.clear()
        val chart = mChart?.get() as? CombinedChart ?: return
        val orders = chart.drawOrder

        for (order in orders) {
            when (order) {
                CombinedChart.DrawOrder.BAR -> {
                    val data = chart.data
                    if (data != null && data.barData != null) {
                        mRenderers.add(RoundedBarRenderer(chart, mAnimator, mViewPortHandler, cornerRadius))
                    }
                }
                CombinedChart.DrawOrder.LINE -> {
                    val data = chart.data
                    if (data != null && data.lineData != null) {
                        mRenderers.add(LineChartRenderer(chart, mAnimator, mViewPortHandler))
                    }
                }
                else -> {}
            }
        }
    }

    private class RoundedBarRenderer(
        private val chartProvider: BarDataProvider,
        animator: ChartAnimator,
        viewPortHandler: ViewPortHandler,
        private val radius: Float
    ) : BarChartRenderer(chartProvider, animator, viewPortHandler) {

        override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
            val trans = mChart.getTransformer(dataSet.axisDependency)
            mBarBorderPaint.color = dataSet.barBorderColor
            mBarBorderPaint.strokeWidth = if (dataSet.barBorderWidth > 0f) dataSet.barBorderWidth else 0f
            val phaseX = mAnimator.phaseX
            val phaseY = mAnimator.phaseY

            if (mBarBuffers != null) {
                val buffer = mBarBuffers[index]
                buffer.setPhases(phaseX, phaseY)
                buffer.setDataSet(index)
                buffer.setInverted(mChart.isInverted(dataSet.axisDependency))

                buffer.setBarWidth(mChart.barData.barWidth)

                buffer.feed(dataSet)
                trans.pointValuesToPixel(buffer.buffer)

                var j = 0
                while (j < buffer.size()) {
                    if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) { j += 4; continue }
                    if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

                    val barColor = dataSet.getColor(j / 4)

                    val startColor = barColor
                    val endColor = ColorUtils.setAlphaComponent(barColor, 150)
                    mRenderPaint.shader = LinearGradient(
                        buffer.buffer[j], buffer.buffer[j + 3],
                        buffer.buffer[j], buffer.buffer[j + 1],
                        startColor, endColor, Shader.TileMode.MIRROR
                    )
                    mRenderPaint.color = barColor

                    val left = buffer.buffer[j]
                    val top = buffer.buffer[j + 1]
                    val right = buffer.buffer[j + 2]
                    val bottom = buffer.buffer[j + 3]

                    val path = Path()
                    val radii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                    path.addRoundRect(RectF(left, top, right, bottom), radii, Path.Direction.CW)
                    c.drawPath(path, mRenderPaint)
                    j += 4
                }
            }
        }
    }
}