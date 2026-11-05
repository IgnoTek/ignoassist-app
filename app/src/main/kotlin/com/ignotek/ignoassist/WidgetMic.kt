package com.ignotek.ignoassist

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/**
 * Widgets de pantalla de inicio para encender/apagar el micrófono con un toque.
 * Apagar se hace directo (broadcast -> ListenService STOP). Encender abre la app un instante,
 * porque Android 14 no permite arrancar un servicio de micrófono desde segundo plano.
 */
object WidgetMic {
    const val ACCION_TOGGLE = "com.ignotek.ignoassist.WIDGET_TOGGLE"
    const val ACCION_PAUSA = "com.ignotek.ignoassist.WIDGET_PAUSA"
    const val TIPO_BOTON = 1
    const val TIPO_ESTADO = 2
    const val TIPO_CONTROL = 3
    private val TEAL = Color.parseColor("#0D6E66")

    fun escuchando(p: Prefs): Boolean = p.grabando && p.servicioActivo

    private fun piEncender(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("accion", "reanudar")
        return PendingIntent.getActivity(ctx, 202, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun piApagar(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetReceiver::class.java).setAction(ACCION_TOGGLE)
        return PendingIntent.getBroadcast(ctx, 201, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun piToggle(ctx: Context, on: Boolean): PendingIntent = if (on) piApagar(ctx) else piEncender(ctx)
    private fun piPausa(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetReceiver::class.java).setAction(ACCION_PAUSA)
        return PendingIntent.getBroadcast(ctx, 203, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun titulo(p: Prefs, on: Boolean): String = when {
        on -> "Escuchando"
        p.enPausa -> "En pausa"
        !p.vinculado -> "Sin vincular"
        else -> "Apagado"
    }
    private fun sub(p: Prefs, on: Boolean): String = when {
        !p.vinculado -> "Abre la app para vincular"
        on -> "${p.subidasHoy} hoy · toca para apagar"
        p.enPausa -> "Toca para reanudar"
        else -> "Toca para encender"
    }

    fun construir(ctx: Context, tipo: Int): RemoteViews {
        val p = Prefs(ctx)
        val on = escuchando(p)
        val circuloBg = if (on) R.drawable.bg_wid_circulo_on else R.drawable.bg_wid_circulo_off
        val iconoColor = if (on) Color.WHITE else TEAL
        return when (tipo) {
            TIPO_BOTON -> {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_boton)
                rv.setInt(R.id.wb_root, "setBackgroundResource", circuloBg)
                rv.setInt(R.id.wb_icono, "setColorFilter", iconoColor)
                rv.setOnClickPendingIntent(R.id.wb_root, piToggle(ctx, on))
                rv
            }
            TIPO_ESTADO -> {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_estado)
                rv.setInt(R.id.we_circulo, "setBackgroundResource", circuloBg)
                rv.setInt(R.id.we_icono, "setColorFilter", iconoColor)
                rv.setTextViewText(R.id.we_titulo, titulo(p, on))
                rv.setTextViewText(R.id.we_sub, sub(p, on))
                rv.setOnClickPendingIntent(R.id.we_root, piToggle(ctx, on))
                rv
            }
            else -> {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_control)
                rv.setInt(R.id.wc_circulo, "setBackgroundResource", circuloBg)
                rv.setInt(R.id.wc_icono, "setColorFilter", iconoColor)
                rv.setTextViewText(R.id.wc_titulo, titulo(p, on))
                rv.setTextViewText(R.id.wc_sub, sub(p, on))
                rv.setTextViewText(R.id.wc_toggle, if (on) "Apagar" else "Encender")
                rv.setInt(R.id.wc_toggle, "setBackgroundResource", if (on) R.drawable.bg_wid_boton_ink else R.drawable.bg_wid_boton_teal)
                rv.setOnClickPendingIntent(R.id.wc_toggle, piToggle(ctx, on))
                rv.setOnClickPendingIntent(R.id.wc_pausa, piPausa(ctx))
                rv
            }
        }
    }

    fun actualizarTodos(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        actualizarTipo(ctx, mgr, WidgetBoton::class.java, TIPO_BOTON)
        actualizarTipo(ctx, mgr, WidgetEstado::class.java, TIPO_ESTADO)
        actualizarTipo(ctx, mgr, WidgetControl::class.java, TIPO_CONTROL)
    }
    private fun actualizarTipo(ctx: Context, mgr: AppWidgetManager, clazz: Class<*>, tipo: Int) {
        try {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, clazz))
            if (ids == null || ids.isEmpty()) return
            mgr.updateAppWidget(ids, construir(ctx, tipo))
        } catch (e: Exception) { }
    }
}

class WidgetBoton : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { mgr.updateAppWidget(ids, WidgetMic.construir(ctx, WidgetMic.TIPO_BOTON)) }
}
class WidgetEstado : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { mgr.updateAppWidget(ids, WidgetMic.construir(ctx, WidgetMic.TIPO_ESTADO)) }
}
class WidgetControl : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { mgr.updateAppWidget(ids, WidgetMic.construir(ctx, WidgetMic.TIPO_CONTROL)) }
}

/** Recibe el toque de los widgets: apagar (directo) o pausar; encender abre la app por la regla de Android 14. */
class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        when (intent.action) {
            WidgetMic.ACCION_TOGGLE -> {
                if (WidgetMic.escuchando(prefs)) ListenService.lanzar(ctx, ListenService.ACTION_STOP)
                else {
                    val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("accion", "reanudar")
                    try { ctx.startActivity(i) } catch (e: Exception) { }
                }
                Handler(Looper.getMainLooper()).postDelayed({ WidgetMic.actualizarTodos(ctx) }, 1200)
            }
            WidgetMic.ACCION_PAUSA -> {
                ListenService.lanzar(ctx, ListenService.ACTION_PAUSE) { it.putExtra("minutos", prefs.pausaMinutosDefecto) }
                Handler(Looper.getMainLooper()).postDelayed({ WidgetMic.actualizarTodos(ctx) }, 1200)
            }
        }
    }
}
