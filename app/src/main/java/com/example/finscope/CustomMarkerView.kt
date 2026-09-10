package com.example.finscope

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarkerView(context: Context, layoutResource: Int, private val labels: List<String>) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        val index = e.x.toInt()
        val value = e.y

        // Достаем дату из списка labels, если индекс валидный
        val dateLabel = if (index >= 0 && index < labels.size) labels[index] else "?"

        // Форматируем текст
        tvContent.text = "$dateLabel\n${String.format("%.0f ₴", value)}"

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Центрируем маркер над точкой
        return MPPointF(-(width / 2).toFloat(), -height.toFloat())
    }
}