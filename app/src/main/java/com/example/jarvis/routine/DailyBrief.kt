package com.example.jarvis.routine

import android.content.Context
import com.example.jarvis.ledger.Ledger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DailyBrief {
    fun build(ctx: Context): String {
        val loc = Locale("pt", "MZ")
        val day = SimpleDateFormat("EEEE, d 'de' MMMM", loc).format(Calendar.getInstance().time)
        val start = Ledger.startOfDay()
        val y = Ledger.total(ctx, Ledger.startOfDay(1), start)
        val money = if (y > 0) " Ontem gastaste ${"%.0f".format(y)} MT." else ""
        val week = if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
            " " + Ledger.report(ctx, "semana") else ""
        return "Bom dia! Hoje é $day. Agenda não consultada (permissão removida).$money$week"
    }

}
