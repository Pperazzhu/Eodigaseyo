package app.eodigaseyo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// 전체 화면용 '목적지 주변 지도' (여의도 일대를 단순하게 그림, 실제 축척 아님)
// · 길 안내는 현대 내장 내비가 맡고, 이 지도는 내비에 없는 정보만 얹어요
//   (주차장별 대기, 같은 곳 가는 차 합계로 만든 혼잡 구역, "같은 곳 가요"라고 답한 차)
// · 다른 차는 묻기에 "같은 곳 가요"라고 답한 차만 보여요 (웹 시연 화면과 같은 규칙)
class DestMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class MapLot(val name: String, val lat: Double, val lon: Double, val label: String, val level: Int)
    data class LatLon(val lat: Double, val lon: Double)

    private var destName: String? = null
    private var dest: LatLon? = null
    private var crowdLevel = 0              // 0 여유 ~ 3 매우 혼잡 (5대 미만이면 0)
    private var lots: List<MapLot> = emptyList()
    private var exits: List<MapLot> = emptyList()      // ⑤ 나갈 때: 출구별 대기 (주차 중에만)
    private var me: LatLon? = null
    private var sameCars: List<LatLon> = emptyList()
    private var sameCount = 0

    // 값이 그대로면 다시 그리지 않아요 (5초마다 오는 위치·목적지 갱신에서 불필요한 그리기를 줄임)
    fun setDest(name: String?, lat: Double, lon: Double, level: Int) {
        val d = if (name == null) null else LatLon(lat, lon)
        if (name == destName && d == dest && level == crowdLevel) return
        destName = name; dest = d; crowdLevel = level
        invalidate()
    }
    fun setLots(list: List<MapLot>) { if (list != lots) { lots = list; invalidate() } }
    fun setExits(list: List<MapLot>) { if (list != exits) { exits = list; invalidate() } }
    fun setMe(lat: Double, lon: Double) { val m = LatLon(lat, lon); if (m != me) { me = m; invalidate() } }
    // list: 그릴 위치(개인정보 때문에 5대 이상일 때만 넘어와요), count: '같은 곳 가요'라고 답한 차 수
    fun setSameCars(list: List<LatLon>, count: Int = list.size) {
        if (list != sameCars || count != sameCount) { sameCars = list; sameCount = count; invalidate() }
    }

    // ---------- 지도 범위: 여의도 일대 ----------
    // 기본 범위. 화면 비율에 맞춰 위아래(또는 좌우)를 넓혀서 카드를 빈틈없이 채워요
    private val baseMinLat = 37.5110; private val baseMaxLat = 37.5420
    private val baseMinLon = 126.8940; private val baseMaxLon = 126.9560
    private var minLat = baseMinLat; private var maxLat = baseMaxLat
    private var minLon = baseMinLon; private var maxLon = baseMaxLon
    private val kx = cos(Math.toRadians(37.526))     // 경도 1도를 위도 기준 거리로 맞춤

    private val area = RectF()
    private fun fit() {
        val pad = 16f
        val availW = width - pad * 2; val availH = height - pad * 2
        area.set(pad, pad, pad + availW, pad + availH)
        val wDeg = (baseMaxLon - baseMinLon) * kx
        val hDeg = baseMaxLat - baseMinLat
        val cLat = (baseMinLat + baseMaxLat) / 2; val cLon = (baseMinLon + baseMaxLon) / 2
        if (availH / availW > hDeg / wDeg) {        // 세로로 긴 카드: 위아래 범위를 넓힘
            val half = wDeg * availH / availW / 2
            minLat = cLat - half; maxLat = cLat + half; minLon = baseMinLon; maxLon = baseMaxLon
        } else {                                     // 가로로 긴 카드: 좌우 범위를 넓힘
            val half = hDeg * availW / availH / kx / 2
            minLon = cLon - half; maxLon = cLon + half; minLat = baseMinLat; maxLat = baseMaxLat
        }
    }
    private fun x(lon: Double) = (area.left + (lon - minLon) / (maxLon - minLon) * area.width()).toFloat()
    private fun y(lat: Double) = (area.bottom - (lat - minLat) / (maxLat - minLat) * area.height()).toFloat()
    private fun inside(p: LatLon) = p.lat in minLat..maxLat && p.lon in minLon..maxLon

    // ---------- 단순화한 여의도 ----------
    private val island = listOf(
        37.5350 to 126.9000, 37.5345 to 126.9150, 37.5305 to 126.9300, 37.5250 to 126.9420,
        37.5185 to 126.9470, 37.5160 to 126.9400, 37.5195 to 126.9250, 37.5235 to 126.9100,
        37.5290 to 126.8990
    )
    // 한강: 위쪽(마포) 강변 ~ 여의도 북쪽 강변, 섬 동쪽 끝을 돌아 이어져요
    private val riverNorth = listOf(
        37.5405 to 126.8940, 37.5405 to 126.9560, 37.5215 to 126.9560, 37.5185 to 126.9470,
        37.5250 to 126.9420, 37.5305 to 126.9300, 37.5345 to 126.9150, 37.5350 to 126.9000,
        37.5362 to 126.8940
    )
    // 샛강: 섬 남쪽을 따라 흐르는 좁은 물길
    private val saetgang = listOf(
        37.5285 to 126.8960, 37.5235 to 126.9100, 37.5195 to 126.9250, 37.5160 to 126.9400, 37.5175 to 126.9500
    )
    private val roads = listOf(
        listOf(37.5315 to 126.9030, 37.5260 to 126.9200, 37.5195 to 126.9420),   // 섬 가운데 큰길
        listOf(37.5285 to 126.9050, 37.5340 to 126.9180),                         // 국회 앞
        listOf(37.5250 to 126.9150, 37.5300 to 126.9300),                         // 여의대로
        listOf(37.5160 to 126.9160, 37.5185 to 126.9470),                         // 강변(샛강 쪽)
    )
    private val bridges = listOf(
        Triple(37.5345 to 126.9230, 37.5420 to 126.9260, "서강대교"),
        Triple(37.5305 to 126.9330, 37.5400 to 126.9395, "마포대교"),
        Triple(37.5245 to 126.9430, 37.5320 to 126.9500, "원효대교"),
    )

    private fun c(id: Int) = ContextCompat.getColor(context, id)
    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    // 자주 쓰는 색은 한 번만 읽어요 (야간 모드가 바뀌면 화면이 새로 만들어져 다시 읽혀요)
    private val cAccent = c(R.color.e_accent)
    private val cPage = c(R.color.e_page)
    private val cCard = c(R.color.e_card)
    private val cCardStroke = c(R.color.e_card_stroke)
    private val cLine = c(R.color.e_radar_line)
    private val cText = c(R.color.e_text)
    private val cText3 = c(R.color.e_text3)
    private val cMe = c(R.color.e_radar_me)
    private val cOnAccent = c(R.color.e_on_accent)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun path(pts: List<Pair<Double, Double>>, close: Boolean) = Path().apply {
        pts.forEachIndexed { i, (la, lo) -> if (i == 0) moveTo(x(lo), y(la)) else lineTo(x(lo), y(la)) }
        if (close) close()
    }

    // 바탕 지도(한강·섬·도로·다리) 경로는 크기가 바뀔 때만 다시 만들어요
    private var riverPath = Path(); private var saetgangPath = Path(); private var islandPath = Path()
    private var roadPaths: List<Path> = emptyList(); private var bridgePaths: List<Path> = emptyList()
    private val tmpRect = RectF(); private val arrow = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        fit()
        riverPath = path(riverNorth, true); saetgangPath = path(saetgang, false); islandPath = path(island, true)
        roadPaths = roads.map { path(it, false) }
        bridgePaths = bridges.map { (a, b, _) -> path(listOf(a, b), false) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val scale = area.width() / 900f               // 화면 크기에 맞춰 글씨·선 굵기 조절
        val accent = cAccent

        // 바탕, 한강, 섬
        canvas.save(); canvas.clipRect(area)
        fill.color = cPage; canvas.drawRect(area, fill)
        fill.color = alpha(accent, 0x40); canvas.drawPath(riverPath, fill)
        stroke.color = alpha(accent, 0x40); stroke.strokeWidth = 14f * scale
        canvas.drawPath(saetgangPath, stroke)
        fill.color = cCard; canvas.drawPath(islandPath, fill)
        stroke.color = cCardStroke; stroke.strokeWidth = 3f * scale
        canvas.drawPath(islandPath, stroke)

        // 도로·다리
        stroke.color = cLine; stroke.strokeWidth = 10f * scale
        roadPaths.forEach { canvas.drawPath(it, stroke) }
        text.textSize = 22f * scale; text.color = cText3
        bridges.forEachIndexed { i, (_, b, name) ->
            canvas.drawPath(bridgePaths[i], stroke)
            canvas.drawText(name, x(b.second), y(b.first) - 8f * scale, text)
        }
        text.textSize = 28f * scale; text.color = alpha(accent, 0xB0)
        canvas.drawText("한  강", x(126.9050), y(37.5395), text)
        text.color = cText3; text.textSize = 24f * scale
        canvas.drawText("여 의 도", x(126.9180), y(37.5250), text)

        // 혼잡 구역: 같은 곳 가는 차 합계로만 칠함 (5대 미만이면 칠하지 않음)
        dest?.let { d ->
            if (crowdLevel >= 1 && inside(d)) {
                val col = when (crowdLevel) { 1 -> R.color.e_c1_fg; 2 -> R.color.e_c2_fg; else -> R.color.e_c3_fg }
                fill.color = alpha(c(col), 0x38)
                canvas.drawCircle(x(d.lon), y(d.lat), 70f * scale, fill)
            }
        }

        // 주차장: 대기 시간 한 줄 / 주차 중이면 출구별 대기
        lots.forEach { pill(canvas, it, "P", scale) }
        exits.forEach { pill(canvas, it, "↗", scale) }

        // "같은 곳 가요"라고 답한 차 (묻기 결과)
        stroke.color = accent; stroke.strokeWidth = 6f * scale
        fill.color = alpha(accent, 0x55)
        sameCars.forEach {
            if (!inside(it)) return@forEach
            canvas.drawCircle(x(it.lon), y(it.lat), 22f * scale, fill)
            canvas.drawCircle(x(it.lon), y(it.lat), 22f * scale, stroke)
        }
        if (sameCount > 0) {
            text.textSize = 26f * scale; text.color = accent; text.isFakeBoldText = true
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText("◯ \"같은 곳 가요\"라고 답한 차 ${sameCount}대" + if (sameCars.isEmpty()) " (위치는 5대 이상일 때만)" else "", area.right - 16f * scale, area.top + 34f * scale, text)
            text.textAlign = Paint.Align.CENTER; text.isFakeBoldText = false
        }

        // 목적지 핀
        dest?.let { d ->
            if (!inside(d)) return@let
            val px = x(d.lon); val py = y(d.lat)
            fill.color = accent; canvas.drawCircle(px, py, 13f * scale, fill)
            fill.color = cOnAccent; canvas.drawCircle(px, py, 5f * scale, fill)
            destName?.let {
                text.textSize = 24f * scale; text.color = cText; text.isFakeBoldText = true
                canvas.drawText(it, px, py + 40f * scale, text); text.isFakeBoldText = false
            }
        }

        // 내 차: 지도 안이면 점, 밖이면 가장자리 화살표와 거리
        me?.let { m ->
            if (inside(m)) {
                fill.color = cMe; canvas.drawCircle(x(m.lon), y(m.lat), 14f * scale, fill)
                text.textSize = 20f * scale; text.color = cText
                canvas.drawText("나", x(m.lon), y(m.lat) - 22f * scale, text)
            } else {
                val cx = area.centerX(); val cy = area.centerY()
                val ang = atan2((y(m.lat) - cy).toDouble(), (x(m.lon) - cx).toDouble())
                val rx = area.width() / 2 - 40f * scale; val ry = area.height() / 2 - 40f * scale
                val ex = cx + (rx * cos(ang)).toFloat(); val ey = cy + (ry * sin(ang)).toFloat()
                fill.color = cMe
                val a = arrow.apply {
                    reset()
                    moveTo(ex + (22f * scale * cos(ang)).toFloat(), ey + (22f * scale * sin(ang)).toFloat())
                    lineTo(ex + (14f * scale * cos(ang + 2.4)).toFloat(), ey + (14f * scale * sin(ang + 2.4)).toFloat())
                    lineTo(ex + (14f * scale * cos(ang - 2.4)).toFloat(), ey + (14f * scale * sin(ang - 2.4)).toFloat())
                    close()
                }
                canvas.drawPath(a, fill)
                val km = distanceKm(m, dest ?: LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2))
                text.textSize = 20f * scale; text.color = cText
                canvas.drawText("내 차 · 직선 약 ${"%.0f".format(km)}km", ex - (40f * scale * cos(ang)).toFloat(), ey - (40f * scale * sin(ang)).toFloat(), text)
            }
        }

        // 목적지가 시연 지도(여의도) 밖이면 안내 한 줄
        dest?.let { dd ->
            if (!inside(dd)) {
                text.textSize = 24f * scale; text.isFakeBoldText = true
                val msg = "목적지가 시연 지도 범위(여의도) 밖이에요 · 길 안내는 현대 내비"
                val w = text.measureText(msg) + 40f * scale; val h = 52f * scale
                val r = tmpRect.apply { set(area.centerX() - w / 2, area.top + 20f * scale, area.centerX() + w / 2, area.top + 20f * scale + h) }
                fill.color = cCard; canvas.drawRoundRect(r, h / 2, h / 2, fill)
                text.color = cText; canvas.drawText(msg, r.centerX(), r.bottom - 17f * scale, text)
                text.isFakeBoldText = false
            }
        }
        canvas.restore()
        stroke.color = cCardStroke; stroke.strokeWidth = 2f
        canvas.drawRoundRect(area, 16f, 16f, stroke)

    }

    private fun pill(canvas: Canvas, lot: MapLot, mark: String, scale: Float) {
        val p = LatLon(lot.lat, lot.lon); if (!inside(p)) return
        val (bg, fg) = when (lot.level) {
            0 -> R.color.e_c0_bg to R.color.e_c0_fg
            1 -> R.color.e_c1_bg to R.color.e_c1_fg
            2 -> R.color.e_c2_bg to R.color.e_c2_fg
            else -> R.color.e_c3_bg to R.color.e_c3_fg
        }
        val px = x(lot.lon); val py = y(lot.lat)
        text.textSize = 26f * scale; text.isFakeBoldText = true
        val label = "$mark ${lot.name} · ${lot.label}"
        val w = text.measureText(label) + 28f * scale; val h = 44f * scale
        val r = tmpRect.apply { set(px - w / 2, py - h - 14f * scale, px + w / 2, py - 14f * scale) }
        fill.color = c(bg); canvas.drawRoundRect(r, h / 2, h / 2, fill)
        text.color = c(fg); canvas.drawText(label, px, r.bottom - 13f * scale, text)
        text.isFakeBoldText = false
        fill.color = c(fg); canvas.drawCircle(px, py, 7f * scale, fill)
    }

    private fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = (a.lat - b.lat) * 111.0
        val dLon = (a.lon - b.lon) * 111.0 * kx
        return kotlin.math.hypot(dLat, dLon)
    }
}
