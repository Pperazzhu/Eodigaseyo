package app.eodigaseyo

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 묻기 답: 같은 곳 / 같은 방향 / 다른 방향 / 비공개
enum class Rel { SAME, DIR, DIFF, PRIV }

// 주변 차 레이더: 방향 8칸 × 거리 3칸으로만 표시 (정확한 위치는 숨김)
// 색은 res/values(낮), res/values-night(밤)의 e_radar_* 를 따름
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Dot(val sector: Int, val band: Int, val rel: Rel)

    private var dots: List<Dot> = emptyList()
    fun setDots(list: List<Dot>) { dots = list; invalidate() }

    private fun c(id: Int) = ContextCompat.getColor(context, id)
    private val accent = c(R.color.e_accent)
    // 색은 한 번만 읽어 둬요 (야간 모드가 바뀌면 화면이 새로 만들어져 다시 읽혀요)
    private val dirColor = c(R.color.e_c0_fg)       // 같은 방향 = 초록 (같은 곳의 파랑과 색으로 구분)
    private val privColor = c(R.color.e_radar_priv)
    private val carColor = c(R.color.e_radar_car)
    private val bandR = floatArrayOf(0.22f, 0.54f, 0.85f)
    private val ringR = floatArrayOf(0.38f, 0.7f, 1f)
    private val seen = HashMap<Int, Int>()
    private val rect = RectF()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = c(R.color.e_radar_ring); strokeWidth = 3f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = c(R.color.e_radar_line); strokeWidth = 2f
    }
    private val mePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.e_radar_me) }
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val solidRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = accent; strokeWidth = 5f
    }
    private val dashRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = dirColor; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.e_text2); textSize = 26f; textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f - 14f
        val r = min(width, height) / 2f - 36f

        ringR.forEach { canvas.drawCircle(cx, cy, r * it, ringPaint) }
        for (s in 0 until 8) {
            val a = Math.toRadians((s * 45 - 90 + 22.5))
            canvas.drawLine(cx, cy, cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), linePaint)
        }

        // 내 차 (가운데, 위쪽이 진행 방향)
        rect.set(cx - 15f, cy - 24f, cx + 15f, cy + 24f)
        canvas.drawRoundRect(rect, 9f, 9f, mePaint)

        seen.clear()
        dots.forEach { d ->
            val key = d.sector * 10 + d.band            // 같은 칸에 여러 대면 조금씩 옆으로
            val n = seen[key] ?: 0; seen[key] = n + 1
            val angle = Math.toRadians(d.sector * 45.0 - 90 + (n - 1) * 12)
            val rr = r * bandR[d.band]
            val x = cx + (rr * cos(angle)).toFloat()
            val y = cy + (rr * sin(angle)).toFloat()

            carPaint.color = when (d.rel) {
                Rel.SAME -> accent
                Rel.DIR -> dirColor
                Rel.PRIV -> privColor
                Rel.DIFF -> carColor
            }
            rect.set(x - 11f, y - 17f, x + 11f, y + 17f)
            canvas.drawRoundRect(rect, 7f, 7f, carPaint)
            when (d.rel) {
                Rel.SAME -> canvas.drawCircle(x, y, 30f, solidRing)
                Rel.DIR -> canvas.drawCircle(x, y, 30f, dashRing)
                else -> {}
            }
        }
        canvas.drawText("방향·거리 구간만 표시", cx, height - 6f, textPaint)
    }
}