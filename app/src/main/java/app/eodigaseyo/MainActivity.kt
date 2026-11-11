package app.eodigaseyo

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import com.google.android.material.button.MaterialButton
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteOverviewShowInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object { @Volatile var inForeground = false }   // 출발 알림을 앱 안에서 띄울지, 시스템 알림으로 띄울지

    // ==================== SDK ====================
    private lateinit var vehicle: Vehicle
    private var navi: NaviHelper? = null
    @Volatile private var naviConnected = false
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propManager: CarPropertyManager? = null

    // ==================== 화면 ====================
    private lateinit var tvStatus: TextView
    private lateinit var tvDest: TextView
    private lateinit var tvSub: TextView
    private lateinit var tvCrowd: TextView
    private lateinit var tvLots: TextView
    private lateinit var tvDrive: TextView
    private lateinit var tvAsk: TextView
    private lateinit var tvNote: TextView
    private lateinit var btnLot2: Button
    private lateinit var btnQuickAsk: Button
    private lateinit var btnQuickHello: Button
    private lateinit var tvPark: TextView
    private lateinit var btnArriveSim: Button
    private lateinit var btnAsk: Button
    private lateinit var btnSim: Button
    private lateinit var btnSettings: Button
    private lateinit var radar: RadarView
    private lateinit var stopArea: View
    private lateinit var cards: LinearLayout
    private lateinit var destCard: View
    private lateinit var mapRow: View
    private lateinit var crowdGroup: View
    private lateinit var radarGroup: View
    private lateinit var destMap: DestMapView
    private lateinit var askGroup: View
    private lateinit var cardAsk: View
    private lateinit var exitGroup: View
    private lateinit var tvExit: TextView
    private lateinit var btnExit: Button
    private lateinit var btnHello: Button
    private lateinit var cardDepart: View
    private lateinit var cardExit: View
    private lateinit var departBars: LinearLayout
    private lateinit var tvDepart: TextView
    private lateinit var btnDepartAlarm: Button
    private var homes: List<Pair<View, Pair<ViewGroup, Int>>> = emptyList()
    private var leftCol: LinearLayout? = null
    private var rightCol: LinearLayout? = null

    // ==================== 내 상태 ====================
    private var myDestName: String? = null
    private var myDestPoiId: String = ""
    private var myDestPoiProvider: String = ""
    private var myDestLat = 0.0
    private var myDestLon = 0.0
    @Volatile private var myLat = 37.5172      // 내비에서 위치를 못 받으면 쓰는 시연 기본값
    @Volatile private var myLon = 126.9076
    private var locationReady = false          // 실제 위치를 한 번이라도 받았는지

    private var simDriving: Boolean? = null    // null = 실제 차량 신호
    private var lastRestrictions: CarUxRestrictions? = null
    @Volatile private var isDriving = false

    // ==================== 기어·브레이크·도착·주차 ====================
    @Volatile private var gear: Int = -1             // VehicleGear 값, -1 = 모름
    @Volatile private var brakePressed = false       // 브레이크 페달 밟음
    private var stoppedSince = 0L                    // 브레이크 밟고 멈춘 시각
    private var remainM = Int.MAX_VALUE              // 목적지까지 남은 거리
    private var arrived = false                      // 목적지 도착
    private var parked = false                       // 도착 후 P로 주차함
    private var parkedText = ""                      // 주차 카드 내용
    private var leftDestName: String? = null         // 방금 출차한 목적지 (같은 곳에 바로 다시 '도착' 처리 안 함)
    private var parkedPlace: String? = null          // 주차한 곳 (주차 중 내비 목적지를 집 등으로 바꿔도 기억)
    private var parkedLat = 0.0
    private var parkedLon = 0.0

    // ==================== ④ 출발 시간 추천 · ⑤ 나갈 때 · 👋 인사 ====================
    // 시연 가정: "지난 4주 같은 요일·시간 통계"로 본 지금 대비 혼잡 배율
    private val departSlots = listOf("지금" to 0, "10분 뒤" to 10, "30분 뒤" to 30, "1시간 뒤" to 60)
    // 시간대별 붐비는 정도(0~1): 주말은 낮에, 평일은 저녁에 붐벼요 (시연 가정값)
    private val busyWeekend = floatArrayOf(.1f,.1f,.1f,.1f,.1f,.15f,.2f,.3f,.45f,.6f,.8f,.95f,1f,1f,.95f,.9f,.85f,.8f,.7f,.6f,.45f,.3f,.2f,.15f)
    private val busyWeekday = floatArrayOf(.1f,.1f,.1f,.1f,.1f,.1f,.15f,.2f,.25f,.3f,.35f,.45f,.55f,.5f,.45f,.45f,.5f,.6f,.8f,1f,.9f,.6f,.35f,.2f)
    private fun busyAt(ms: Long): Float {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val t = if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) busyWeekend else busyWeekday
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY); val f = cal.get(java.util.Calendar.MINUTE) / 60f
        return t[h] * (1 - f) + t[(h + 1) % 24] * f
    }
    // 지금 대비 각 출발 시간의 혼잡 배율 (지금 시각·요일에 따라 달라져요)
    private fun departRates(): List<Double> {
        val now = System.currentTimeMillis(); val base = busyAt(now)
        return departSlots.map { (busyAt(now + it.second * 60_000L) / base).toDouble() }
    }
    private var departRate = listOf(1.0, 1.0, 1.0, 1.0)
    private var departBest = 0
    private var departAlarm: Runnable? = null
    private data class Exit(val name: String, val lat: Double, val lon: Double, val rate: Double)
    private val hanExits = listOf(       // 시연 가정: 한강공원 주차장 출구 두 곳
        Exit("여의동로 출구", 37.5228, 126.9285, 1.0),
        Exit("63빌딩 방향 출구", 37.5205, 126.9425, 0.35),
    )
    private var bestExit: Exit? = null
    private var lastHelloAt = 0L

    // ==================== 로그 (출시 빌드에서는 위치·목적지 로그를 남기지 않음) ====================
    private val debugBuild by lazy { (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    private fun dlog(msg: String) { if (debugBuild) Log.d("Pleos", msg) }

    // ==================== 내 차 설정 (기기에 저장) ====================
    private val prefs by lazy { getSharedPreferences("eodiga", Context.MODE_PRIVATE) }
    private val myName get() = prefs.getString("name", "") ?: ""
    private val visibleOn get() = prefs.getBoolean("visible", true)
    private val shareDest get() = prefs.getBoolean("share", false)   // 기본 꺼짐
    private val signalAsk get() = prefs.getBoolean("signalAsk", true)  // 신호 대기 중 묻기 (D + 브레이크로 멈춤), 기본 켜짐
    private val defaultName = "노란색 SUV"
    private val badWords = listOf("시발", "씨발", "병신", "개새", "좆", "fuck")

    // ==================== 묻기 제한 ====================
    private var lastAskAt = 0L
    private val askLog = ArrayDeque<Long>()

    // ==================== 자동 답장 ====================
    private val pendingAuto = mutableListOf<String>()   // 주행 중에 답한 차 이름들
    private var lastAutoNoteAt = 0L                      // 정차 중 자동 답장 알림을 마지막으로 띄운 때

    // ==================== 판단 기준 ====================
    private val sameDistanceM = 200f    // 목적지끼리 200m 안이면 같은 곳 (500m면 2주차장과 더현대서울이 같은 곳이 돼요)
    private val sameDirDeg = 30f        // 방향 차이 30° 이내면 같은 방향
    private val kMin = 5                // 같은 곳 가는 차가 5대 미만이면 숫자 숨김
    private val askRadiusM = 500        // 묻는 범위
    private val askMax = 20             // 한 번에 묻는 차 수

    // ==================== 시연용 장소·주차장 (가정 값) ====================
    private data class Place(val name: String, val lat: Double, val lon: Double, val weight: Int)
    private val places = listOf(
        Place("여의도한강공원 1주차장", 37.52207, 126.94085, 30),
        Place("여의도한강공원 2주차장", 37.5280, 126.9310, 6),
        Place("더현대서울", 37.5259, 126.9284, 14),
        Place("63스퀘어", 37.5198, 126.9402, 8),
        Place("국회의사당", 37.5319, 126.9140, 4),
        Place("노량진수산시장", 37.5133, 126.9405, 6),
        Place("김포공항", 37.5587, 126.7945, 6),
        Place("광명역", 37.4166, 126.8848, 6),
    )

    private data class Lot(val name: String, val lat: Double, val lon: Double, val cap: Int, val occ: Int, val share: Double)
    private val hanLots = listOf(
        Lot("1주차장", 37.52207, 126.94085, 300, 288, 0.75),
        Lot("2주차장", 37.5280, 126.9310, 200, 120, 0.25),
    )
    private val lot2 = hanLots[1]

    // ==================== 가상 주변 차 ====================
    private data class FakeCar(val name: String, val lat: Double, val lon: Double, val dest: Place, val shares: Boolean)
    private var fakeCars: List<FakeCar> = emptyList()
    private val colors = listOf("흰색", "검은색", "회색", "빨간색", "파란색", "은색")
    private val types = listOf("SUV", "세단", "경차", "트럭")
    private val nicks = listOf("불꽃구경 가요", "야근 탈출", "주말 나들이", "조용한 운전자")

    // 시연: 가상 차 60대 (막힌 길처럼 35대는 내 주변 약 450m 안, 25대는 약 2km 안)
    private val demoSeed = 20261115         // 시연: 앱을 다시 열어도 같은 주변 차·같은 숫자가 나오게 고정
    private val askRnd = Random(demoSeed)
    private var rotateRnd = Random(demoSeed + 1)
    private val nearCars = 35
    private val farCars = 25
    private fun newFakeCar(rnd: Random, near: Boolean): FakeCar {
        var r = rnd.nextInt(places.sumOf { it.weight })
        val dest = places.first { r -= it.weight; r < 0 }
        val name = if (rnd.nextFloat() < 0.25f) nicks.random(rnd) else "${colors.random(rnd)} ${types.random(rnd)}"
        val dLat = if (near) 0.0040 else 0.018
        val dLon = if (near) 0.0050 else 0.022
        return FakeCar(name, myLat + rnd.nextDouble(-dLat, dLat), myLon + rnd.nextDouble(-dLon, dLon), dest, rnd.nextFloat() < 0.7f)
    }
    private fun makeFakeCars() {
        val rnd = Random(demoSeed)          // 만들 때마다 처음부터 같은 순서로
        rotateRnd = Random(demoSeed + 1)
        fakeCars = List(nearCars + farCars) { newFakeCar(rnd, it < nearCars) }
        dlog("가상 주변 차 ${fakeCars.size}대 생성")
    }

    // 3분마다 주변 차 일부가 빠지고 새 차가 들어와요 (다시 물으면 답이 조금씩 달라지게)
    private val rotateCars = object : Runnable {
        override fun run() {
            fakeCars = fakeCars.mapIndexed { i, c -> if (rotateRnd.nextFloat() < 0.2f) newFakeCar(rotateRnd, i < nearCars) else c }
            if (myDestName != null && !parked) { showCrowd(); updateDepart() }
            handler.postDelayed(this, 180_000)
        }
    }

    // ==================== 타이머 (화면에 있을 때만) ====================
    private val handler = Handler(Looper.getMainLooper())

    // 5초마다 목적지·위치 확인
    private val checkDestination = object : Runnable {
        override fun run() {
            if (naviConnected) {
                navi?.getDestinationInfo()
                navi?.getCurrentLocationInfo()
            }
            handler.postDelayed(this, 5000)
        }
    }

    // 15~30초마다 주변 차가 나에게 물어옴 (시연)
    private val incomingAsk = object : Runnable {
        override fun run() {
            receiveAsk()
            handler.postDelayed(this, Random.nextLong(15_000, 30_000))
        }
    }

    // 신호 대기 중 묻기를 켠 경우에만, 주행 중 1초마다 브레이크 페달 확인
    private val checkBrake = object : Runnable {
        override fun run() {
            if (isDriving && signalAsk) readBrakePedal()
            handler.postDelayed(this, 1000)
        }
    }

    private val hideNote = Runnable { tvNote.visibility = View.GONE }

    // ==================== 내비 소식 ====================
    private val naviListener = object : NaviHelperEventListener {
        override fun onError(errorCode: NaviErrorCode) {
            dlog("내비 오류: $errorCode")
            runOnUiThread { showNote("현대 내비가 요청을 처리하지 못했어요 ($errorCode)") }
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            dlog("목적지: ${destinationInfo.name} " +
                    "(${destinationInfo.latitude}, ${destinationInfo.longitude}) " +
                    "남은거리 ${destinationInfo.remainDistance}m")
            showDestination(destinationInfo)
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            if (currentLocationInfo.latitude == 0.0) return
            myLat = currentLocationInfo.latitude
            myLon = currentLocationInfo.longitude
            runOnUiThread {
                destMap.setMe(myLat, myLon)
                // 처음 실제 위치를 받으면, 가상 차를 내 실제 위치 주변으로 다시 만듦
                if (!locationReady) {
                    locationReady = true
                    makeFakeCars()
                    if (myDestName != null) showCrowd()
                }
            }
        }
        override fun onDestinationChanged() { navi?.getDestinationInfo() }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            dlog("목적지 도착: ${destinationArrivedInfo.name}")
            runOnUiThread { markArrived() }
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { navi?.getDestinationInfo() }
        override fun onRoutingCompleted() { navi?.getDestinationInfo() }
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onRouteEnded() { navi?.getDestinationInfo() }
        override fun onNavigationStatus(status: NavigationStatus) {
            dlog("내비 상태: $status")
            navi?.getDestinationInfo()
        }
    }

    // ==================== 거리·방향 ====================
    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = FloatArray(2); Location.distanceBetween(lat1, lon1, lat2, lon2, r); return r[0]
    }
    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = FloatArray(2); Location.distanceBetween(lat1, lon1, lat2, lon2, r); return r[1]
    }
    private fun isSamePlace(p: Place) = distanceM(myDestLat, myDestLon, p.lat, p.lon) <= sameDistanceM
    // 한강공원 목적지인지: 이름에 '한강'이 있거나 한강공원 주차장 150m 안
    // (전에는 1주차장 1.5km 안을 다 한강공원으로 봐서, 바로 옆 더현대서울도 한강공원 주차장 대기를 보여줬어요)
    private fun isHan(name: String?, lat: Double, lon: Double) =
        name?.contains("한강") == true || hanLots.any { distanceM(lat, lon, it.lat, it.lon) <= 150f }
    private fun isHan(p: Place) = isHan(p.name, p.lat, p.lon)

    // 같은 곳 / 같은 방향 / 다른 방향 (mine: 내 진행 방향, 여러 대를 볼 때 한 번만 계산)
    private fun myBearing() = bearingDeg(myLat, myLon, myDestLat, myDestLon)
    private fun relation(c: FakeCar, mine: Float = myBearing()): Rel {
        if (isSamePlace(c.dest)) return Rel.SAME
        val theirs = bearingDeg(c.lat, c.lon, c.dest.lat, c.dest.lon)
        var diff = abs(mine - theirs)
        if (diff > 180f) diff = 360f - diff
        return if (diff <= sameDirDeg) Rel.DIR else Rel.DIFF
    }

    private fun relText(rel: Rel) = when (rel) {
        Rel.SAME -> "🎉 같은 곳 가요!"
        Rel.DIR -> "➡️ 같은 방향이에요"
        else -> "↗️ 다른 방향이에요"
    }

    // ==================== 칩 모양 ====================
    // 색은 res/values(낮)·values-night(밤)에서 가져와서 야간 모드에 자동 대응
    private fun chip(tv: TextView, bgRes: Int, fgRes: Int) {
        tv.background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(ContextCompat.getColor(this@MainActivity, bgRes))
        }
        tv.setTextColor(ContextCompat.getColor(this@MainActivity, fgRes))
    }

    // 목적지 글자: 안내 문구일 땐 작은 회색, 목적지일 땐 크고 굵게 (주행 중엔 더 크게)
    private fun styleDest(isPlace: Boolean) {
        val glance = isDriving && wideLayout == true && !signalStop     // 주행 중 전체 화면: 한눈에 읽히게 아주 크게
        val size = when { !isPlace -> if (isDriving) 28f else 20f; glance -> 64f; isDriving -> 48f; else -> 34f }
        tvDest.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tvDest.setTextColor(ContextCompat.getColor(this, if (isPlace) R.color.e_text else R.color.e_text2))
        tvDest.setTypeface(null, if (isPlace) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tvLots.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (glance) 40f else if (isDriving) 30f else 20f)
        tvCrowd.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (glance) 32f else if (isDriving) 26f else 20f)
        val subBase = baseText.getOrPut(tvSub) { tvSub.textSize }
        if (glance) tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f) else tvSub.setTextSize(TypedValue.COMPLEX_UNIT_PX, subBase * (if (wideLayout == true) 1.2f else 1.25f))
        val lotBase = baseText.getOrPut(btnLot2) { btnLot2.textSize }
        if (glance) btnLot2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f) else btnLot2.setTextSize(TypedValue.COMPLEX_UNIT_PX, lotBase * (if (wideLayout == true) 1.2f else 1.25f))
        btnLot2.minHeight = dp(if (glance) 96f else 64f)
    }

    // ==================== 작은 알림 (소리 없음) ====================
    private fun showNote(text: String, ms: Long = 3000) {
        tvNote.text = text
        tvNote.visibility = View.VISIBLE
        handler.removeCallbacks(hideNote)
        handler.postDelayed(hideNote, ms)
    }

    // ==================== 목적지 + 혼잡 ====================
    private fun showDestination(info: DestinationInfo) {
        runOnUiThread {
            if (info.name.isNullOrBlank()) {
                if (arrived || parked) { updatePark(); return@runOnUiThread }   // 도착 후 안내 종료: 주차 카드는 유지
                if (myDestName != null) clearAskResult()   // 목적지가 없어지면 지난 묻기 결과는 맞지 않아요
                myDestName = null
                drivenSinceDest = false
                tvStatus.text = "내비 연결됨"
                destMap.setDest(null, 0.0, 0.0, 0); destMap.setLots(emptyList())
                updatePark()        // 목적지가 없으면 지난 주차 위치를 보여줘요
                tvDest.text = "목적지를 내비에 입력하면 여기에 보여요"
                styleDest(false)
                tvSub.text = ""
                tvCrowd.visibility = View.GONE
                tvLots.visibility = View.GONE
                btnLot2.visibility = View.GONE
                mapRow.visibility = View.GONE
                updateQuickAsk()
                refreshCards()
                return@runOnUiThread
            }
            if (myDestName != info.name) {
                // 목적지가 바뀌면 지난 묻기 결과는 맞지 않으니 지워요
                clearAskResult()
                // 새 목적지가 생기면 도착 상태 초기화 (주차 중이면 출차할 때 처리)
                if (!parked) { arrived = false; leftDestName = null }
                drivenSinceDest = isDriving
            }
            myDestName = info.name
            myDestLat = info.latitude
            myDestLon = info.longitude
            myDestPoiId = info.poiId
            myDestPoiProvider = info.poiProvider
            remainM = info.remainDistance
            // 300m 안이면 도착. 단 목적지를 정한 뒤 실제로 운전했을 때만 (주차한 채 바로 옆 목적지를 넣으면 도착으로 보지 않아요)
            if (remainM in 1..300 && leftDestName != info.name && drivenSinceDest) markArrived()
            val km = info.remainDistance / 1000.0
            val min = info.remainTime / 60
            tvStatus.text = "내비 목적지"
            tvDest.text = info.name
            styleDest(true)
            // 주차한 채 다음 목적지를 넣었으면 '도착'이 아니라 새 목적지까지 남은 거리를 보여줘요
            val arrivedHere = arrived && (parkedPlace == null || parkedPlace == info.name)
            tvSub.text = if (arrivedHere) "도착했어요"
                else if (info.remainDistance <= 0) "경로 계산 중…"   // 목적지를 막 바꾸면 내비가 잠깐 0을 줘요
                else String.format(Locale.KOREA, "남은 거리 %.1fkm · 약 %s", km,
                    if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분")
            showCrowd()
            mapRow.visibility = if (isDriving) View.GONE else View.VISIBLE
            updatePark()          // 주차 중이면 주차 카드가 대기 정보보다 우선
            updateDepart()
            updateQuickAsk()
        }
    }

    private var drivenSinceDest = false  // 지금 목적지를 정한 뒤 주행한 적이 있는지
    private var departBaseWait = -1      // 내 목적지 주차장 대기(분), 한강공원이 아니면 -1
    private var departBaseHeading = 0    // 같은 곳 가는 차 수

    private fun showCrowd() {
        // 내 목적지와 같은 곳으로 가는 차 수 (합계로만 셈)
        val heading = fakeCars.count { it.shares && isSamePlace(it.dest) }   // 목적지를 알려준 차만 셈
        departBaseHeading = heading
        departBaseWait = -1
        val level = when {
            heading < kMin -> 0
            heading < 8 -> 1
            heading < 14 -> 2
            else -> 3
        }
        val label = when (level) {
            0 -> "여유 예상"; 1 -> "보통"; 2 -> "혼잡"; else -> "매우 혼잡"
        }
        when (level) {
            0 -> chip(tvCrowd, R.color.e_c0_bg, R.color.e_c0_fg)
            1 -> chip(tvCrowd, R.color.e_c1_bg, R.color.e_c1_fg)
            2 -> chip(tvCrowd, R.color.e_c2_bg, R.color.e_c2_fg)
            else -> chip(tvCrowd, R.color.e_c3_bg, R.color.e_c3_fg)
        }
        tvCrowd.visibility = View.VISIBLE
        tvCrowd.text = if (heading < kMin) label else "$label · 가는 차 ${heading}대"
        destMap.setDest(myDestName, myDestLat, myDestLon, level)

        // 현대 지도 버튼: 주차장 정보가 있는 곳(한강공원)만 '주차장', 나머지는 '목적지'
        findViewById<Button>(R.id.btnMapDest).text =
            if (isHan(myDestName, myDestLat, myDestLon)) "🗺️ 현대 지도에서 주차장 보기" else "🗺️ 현대 지도에서 목적지 보기"
        // 여의도한강공원 쪽이면 주차장별 대기 표시
        if (!isHan(myDestName, myDestLat, myDestLon)) {
            destMap.setLots(emptyList())
            tvLots.visibility = View.GONE
            btnLot2.visibility = View.GONE
            if (wideLayout != null) refreshCards()
            return
        }
        // 주차장 대기는 "한강공원 일대로 가는 차 전체"로 계산
        // (2주차장으로 바꿔도 1주차장 대기가 갑자기 줄어들지 않게)
        val areaHeading = fakeCars.count { isHan(it.dest) }
        val ahead = (areaHeading * 1.4).roundToInt()   // 앱 사용 비율 보정 (시연 가정)
        val waits = hanLots.map { lot ->
            val pred = lot.occ + ahead * lot.share
            max(0, ((pred - lot.cap) * 1.5).roundToInt())   // 넘치는 대수 × 1.5분
        }
        val lines = hanLots.mapIndexed { i, lot ->
            if (waits[i] >= 3) "한강공원 ${lot.name} 대기 약 ${waits[i]}분"
            else "한강공원 ${lot.name}은 여유 있어요"
        }
        tvLots.visibility = View.VISIBLE
        tvLots.text = lines.joinToString("\n")
        val myLot = hanLots.indices.minByOrNull { distanceM(myDestLat, myDestLon, hanLots[it].lat, hanLots[it].lon) } ?: 0
        departBaseWait = waits[myLot]
        if (parked) destMap.setLots(emptyList())       // 주차 중이면 지도에는 출구만 (⑤)
        else destMap.setLots(hanLots.mapIndexed { i, lot ->
            val wt = waits[i]
            DestMapView.MapLot(lot.name, lot.lat, lot.lon,
                if (wt >= 3) "대기 ${wt}분" else "여유",
                when { wt >= 30 -> 3; wt >= 15 -> 2; wt >= 3 -> 1; else -> 0 })
        })

        val goingToLot2 = distanceM(myDestLat, myDestLon, lot2.lat, lot2.lon) <= 200f
        btnLot2.visibility =
            if (!goingToLot2 && waits[0] >= 3 && waits[1] < waits[0]) View.VISIBLE else View.GONE
        if (goingToLot2) tvLots.append("\n✅ 2주차장으로 가고 있어요")
        if (wideLayout != null) refreshCards()      // 주 버튼 강조(2주차장 vs 묻기)를 다시 맞춰요
    }

    // ==================== 🅿️ 도착·주차·출차 ====================
    private fun markArrived() {
        if (arrived) return
        arrived = true
        showNote("${myDestName ?: "목적지"}에 도착했어요")
        updatePark()
        saveState()
    }

    // 도착 + 기어 P = 주차, 주차 후 D로 바꾸면 = 출차 (R·N은 자리 고치기로 보고 무시)
    private fun updatePark() {
        val inPark = gear == VehicleGear.GEAR_PARK
        if (arrived && inPark && !parked) {
            parked = true
            val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
            val place = myDestName ?: "목적지"
            parkedPlace = place; parkedLat = myDestLat; parkedLon = myDestLon
            destMap.setLots(emptyList())     // 주차 중엔 주차장 대기 대신 출구 대기를 지도에
            parkedText = "주차 완료 · $place\n$time 주차 · 위치를 기억해 둘게요"
            prefs.edit().putString("lastPark", "$place · $time").apply()
            clearAskResult()                 // 도착했으니 지난 묻기 결과는 더 이상 맞지 않아요
            dlog("주차: $place $time")
            saveState()
        } else if (parked && gear != -1 && gear != VehicleGear.GEAR_PARK &&
                   gear != VehicleGear.GEAR_NEUTRAL && gear != VehicleGear.GEAR_REVERSE) {
            // 출차는 D(전진 기어)로 바꿀 때만: 주차 자리를 고치려고 R·N을 잠깐 넣어도 출차로 보지 않아요
            parked = false
            arrived = false
            leftDestName = parkedPlace      // 주차 중 목적지를 바꿨어도 '떠난 곳'은 주차한 곳
            parkedPlace = null
            parkedText = ""
            exitAddedFor = null
            clearAskResult()
            saveState()
            showNote("출차했어요 · 다음 목적지를 내비에 입력하면 혼잡을 알려드릴게요", 5000)
            dlog("출차")
        }
        if (parked) {
            tvPark.text = parkedText
            tvPark.visibility = View.VISIBLE
            tvLots.visibility = View.GONE
            btnLot2.visibility = View.GONE
        } else {
            // 목적지가 없을 때는 지난 주차 위치를 보여줘요 (차 찾을 때)
            val last = prefs.getString("lastPark", null)
            if (last != null && myDestName == null) {
                tvPark.text = "지난 주차 · $last"
                tvPark.visibility = View.VISIBLE
            } else tvPark.visibility = View.GONE
        }
        updateExit()
        refreshCards()
    }

    // ==================== 기어 (안드로이드 자동차 표준 API) ====================
    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            gear = (value.value as? Int) ?: -1
            dlog("기어: ${gearName(gear)}")
            runOnUiThread { updatePark(); maybeAskSpeed() }   // P가 되면(정차) 처음 한 번 속도 권한 설명
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.e("Pleos", "기어 읽기 오류")
        }
    }

    private fun gearName(g: Int) = when (g) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        else -> "?"
    }

    private fun startGear(c: Car) {
        try {
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
            propManager = pm
            pm.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (t: Throwable) {
            Log.e("Pleos", "기어 구독 실패: ${t.message}")
        }
        registerSpeed()      // 이미 허용돼 있으면 바로 속도 읽기
    }

    // ==================== 차 속도 (신호 대기로 멈췄는지 정확히 알기) ====================
    // CAR_SPEED 권한은 시스템 창에 '위치 정보'로 표시돼요. 그래서
    // · 주행 중엔 절대 묻지 않고(창이 앱을 가려요), 정차(P) 중 처음 한 번만 설명을 먼저 보여준 뒤 물어요
    // · 허용하지 않으면 브레이크를 1.5초 이상 계속 밟을 때를 '멈춤'으로 봐요
    // · 속도는 이 판단에만 쓰고 저장하거나 다른 차에 보내지 않아요
    private val speedPerm = "android.car.permission.CAR_SPEED"
    @Volatile private var speedKmh = -1f              // 차 속도 (-1: 못 읽음 → 브레이크 시간으로 판단)
    private var speedRegistered = false

    private fun hasSpeedPerm() = checkSelfPermission(speedPerm) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun registerSpeed() {
        if (speedRegistered || !hasSpeedPerm()) return
        try {
            speedRegistered = propManager?.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) == true
            dlog("속도 구독: $speedRegistered")
        } catch (t: Throwable) {
            Log.e("Pleos", "속도 구독 실패: ${t.message}")
        }
    }

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val ms = (value.value as? Float) ?: return
            val kmh = abs(ms) * 3.6f
            val changed = (speedKmh in 0f..0.99f) != (kmh < 1f)
            speedKmh = kmh
            if (changed) runOnUiThread { updateQuickAsk() }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) { speedKmh = -1f }
    }

    // 정차(P) 중에만, 처음 한 번 설명하고 권한을 물어요
    private fun maybeAskSpeed() {
        if (isDriving || gear != VehicleGear.GEAR_PARK || hasSpeedPerm() || !signalAsk) return
        if (prefs.getBoolean("speedAsked", false)) return
        prefs.edit().putBoolean("speedAsked", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("신호 대기 중 묻기를 정확하게")
            .setMessage("신호 대기로 멈췄는지 알려고 차 속도만 읽어요.\n" +
                "다음 창에 '위치 정보'라고 나오지만, 실제로 쓰는 건 속도뿐이에요. " +
                "내 위치는 저장하거나 다른 차에 보내지 않아요.\n\n허용하지 않아도 브레이크를 계속 밟고 있으면 쓸 수 있어요.")
            .setPositiveButton("계속") { _, _ -> requestPermissions(arrayOf(speedPerm), 7) }
            .setNegativeButton("괜찮아요", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) registerSpeed()
            else dlog("속도 권한 거부 → 브레이크 시간으로 신호 대기 판단")
        }
    }

    // ==================== 브레이크 페달 (Pleos Vehicle SDK) ====================
    private fun readBrakePedal() {
        try {
            vehicle.getBrake().getCurrentBrakePedalStatus(
                onComplete = { pressed: Boolean? ->
                    val now = pressed == true
                    brakePressed = now
                    runOnUiThread { updateQuickAsk() }    // 밟은 시간을 보므로 매번 다시 판단
                },
                onFailed = { e: Exception -> Log.e("Pleos", "브레이크 읽기 실패: ${e.message}") }
            )
        } catch (t: Throwable) {
            Log.e("Pleos", "브레이크 읽기 실패: ${t.message}")
        }
    }

    // 주행 중 + 신호 대기 묻기 켜짐 + 브레이크 밟음 → 큰 버튼 하나만
    private fun updateQuickAsk() {
        // 멈춤 판단: 속도를 읽을 수 있으면 '브레이크 + 시속 1km 미만', 못 읽으면 '브레이크 1.5초 이상 계속'
        if (!brakePressed) stoppedSince = 0L else if (stoppedSince == 0L) stoppedSince = System.currentTimeMillis()
        val stopped = brakePressed && (if (speedKmh >= 0f) speedKmh < 1f
                                       else System.currentTimeMillis() - stoppedSince >= 1_500)
        val show = isDriving && signalAsk && stopped && visibleOn && myDestName != null && !arrived && !parked
        btnQuickAsk.visibility = if (show) View.VISIBLE else View.GONE
        btnQuickHello.visibility = btnQuickAsk.visibility      // 인사도 묻기와 같은 조건(신호 대기로 멈춤)에서만
        if (show == signalStop) return
        signalStop = show
        if (!show) { quickResult = null; btnQuickAsk.text = "다 같이 묻기" }
        applySignalStop()
    }

    // 신호 대기 화면: 조작은 큰 '다 같이 묻기' 버튼 하나만
    // · 분할 화면(내비 옆): 레이더를 같이 보여줘요 (지도는 옆의 현대 내비가 맡아요)
    // · 전체 화면: 목적지 주변 지도를 같이 보여줘요
    // 다시 출발하면 주행 화면(큰 글씨)으로 돌아가요
    // 주변 차 카드(분할 화면: 레이더 / 전체 화면: 지도)를 보여줄지
    // · 분할 화면은 주행 중에도 레이더를 계속 보여줘요 (보기만, 조작 없음)
    // · 전체 화면의 지도는 정차·신호 대기 때만 (주행 중엔 목적지 카드 아래 레이더로 대신)
    private fun showStopArea() = !isDriving || signalStop || wideLayout != true

    private fun applySignalStop() {
        if (!::askGroup.isInitialized) return
        stopArea.visibility = if (showStopArea()) View.VISIBLE else View.GONE
        askGroup.visibility = if (isDriving) View.GONE else View.VISIBLE     // 주행 중엔 일반 묻기·인사 버튼은 숨김
        refreshCards()
        styleDest(myDestName != null)
    }

    private fun distM(lat: Double, lon: Double) = hypot((lat - myLat) * 111_000.0, (lon - myLon) * 88_000.0)

    // ==================== 📣 다 같이 묻기 ====================
    private var askSame = -1        // 마지막 묻기에서 '같은 곳' 답한 차 (-1: 아직 안 물어봄·비공개)
    private var askDir = 0          // 마지막 묻기에서 '같은 방향' 답한 차
    private var tvAskTop: TextView? = null
    private var quickResult: String? = null     // 신호 대기 중 물어본 결과 (다시 출발하면 지움)
    private var signalStop = false              // 신호 대기 화면(주행 중 + 브레이크로 멈춤)인지

    private fun clearAskResult() {
        radar.setDots(emptyList())
        destMap.setSameCars(emptyList(), 0)
        askSame = -1; askDir = 0; updateAskTop()
        tvAsk.text = "같은 곳 가는 차를 물어봐요"
    }

    // 목적지 카드 맨 위 한 줄: "같은 곳 N대 · 같은 방향 M대" (주행 중에만, 물어본 결과가 있을 때)
    private fun updateAskTop() {
        val tv = tvAskTop ?: return
        val show = isDriving && askSame >= 0 && myDestName != null
        tv.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        tv.text = "🙋 같은 곳 ${askSame}대 · 같은 방향 ${askDir}대"
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (wideLayout == true) 36f else 26f)
    }

    private fun askBlockReason(nearCount: Int): String? {
        if (!visibleOn) return "'내 차 보이기'를 켜야 물어볼 수 있어요"
        if (myDestName == null) return "목적지를 정해야 같은 곳·같은 방향인지 알 수 있어요"
        if (arrived || parked) return "도착했어요. 다음 목적지로 출발하면 다시 물어볼 수 있어요"
        val now = System.currentTimeMillis()
        if (now - lastAskAt < 10_000) return "방금 물어봤어요. 잠시 뒤 다시 물어볼 수 있어요"
        while (askLog.isNotEmpty() && now - askLog.first() > 600_000) askLog.removeFirst()
        if (askLog.size >= 5) return "10분에 5번까지 물어볼 수 있어요"
        if (nearCount < 3) return "주변 차가 3대 이상일 때만 물어볼 수 있어요"
        return null
    }

    private fun askAll(quick: Boolean = false) {
        // 내 위치 500m 안의 가까운 차부터 최대 20대에게 물어요 (막힌 길일수록 많이 물어요)
        val near = fakeCars.filter { distM(it.lat, it.lon) < askRadiusM }.sortedBy { distM(it.lat, it.lon) }.take(askMax)
        askBlockReason(near.size)?.let {
            if (quick) showNote(it) else tvAsk.text = it
            return
        }
        if (quick && !prefs.contains("share")) {
            showNote("정차(P)한 뒤 '목적지 알려주기'를 정하면 같은 곳·같은 방향 차를 볼 수 있어요", 5000)
            return
        }
        lastAskAt = System.currentTimeMillis()
        askLog.addLast(lastAskAt)

        var same = 0; var dir = 0; var diff = 0; var priv = 0
        val mine = myBearing()
        val dots = near.map { c ->
            // 내가 목적지를 안 알려주면 남의 답도 모두 비공개
            val rel = if (!shareDest || !c.shares) { priv++; Rel.PRIV } else relation(c, mine).also {
                when (it) { Rel.SAME -> same++; Rel.DIR -> dir++; else -> diff++ }
            }
            dlog("${c.name} → ${c.dest.name} : $rel")
            // 레이더: 나에게서 본 실제 방향(북쪽 위, 8칸)과 거리(150m·300m·500m) 구간
            val dy = (c.lat - myLat) * 111_000.0
            val dx = (c.lon - myLon) * 88_000.0
            val bearing = (Math.toDegrees(atan2(dx, dy)) + 360) % 360
            val d = hypot(dx, dy)
            RadarView.Dot(sector = ((bearing + 22.5) / 45).toInt() % 8,
                band = when { d < 150 -> 0; d < 300 -> 1; else -> 2 }, rel = rel)
        }
        askSame = if (shareDest) same else -1; askDir = dir
        updateAskTop()
        // 개인정보: '같은 곳' 답이 5대 미만이면 레이더에서도 어느 차인지 표시하지 않아요 (숫자로만)
        radar.setDots(if (same in 1 until kMin) dots.map { if (it.rel == Rel.SAME) it.copy(rel = Rel.DIFF) else it } else dots)
        // 개인정보: '같은 곳 가요' 답이 5대 미만이면 지도에 위치를 그리지 않아요 (특정 차가 드러나지 않게)
        destMap.setSameCars(if (same >= kMin) near.filterIndexed { i, _ -> dots[i].rel == Rel.SAME }.map { DestMapView.LatLon(it.lat, it.lon) } else emptyList(),
            if (shareDest) same else 0)   // 숫자는 늘 보여주고, 위치 원은 5대 이상일 때만
        val summary = buildList {
            add("🎉 ${same}대 같은 곳")
            if (dir > 0) add("➡️ ${dir}대 같은 방향")
            if (diff > 0) add("↗️ ${diff}대 다른 방향")
            if (priv > 0) add("🙂 ${priv}대 비공개")
        }.joinToString(" · ")
        tvAsk.text = "주변 ${near.size}대 중 $summary" +
                (if (!shareDest) "\n💡 '목적지 알려주기'를 켜야 다른 차의 답도 볼 수 있어요" else "") +
                (if (shareDest && same in 1 until kMin) "\n같은 곳 가는 차 위치는 5대 이상일 때만 표시해요" else "")
        // 신호 대기 중에는 결과를 작은 알림 한 줄로만
        if (quick) {
            quickResult = if (shareDest) "같은 곳 ${same}대 · 같은 방향 ${dir}대" else "모두 비공개 · 정차 후 목적지 알려주기를 켜세요"
            btnQuickAsk.text = quickResult      // 다시 출발할 때까지 버튼 자리에 남겨요 (출발 뒤엔 맨 위 줄)
        }
    }

    // ==================== 💬 주변 차가 나에게 물어옴 → 자동 답장 ====================
    private fun receiveAsk() {
        if (!visibleOn) return                 // 내 차 보이기 꺼짐이면 아무도 못 물어봄
        if (fakeCars.isEmpty()) return
        val asker = fakeCars.random()
        val answer = when {
            !shareDest -> "🙂 비공개예요"
            parked -> "🅿️ 도착해서 주차 중이에요"
            arrived -> "🏁 방금 도착했어요"
            myDestName == null -> "❔ 아직 목적지가 없어요"
            else -> relText(relation(asker))
        }
        dlog("${asker.name}이(가) 물어봄 → 자동 답장: $answer")

        if (isDriving || !foreground) {
            // 주행 중이거나 앱이 화면에 없을 때: 띄우지 않고 모아 뒀다가 나중에 한 번에 알려줘요
            pendingAuto += asker.name
        } else if (System.currentTimeMillis() - lastAutoNoteAt > 60_000) {
            // 정차 중: 1분에 한 번만 짧게
            lastAutoNoteAt = System.currentTimeMillis()
            showNote("${asker.name}이(가) 물어봐서 답했어요 · $answer")
        }
    }

    // 자동 답장 요약에 붙이는 이름: 최대 2개까지만 (한눈에 읽히게)
    private fun autoNames(): String {
        val names = pendingAuto.distinct()
        return when {
            names.isEmpty() -> ""
            names.size <= 2 -> " (${names.joinToString(", ")})"
            else -> " (${names.take(2).joinToString(", ")} 외 ${names.size - 2}명)"
        }
    }

    // ==================== ⚙️ 내 차 설정 ====================
    private fun updateSettingsLabel() {
        btnSettings.text = if (visibleOn) "" else "비공개"   // 아이콘은 XML에 있음
    }

    private fun openSettings() {
        if (isDriving) {
            Toast.makeText(this, "설정은 정차 중에 바꿀 수 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(12f), dp(24f), 0)
        }
        val etName = EditText(this).apply {
            hint = "내 차 이름 (비우면 '$defaultName')"
            setText(myName); maxLines = 1; textSize = 18f
        }
        val swVisible = SwitchMaterial(this).apply {
            text = "내 차 보이기 (끄면 묻기·답하기 모두 안 해요)"
            isChecked = visibleOn; setPadding(0, dp(14f), 0, 0); textSize = 18f
        }
        val swShare = SwitchMaterial(this).apply {
            text = "목적지 알려주기 (켜야 다른 차의 답도 보여요)"
            isChecked = shareDest; setPadding(0, dp(14f), 0, 0); textSize = 18f
        }
        val swSignal = SwitchMaterial(this).apply {
            text = "신호 대기 중 묻기 (D에서 브레이크를 밟고 멈춰 있을 때 버튼 하나만)"
            isChecked = signalAsk; setPadding(0, dp(14f), 0, 0); textSize = 18f
        }
        box.addView(etName); box.addView(swVisible); box.addView(swShare); box.addView(swSignal)

        MaterialAlertDialogBuilder(this)   // 앱 테마를 따라 야간 모드에서도 어둡게
            .setTitle("내 차 설정")
            .setView(android.widget.ScrollView(this).apply { addView(box) })   // 분할 화면에서도 아래까지 보이게
            .setPositiveButton("저장") { _, _ ->
                var name = etName.text.toString().trim().take(10)
                if (badWords.any { name.contains(it, ignoreCase = true) }) {
                    Toast.makeText(this, "쓸 수 없는 이름이라 이름만 빼고 저장했어요", Toast.LENGTH_SHORT).show()
                    name = myName
                }
                if (swShare.isChecked != shareDest) clearAskResult()   // 공유를 바꾸면 지난 묻기 결과는 맞지 않아요
                prefs.edit()
                    .putString("name", name)
                    .putBoolean("visible", swVisible.isChecked)
                    .putBoolean("share", swShare.isChecked)
                    .putBoolean("signalAsk", swSignal.isChecked)
                    .apply()
                updateSettingsLabel()
                updateQuickAsk()
                refreshCards()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ==================== 현대 내비 지도 움직이기 ====================
    // 지도는 현대 내장 내비를 그대로 쓰고, 우리 앱은 "어디를 보여줄지"만 요청해요 (NaviHelper requestNavigationScreen)
    private fun showOnNavi(req: NavigationScreenRequest, okText: String) {
        val helper = navi
        if (helper == null || !naviConnected) { showNote("내비에 아직 연결되지 않았어요"); return }
        try {
            helper.requestNavigationScreen(req)
            dlog("내비 화면 요청: $req")
            showNote(okText)
        } catch (t: Throwable) {
            Log.e("Pleos", "내비 화면 요청 실패: ${t.message}")
            showNote("현대 내비 화면을 바꾸지 못했어요")
        }
    }

    // 목적지를 현대 지도 위 상세 팝업으로. POI 번호가 없으면 이름으로 검색 화면을 열어요
    private fun showDestOnNavi() {
        val name = myDestName ?: run { showNote("먼저 내비에 목적지를 정해 주세요"); return }
        if (myDestPoiId.isNotBlank()) {
            showOnNavi(NavigationScreenRequest.POIDetailPopup(myDestPoiId, myDestPoiProvider), "현대 내비 지도에 $name 정보를 띄웠어요")
        } else {
            showOnNavi(NavigationScreenRequest.SearchPlace(name), "현대 내비에서 $name 을(를) 찾았어요")
        }
    }

    private var lot2Pending: Runnable? = null     // 주행 중 '2주차장으로 바꾸기'를 누르고 기다리는 3초

    private fun goToLot2() {
        val helper = navi
        if (helper == null || !naviConnected) {
            tvStatus.text = "내비에 아직 연결되지 않았어요"
            return
        }
        try {
            helper.requestRoute(
                RouteInfo(
                    latitude = lot2.lat,
                    longitude = lot2.lon,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = "여의도한강공원 2주차장",
                    poiId = "",
                    poiSubId = "0",
                    address = "",
                    routeOption = RouteOption.RECOMMENDED,
                )
            )
            tvStatus.text = "내비 경로를 바꾸는 중…"
            // 바뀐 경로를 현대 지도에서 바로 보여줘요
            handler.postDelayed({
                showOnNavi(NavigationScreenRequest.OverviewRoute(RouteOverviewShowInfo(6000)), "현대 내비에 2주차장 가는 길을 띄웠어요")
            }, 1500)
        } catch (e: Exception) {
            Log.e("Pleos", "경로 변경 실패: ${e.message}")
            tvStatus.text = "경로 변경 실패"
        }
    }

    // ==================== ④ 출발 시간 추천 ====================
    // 도착 전·정차 중: 지금 / 10분 / 30분 / 1시간 뒤 출발할 때의 혼잡을 막대로 비교
    private fun updateDepart() {
        if (myDestName == null) return
        departRate = departRates()
        val values = departRate.map { r ->
            if (departBaseWait >= 0) (departBaseWait * r).roundToInt() else (departBaseHeading * r).roundToInt()
        }
        val fewCars = departBaseWait < 0 && departBaseHeading < kMin   // 같은 곳 가는 차가 적으면 시간대 비교가 의미 없어요
        departBest = if (fewCars) 0 else values.indices.minByOrNull { values[it] } ?: 0
        val key = "$values|$departBaseWait|$departBaseHeading|$wideLayout|${departAlarm != null}"
        if (key == departKey) return          // 바뀐 게 없으면 막대를 다시 만들지 않아요
        departKey = key
        val maxV = max(1, values.maxOrNull() ?: 1)
        departBars.removeAllViews()
        if (fewCars) {
            tvDepart.text = "이 목적지는 붐비지 않을 것 같아요 · 언제 출발해도 괜찮아요"
            btnDepartAlarm.visibility = View.GONE
            departBars.visibility = View.GONE
            return
        }
        departBars.visibility = View.VISIBLE
        val barH = dp(if (wideLayout == true) 52f else 80f)   // 전체 화면은 레이더를 키운 만큼 막대를 낮게
        values.forEachIndexed { i, v ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val track = android.widget.FrameLayout(this).apply {
                background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(ContextCompat.getColor(this@MainActivity, R.color.e_soft)) }
            }
            val bar = View(this).apply {
                val color = if (i == departBest) R.color.e_c0_fg else R.color.e_radar_ring   // 추천 시간만 초록, 나머지는 옅은 회색
                background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(ContextCompat.getColor(this@MainActivity, color)) }
            }
            track.addView(bar, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                max(dp(6f), barH * v / maxV), Gravity.BOTTOM))
            col.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH))
            col.addView(TextView(this).apply {
                text = departSlots[i].first; textSize = if (wideLayout == true) 22f else 18f; gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, if (i == departBest) R.color.e_accent else R.color.e_text))
                if (i == departBest) setTypeface(null, android.graphics.Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = if (departBaseWait >= 0) (if (v >= 3) "대기 ${v}분" else "여유") else if (v >= kMin) "${v}대" else "적음"
                textSize = if (wideLayout == true) 19f else 16f; gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.e_text3))
            })
            departBars.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = if (i == 0) 0 else dp(8f)
            })
        }
        val best = departSlots[departBest]
        tvDepart.text = if (departBest == 0) "지금 출발이 가장 덜 붐벼요"
            else "${best.first} 출발 추천 · ${100 - (departRate[departBest] * 100).roundToInt()}% 덜 붐벼요 (요일·시간대 통계)"
        btnDepartAlarm.visibility = if (departBest == 0) View.GONE else View.VISIBLE
        btnDepartAlarm.text = if (departAlarm != null) "⏰ 알림 취소" else "⏰ ${best.first}에 알려주기"
    }

    private var departKey = ""

    private fun scheduleDepart(at: Long) {
        departAlarm?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            departAlarm = null
            val dest = prefs.getString("departDest", null) ?: myDestName ?: "목적지"
            prefs.edit().remove("departAt").remove("departDest").apply()
            showNote("⏰ 지금 출발하면 ${dest}이(가) 덜 붐벼요", 8000)
            updateDepart()
        }
        departAlarm = r
        handler.postDelayed(r, max(0L, at - System.currentTimeMillis()))
    }

    private fun toggleDepartAlarm() {
        departAlarm?.let {
            handler.removeCallbacks(it); departAlarm = null
            DepartReceiver.cancel(this)
            prefs.edit().remove("departAt").remove("departDest").apply()
            showNote("출발 알림을 취소했어요"); updateDepart(); return
        }
        val (label, min) = departSlots[departBest]
        setDepartAlarm(System.currentTimeMillis() + min * 60_000L, label)
    }

    private fun setDepartAlarm(at: Long, label: String) {
        prefs.edit().putLong("departAt", at).putString("departDest", myDestName).apply()
        scheduleDepart(at)
        DepartReceiver.schedule(this, at, myDestName ?: "목적지")     // 앱이 화면에 없어도 시스템 알림으로
        // 알림 권한은 정차 중 알림을 맞출 때 한 번만 물어요
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 8)
        showNote("$label(${SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(at))})에 알려드릴게요 · 다른 화면을 봐도 알림이 와요", 4000)
        updateDepart()
    }

    // ==================== ⑤ 나갈 때 안내 ====================
    // 주차 중: 출구별 대기를 비교하고, 다음 목적지(집 등)를 내비에 넣으면 추천 출구를 경유지로 넣어요
    private fun updateExit() {
        val inHan = parked && isHan(parkedPlace, parkedLat, parkedLon)
        if (!inHan) {
            exitGroup.visibility = View.GONE
            destMap.setExits(emptyList()); bestExit = null
            return
        }
        val area = fakeCars.count { isHan(it.dest) }
        val waits = hanExits.map { (area * 0.8 * it.rate).roundToInt() }
        val later = hanExits.map { (area * 0.8 * it.rate * 0.35).roundToInt() }
        val bi = waits.indices.minByOrNull { waits[it] } ?: 0
        bestExit = hanExits[bi]
        val lines = hanExits.mapIndexed { i, e -> "${e.name} · 지금 ${waits[i]}분 / 30분 뒤 ${later[i]}분" }
        val hasNext = myDestName != null && myDestName != parkedPlace
        tvExit.text = lines.joinToString("\n") + "\n➡️ 추천: ${hanExits[bi].name}" +
            if (hasNext) " (→ $myDestName)" else "\n집 등 다음 목적지를 내비에 넣으면 추천 출구를 경유지로 넣어드려요"
        btnExit.visibility = if (hasNext && !isDriving) View.VISIBLE else View.GONE
        exitGroup.visibility = View.VISIBLE
        destMap.setExits(hanExits.mapIndexed { i, e ->
            DestMapView.MapLot(e.name, e.lat, e.lon, "${waits[i]}분", if (i == bi) 0 else if (waits[i] >= 15) 3 else 1)
        })
    }

    private var exitAddedFor: String? = null      // 경유지를 이미 넣은 다음 목적지 (두 번 눌러 중복으로 넣지 않게)
    private fun addExitWaypoint() {
        val helper = navi
        val e = bestExit
        if (helper == null || !naviConnected || e == null) { showNote("내비에 아직 연결되지 않았어요"); return }
        if (exitAddedFor == myDestName) { showNote("이미 ${e.name}를 경유지로 넣었어요"); return }
        try {
            helper.addWaypoint(RequestWaypointInfo(
                latitude = e.lat, longitude = e.lon, poiName = e.name,
                waypointIndex = WaypointIndex.FIRST, poiId = "", address = "", poiSubId = "0"
            ))
            exitAddedFor = myDestName
            showNote("${e.name}를 현대 내비 경유지로 넣었어요")
            handler.postDelayed({
                showOnNavi(NavigationScreenRequest.OverviewRoute(RouteOverviewShowInfo(6000)), "현대 내비에 출구 경유 경로를 띄웠어요")
            }, 1500)
        } catch (t: Throwable) {
            Log.e("Pleos", "경유지 추가 실패: ${t.message}")
            showNote("경유지를 넣지 못했어요")
        }
    }

    // ==================== 👋 인사 ====================
    // 정차 중이나 신호 대기로 멈춰 있을 때만, 가장 가까운 차 한 대에게. 30초에 한 번까지
    private fun sayHello() {
        if (isDriving && !signalStop) { showNote("인사는 멈춰 있을 때 보낼 수 있어요"); return }
        if (!visibleOn) { showNote("'내 차 보이기'를 켜야 인사할 수 있어요"); return }
        val now = System.currentTimeMillis()
        if (now - lastHelloAt < 30_000) { showNote("방금 인사했어요. 잠시 뒤 다시 보낼 수 있어요"); return }
        val target = fakeCars.minByOrNull { distanceM(myLat, myLon, it.lat, it.lon) } ?: return
        lastHelloAt = now
        showNote("${target.name}에게 👋 인사를 보냈어요")
        if (Random.nextFloat() < 0.7f) handler.postDelayed({
            showNote("${target.name}이(가) 👋 답인사를 보냈어요", 4000)
        }, Random.nextLong(2_000, 5_000))
    }

    // ==================== 정차 / 주행 ====================
    private fun applyDriving(restrictions: CarUxRestrictions?) {
        runOnUiThread {
            lastRestrictions = restrictions
            val real = restrictions?.isRequiresDistractionOptimization ?: false
            val driving = simDriving ?: real
            val wasDriving = isDriving
            isDriving = driving
            if (driving && myDestName != null) drivenSinceDest = true
            val tag = if (simDriving != null) " (시연)" else ""
            if (driving) {
                tvDrive.text = "주행 중$tag"
                chip(tvDrive, R.color.e_drive_bg, R.color.e_drive_fg)
                stopArea.visibility = if (showStopArea()) View.VISIBLE else View.GONE   // 주행 중엔 묻기 버튼만 숨기고 레이더는 보여줘요
                askGroup.visibility = View.GONE
                mapRow.visibility = View.GONE          // 주행 중엔 지도 조작 버튼도 숨김
                if (::cardAsk.isInitialized) { if (myDestName != null && !parked) showCrowd(); updateExit(); refreshCards() }
                styleDest(myDestName != null)          // 대신 목적지·대기 글자를 크게
                updateQuickAsk()
            } else {
                tvDrive.text = "정차 중$tag"
                chip(tvDrive, R.color.e_stop_bg, R.color.e_stop_fg)
                signalStop = false; quickResult = null; btnQuickAsk.text = "다 같이 묻기"
                stopArea.visibility = View.VISIBLE
                askGroup.visibility = View.VISIBLE
                mapRow.visibility = if (myDestName != null) View.VISIBLE else View.GONE
                if (::cardAsk.isInitialized) { updateExit(); refreshCards() }
                styleDest(myDestName != null)
                brakePressed = false
                btnQuickAsk.visibility = View.GONE
                btnQuickHello.visibility = View.GONE
                // 주행 중에 받은 질문을 정차하면 한 번에 알려줌
                if (wasDriving && pendingAuto.isNotEmpty()) {
                    showNote("주행 중에 ${pendingAuto.size}명에게 자동으로 답했어요" + autoNames(), 6000)
                    pendingAuto.clear()
                }
            }
        }
    }

    private fun startDrivingState() {
        try {
            val c = Car.createCar(this) ?: return applyDriving(null)
            @Suppress("DEPRECATION")
            val mgr = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (mgr == null) { c.disconnect(); return applyDriving(null) }
            car = c
            uxManager = mgr
            startGear(c)
            mgr.registerListener { r -> applyDriving(r) }
            applyDriving(mgr.currentCarUxRestrictions)
        } catch (t: Throwable) {
            Log.e("Pleos", "주행 상태 읽기 실패: ${t.message}")
            applyDriving(null)
        }
    }

    // ==================== 전체 화면 / 분할 화면 ====================
    // 웹 시연 페이지와 같은 구분이에요.
    // Pleos 화면은 왼쪽 차량 패널 옆 오른쪽 영역에 앱이 떠요.
    // · 전체 화면(오른쪽 영역을 다 씀, 가로가 세로보다 넓음): 왼쪽 목적지 주변 지도 + 오른쪽 목적지·①~⑤ 카드
    // · 분할 화면(내비와 반씩 나눔, 세로가 더 김): 목적지(혼잡·주차·출구 포함) → 작은 레이더·묻기 순서로 위아래
    private var wideLayout: Boolean? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()

    private fun applyWindowLayout() {
        val cfg = resources.configuration
        val w = cfg.screenWidthDp
        val h = cfg.screenHeightDp.coerceAtLeast(1)
        val wide = w >= h * 1.15f
        if (wide == wideLayout) return
        wideLayout = wide
        dlog("창 크기 ${w}x${h}dp → ${if (wide) "전체 화면(웹 시연 화면 배치)" else "분할 화면(위젯)"}")

        val gap = dp(16f)
        fun detach(v: View) { (v.parent as? ViewGroup)?.removeView(v) }
        fun lp(weight: Float) = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight)

        // 먼저 모든 카드를 떼어내고, 옮겼던 묶음은 원래 자리로 되돌려요
        listOf(destCard, stopArea, cardAsk, cardDepart, cardExit).forEach { detach(it) }
        leftCol?.let { detach(it) }; rightCol?.let { detach(it) }
        cards.removeAllViews()
        homes.sortedBy { it.second.second }.forEach { (v, home) ->
            detach(v)
            home.first.addView(v, home.second.coerceAtMost(home.first.childCount))
        }

        if (wide) {
            // 웹 시연 화면과 같은 배치: 왼쪽 목적지 주변 지도 | 오른쪽 목적지 + ①~⑤ 카드
            val left = leftCol ?: LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { leftCol = it }
            val right = rightCol ?: LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { rightCol = it }
            left.removeAllViews(); right.removeAllViews()
            cards.orientation = LinearLayout.HORIZONTAL
            cards.addView(left, lp(1.2f).apply { marginEnd = gap })
            cards.addView(right, lp(1f))
            left.addView(stopArea, lp(0f).apply { topMargin = gap })
            // 스크롤 없이 한 화면에: 목적지(혼잡 칩·2주차장 버튼·주차 완료 포함) → ④ 또는 ⑤ → ① 묻기
            // (②의 주차장 대기·⑤의 출구 대기는 왼쪽 지도에서 보여줘요)
            listOf(cardAsk, destCard, cardExit, cardDepart).forEach {   // 앱의 목적: ① 묻기가 맨 위
                right.addView(it, lp(0f).apply { topMargin = gap })
            }
            val askSlot = findViewById<LinearLayout>(R.id.askSlot)
            detach(radarGroup); askSlot.addView(radarGroup)     // 레이더는 ① 카드로 (작게)
            detach(askGroup); askSlot.addView(askGroup)
            destMap.visibility = View.VISIBLE                   // 왼쪽 큰 판은 목적지 주변 지도
            detach(exitGroup); (findViewById<LinearLayout>(R.id.exitSlot)).addView(exitGroup)
            findViewById<TextView>(R.id.tvRadarTitle).text = "목적지 주변 지도(단순화) · 길 안내는 현대 내비"
        } else {
            // 분할 화면(내비 옆 위젯): 목적지(혼잡 포함) → 주변 차(레이더 + 묻기)
            cards.orientation = LinearLayout.VERTICAL
            cards.addView(destCard, lp(0f).apply { topMargin = gap })
            cards.addView(stopArea, lp(0f).apply { topMargin = gap })
            findViewById<TextView>(R.id.tvRadarTitle).text = "주변 차"
            destMap.visibility = View.GONE
        }

        // 레이더는 작게, 지도는 왼쪽 세로를 거의 다 쓰게 (머리줄·여백만 빼고)
        radar.layoutParams = radar.layoutParams.apply { height = dp(if (wide) 180f else (h * 0.38f).coerceIn(200f, 300f)) }   // 분할 화면은 남는 세로를 레이더로 채워요
        destMap.layoutParams = destMap.layoutParams.apply { height = dp((h - 150f).coerceAtLeast(300f)) }
        radar.invalidate()

        // 글씨 배율: 전체 화면 1.2배, 분할 화면도 1.25배 (반쪽 폭이라도 팔 길이 거리에서 읽기 쉽게)
        val f = if (wide) 1.2f else 1.25f
        listOf(destCard, stopArea, cardAsk, cardDepart, cardExit, tvNote).forEach { scaleTexts(it, f) }
        (tvDrive.parent as? View)?.let { scaleTexts(it, f) }   // 머리줄(앱 이름·시연 버튼·정차/주행)도 같은 배율로
        if (myDestName != null) { showCrowd(); updatePark() }   // 분할 화면으로 돌아오면 주차장 대기 줄을 다시 (주차 중이면 주차 카드 우선)
        departKey = ""; updateDepart()       // ④ 막대 글자 크기를 화면 모드에 맞게 다시 만들어요
        refreshCards()
        styleDest(myDestName != null)       // 화면 모드에 맞는 목적지·대기 글자 크기
    }

    // 글씨 크기: 처음 크기를 기억해 두고 배율만 바꿔요 (목적지 이름·대기 줄은 styleDest가 따로 정해요)
    private val baseText = java.util.WeakHashMap<TextView, Float>()
    private fun scaleTexts(v: View, f: Float) {
        if (v is TextView && v !== tvDest && v !== tvLots && v !== tvCrowd && v !== tvAskTop) {
            // 처음 크기를 기억하되, 팔 길이 거리에서 읽히도록 17sp보다 작게는 두지 않아요
            val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics)
            val base = baseText.getOrPut(v) { max(v.textSize, minPx) }
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * f)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) scaleTexts(v.getChildAt(i), f)
    }

    // 전체 화면 카드의 보이기·안내 문구를 지금 상태에 맞춰요
    private fun refreshCards() {
        val wide = wideLayout == true
        cardAsk.visibility = if (wide) View.VISIBLE else View.GONE      // 전체 화면: 주행 중에도 레이더는 보여요 (묻기 버튼은 숨김)
        if (::stopArea.isInitialized) stopArea.visibility = if (showStopArea()) View.VISIBLE else View.GONE
        // 전체 화면: 정차 중엔 ① 묻기가 맨 위, 주행 중엔 목적지가 맨 위이고 레이더는 그 아래
        rightCol?.let { col ->
            if (wide && cardAsk.parent === col && destCard.parent === col) {
                val wantAskFirst = !isDriving
                if ((col.indexOfChild(cardAsk) < col.indexOfChild(destCard)) != wantAskFirst) {
                    col.removeView(cardAsk)
                    col.addView(cardAsk, if (wantAskFirst) 0 else col.indexOfChild(destCard) + 1)
                }
            }
        }
        leftCol?.visibility = if (wide && isDriving && !signalStop) View.GONE else View.VISIBLE   // 주행 중엔 목적지 카드가 가로 전체를 써요 (신호 대기 땐 지도)
        // 정차 중 전체 화면은 주차장 대기를 지도에서 보여줘요. 주행 중엔 지도가 숨겨지니 대기 줄을 크게 다시 보여요
        if (wide && !isDriving && tvLots.visibility == View.VISIBLE) tvLots.visibility = View.GONE
        // ④는 출발 전(기어 P)에만: 신호 대기 같은 잠깐 정차 때는 ① 묻기를 가리지 않게
        val beforeDeparture = gear == VehicleGear.GEAR_PARK || gear == -1
        cardDepart.visibility = if (wide && !isDriving && beforeDeparture && myDestName != null && !arrived && !parked) View.VISIBLE else View.GONE
        cardExit.visibility = if (wide && exitGroup.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        btnHello.visibility = if (!isDriving && visibleOn) View.VISIBLE else View.GONE

        // 주 버튼은 '다 같이 묻기' 하나 (앱의 목적). 정차 중엔 2주차장·출구 버튼을 테두리 버튼으로 한 단계 낮춰요
        // 주행 중엔 묻기 카드가 숨으니 2주차장 버튼이 다시 주 버튼이 돼요
        if (lot2Pending == null) btnLot2.text = if (isDriving) "2주차장으로 바꾸기" else "2주차장으로 출발"   // 달리는 중엔 '출발'이 어색해요
        val secondary = !isDriving
        val pBg = ContextCompat.getColor(this, R.color.e_primary_bg)
        val pFg = ContextCompat.getColor(this, R.color.e_primary_fg)
        val txt = ContextCompat.getColor(this, R.color.e_text)
        listOf(btnLot2, btnExit).forEach { b ->
            (b as? MaterialButton)?.apply {
                backgroundTintList = ColorStateList.valueOf(if (secondary) Color.TRANSPARENT else pBg)
                setTextColor(if (secondary) txt else pFg)
                iconTint = ColorStateList.valueOf(if (secondary) txt else pFg)
                strokeColor = ColorStateList.valueOf(txt)
                strokeWidth = if (secondary) dp(2f) else 0
            }
        }
        // 주행 중에는 목적지 카드 맨 위에 묻기 결과를 크게 (혼잡은 그 결과로 아래에)
        updateAskTop()

        // 도착·주차 중엔 물어볼 일이 없으니 '다 같이 묻기'를 숨기고 이유만 보여줘요
        btnAsk.visibility = if (arrived || parked) View.GONE else View.VISIBLE
        if (arrived || parked) tvAsk.text = "도착했어요 · 다음 목적지로 출발하면 다시 물어볼 수 있어요"

        // 주행 중 전체 화면: 목적지 카드가 화면을 다 쓰고, 내용은 세로 가운데에 크게 (한눈에 보기)
        val glance = wide && isDriving && !signalStop
        val hDp = resources.configuration.screenHeightDp
        destCard.minimumHeight = if (glance) dp(((hDp - 140f) * 0.5f).coerceAtLeast(220f)) else 0
        // 레이더 크기: 전체 화면 주행 중엔 남는 세로를 채우게 키워요
        val radarH = dp(if (!wide) (hDp * 0.38f).coerceIn(200f, 300f) else if (glance) (hDp * 0.30f).coerceIn(170f, 280f) else 180f)
        if (radar.layoutParams.height != radarH) radar.layoutParams = radar.layoutParams.apply { height = radarH }
        (destCard as? LinearLayout)?.gravity = if (glance) Gravity.CENTER_VERTICAL else Gravity.TOP
    }

    // 분할 화면 크기를 바꿔도 앱이 다시 시작되지 않으니(configChanges), 여기서 배치만 바꿔요
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyWindowLayout()
    }

    // ==================== 도착·주차 상태 저장 ====================
    // 앱을 닫았다 열어도(Pleos는 독 아이콘을 누르면 앱을 닫아요), 야간 모드 전환으로 화면이 다시 만들어져도 기억해요
    private fun saveState() {
        prefs.edit().putBoolean("st_arrived", arrived).putBoolean("st_parked", parked)
            .putString("st_parkedPlace", parkedPlace).putString("st_parkedLat", parkedLat.toString())
            .putString("st_parkedLon", parkedLon.toString()).putString("st_parkedText", parkedText)
            .putString("st_leftDest", leftDestName).apply()
    }

    private fun restoreState() {
        arrived = prefs.getBoolean("st_arrived", false)
        parked = prefs.getBoolean("st_parked", false)
        parkedPlace = prefs.getString("st_parkedPlace", null)
        parkedLat = prefs.getString("st_parkedLat", null)?.toDoubleOrNull() ?: 0.0
        parkedLon = prefs.getString("st_parkedLon", null)?.toDoubleOrNull() ?: 0.0
        parkedText = prefs.getString("st_parkedText", null) ?: ""
        leftDestName = prefs.getString("st_leftDest", null)
        if (parked) { tvPark.text = parkedText; tvPark.visibility = View.VISIBLE }
    }

    // ==================== 앱 시작 ====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvDest = findViewById(R.id.tvDest)
        tvSub = findViewById(R.id.tvSub)
        tvCrowd = findViewById(R.id.tvCrowd)
        tvLots = findViewById(R.id.tvLots)
        tvDrive = findViewById(R.id.tvDrive)
        tvAsk = findViewById(R.id.tvAsk)
        tvNote = findViewById(R.id.tvNote)
        btnLot2 = findViewById(R.id.btnLot2)
        btnQuickAsk = findViewById(R.id.btnQuickAsk)
        btnQuickHello = findViewById(R.id.btnQuickHello)
        tvPark = findViewById(R.id.tvPark)
        btnArriveSim = findViewById(R.id.btnArriveSim)
        btnAsk = findViewById(R.id.btnAsk)
        btnSim = findViewById(R.id.btnSim)
        // 시연 버튼은 맨 위 줄(앱 이름 옆)로: 전체 화면에서 아래로 밀려 안 보이는 일이 없게
        (btnSim.parent as? ViewGroup)?.let { simRow ->
            val header = (findViewById<LinearLayout>(R.id.main)).getChildAt(0) as ViewGroup
            (simRow.parent as? ViewGroup)?.removeView(simRow)
            header.addView(simRow, 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // 시연 버튼은 평소엔 숨기고, 앱 이름("어디가세요?")을 길게 누르면 보였다 숨겼다 해요
            simRow.visibility = View.GONE
            header.getChildAt(0).setOnLongClickListener {
                simRow.visibility = if (simRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                showNote(if (simRow.visibility == View.VISIBLE) "시연 버튼을 보여요" else "시연 버튼을 숨겼어요", 1500)
                true
            }
        }
        btnSettings = findViewById(R.id.btnSettings)
        radar = findViewById(R.id.radar)
        // 범례: 같은 곳은 파랑, 같은 방향은 초록으로 (레이더와 같은 색)
        findViewById<TextView>(R.id.tvLegend).text = android.text.SpannableStringBuilder().apply {
            fun part(t: String, colorRes: Int?) {
                val st = length; append(t)
                if (colorRes != null) setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, colorRes)), st, length, 0)
            }
            part("●  같은 곳", R.color.e_accent); part("      ", null)
            part("◌  같은 방향", R.color.e_c0_fg); part("      ○  다른 방향·비공개", null)
        }
        stopArea = findViewById(R.id.stopArea)
        cards = findViewById(R.id.cards)
        destCard = findViewById(R.id.destCard)
        tvAskTop = TextView(this).apply {
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.e_accent))
            setPadding(0, 0, 0, dp(12f))
            visibility = View.GONE
        }
        (destCard as ViewGroup).addView(tvAskTop, 0)
        mapRow = findViewById(R.id.mapRow)
        findViewById<Button>(R.id.btnMapDest).setOnClickListener { showDestOnNavi() }
        crowdGroup = findViewById(R.id.crowdGroup)
        radarGroup = findViewById(R.id.radarGroup)
        destMap = findViewById(R.id.destMap)
        askGroup = findViewById(R.id.askGroup)
        cardAsk = findViewById(R.id.cardAsk)
        exitGroup = findViewById(R.id.exitGroup)
        tvExit = findViewById(R.id.tvExit)
        btnExit = findViewById(R.id.btnExit)
        btnHello = findViewById(R.id.btnHello)
        cardDepart = findViewById(R.id.cardDepart)
        cardExit = findViewById(R.id.cardExit)
        departBars = findViewById(R.id.departBars)
        tvDepart = findViewById(R.id.tvDepart)
        btnDepartAlarm = findViewById(R.id.btnDepartAlarm)
        // 분할 화면 배치(XML)의 원래 자리를 기억해 두고, 전체 화면에서 옮겼다가 되돌려요
        homes = listOf(crowdGroup, tvPark, exitGroup, radarGroup, askGroup).map { v ->
            val p = v.parent as ViewGroup
            v to (p to p.indexOfChild(v))
        }
        applyWindowLayout()

        tvCrowd.visibility = View.GONE
        tvLots.visibility = View.GONE
        btnLot2.visibility = View.GONE
        refreshCards()        // 처음 안내 문구 상태를 맞춰요

        btnLot2.setOnClickListener {
            val pending = lot2Pending
            when {
                pending != null -> {             // 한 번 더 누르면 취소
                    handler.removeCallbacks(pending); lot2Pending = null
                    showNote("경로를 바꾸지 않았어요"); refreshCards()
                }
                isDriving -> {                   // 주행 중엔 잘못 눌렀을 때를 위해 3초 기다려요
                    val r = Runnable { lot2Pending = null; refreshCards(); goToLot2() }
                    lot2Pending = r
                    btnLot2.text = "3초 뒤 2주차장으로 바꿔요 · 누르면 취소"
                    handler.postDelayed(r, 3000)
                }
                else -> goToLot2()
            }
        }
        btnAsk.setOnClickListener {
            if (!prefs.contains("share") && visibleOn) {
                // 처음 묻기: 내 목적지도 알려줘야 남의 답을 볼 수 있다는 걸 알려주고 고르게 해요
                MaterialAlertDialogBuilder(this)
                    .setTitle("내 목적지도 알려줄까요?")
                    .setMessage("같은 곳·같은 방향인지 서로 알려면 내 목적지도 알려줘야 해요.\n목적지 이름은 보여주지 않고 '같은 곳·같은 방향' 여부만 답해요. 설정(⚙)에서 언제든 바꿀 수 있어요.")
                    .setPositiveButton("켜고 묻기") { _, _ -> prefs.edit().putBoolean("share", true).apply(); updateSettingsLabel(); askAll() }
                    .setNegativeButton("그냥 묻기") { _, _ -> prefs.edit().putBoolean("share", false).apply(); askAll() }
                    .show()
            } else askAll()
        }
        btnQuickAsk.setOnClickListener { askAll(quick = true) }
        btnQuickHello.setOnClickListener { sayHello() }
        btnArriveSim.setOnClickListener {          // 시연: 에뮬레이터에선 실제로 도착할 수 없어서
            if (myDestName == null) {
                showNote("먼저 내비에 목적지를 정해 주세요")
            } else {
                arrived = false; parked = false; leftDestName = null   // 시연은 누를 때마다 다시 도착 처리
                markArrived()
            }
        }
        btnSettings.setOnClickListener { openSettings() }
        btnExit.setOnClickListener { addExitWaypoint() }
        btnHello.setOnClickListener { sayHello() }
        btnDepartAlarm.setOnClickListener { toggleDepartAlarm() }
        // 시연: 알림 버튼이나 ④ 안내 문구를 길게 누르면 15초 뒤 알림 (10분을 기다릴 수 없어서)
        val demoAlarm = View.OnLongClickListener {
            if (departAlarm == null && myDestName != null) setDepartAlarm(System.currentTimeMillis() + 15_000, "15초 뒤(시연)")
            true
        }
        btnDepartAlarm.setOnLongClickListener(demoAlarm)
        tvDepart.setOnLongClickListener(demoAlarm)
        btnSim.setOnClickListener {
            simDriving = if (simDriving == true) false else true
            btnSim.text = if (simDriving == true) "정차 시연으로" else "주행 시연 켜기"
            applyDriving(lastRestrictions)
        }
        updateSettingsLabel()

        // Pleos 가이드: Back 키를 누르면 앱을 끄지 않고 뒤로 숨김
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })

        makeFakeCars()   // 위치를 받기 전 임시 생성 (실제 위치를 받으면 다시 만듦)
        restoreState()   // 앱을 다시 열었거나 야간 모드 전환으로 다시 만들어진 경우 도착·주차 상태 복원

        // 1. 차량 SDK
        vehicle = Vehicle(this)
        try { vehicle.initialize() } catch (e: Exception) {
            Log.e("Pleos", "차량 SDK 시작 실패: ${e.message}")      // 기어·브레이크 없이도 앱은 계속 동작
        }

        // 2. 내비 SDK (실패하면 5초 뒤 다시, 최대 5번)
        val helper = NaviHelper(this)
        navi = helper
        connectNavi(helper)

        // 3. 주행 규제 상태
        startDrivingState()
    }

    private var naviRetry = 0
    private fun connectNavi(first: NaviHelper) {
        val helper = if (naviRetry == 0) first else {
            runCatching { navi?.release() }
            NaviHelper(this).also { navi = it }       // 해제한 객체를 다시 쓰지 않고 새로 만들어요
        }
        helper.initialize(
            onConnected = {
                dlog("내비 연결됨")
                naviConnected = true
                runOnUiThread { tvStatus.text = "내비 연결됨" }
                helper.addListener(naviListener)
                helper.getDestinationInfo()
                helper.getCurrentLocationInfo()
            },
            onFailed = { e ->
                Log.e("Pleos", "내비 연결 실패: ${e.message}")
                runOnUiThread {
                    if (naviRetry < 5) {
                        naviRetry++
                        tvStatus.text = "내비 연결 중… (다시 시도 $naviRetry)"
                        handler.postDelayed({ connectNavi(helper) }, 5000)
                    } else tvStatus.text = "내비 연결 실패 · 앱을 다시 열어 주세요"
                }
            }
        )
    }

    // ==================== 화면에 보이는 동안만 동작 ====================
    private var foreground = false     // 앱이 화면에 보이는지

    override fun onStart() {
        super.onStart()
        foreground = true; inForeground = true
        DepartReceiver.clear(this)                  // 앱을 열었으니 떠 있던 출발 알림은 지워요
        handler.removeCallbacks(rotateCars); handler.postDelayed(rotateCars, 180_000)
        handler.post(checkDestination)
        handler.removeCallbacks(incomingAsk)        // 화면 밖에서도 돌던 자동 답장 타이머를 다시 맞춰요
        handler.postDelayed(incomingAsk, 8_000)     // 첫 질문은 8초 뒤
        handler.post(checkBrake)
        // 화면 밖에 있던 동안 대신 답한 질문을 한 번에 알려줘요 (주행 중이면 멈췄을 때)
        if (!isDriving && pendingAuto.isNotEmpty()) {
            showNote("앱이 화면에 없던 동안 ${pendingAuto.size}명에게 자동으로 답했어요" + autoNames(), 6000)
            pendingAuto.clear()
        }
        // 앱이 닫혀 있던 동안 출발 알림 시간이 지났으면 지금 알려주고, 남았으면 다시 맞춰요
        prefs.getLong("departAt", 0L).takeIf { it > 0 }?.let { scheduleDepart(it) }
    }

    override fun onStop() {
        foreground = false; inForeground = false
        handler.removeCallbacks(rotateCars)
        lot2Pending?.let { handler.removeCallbacks(it); lot2Pending = null }
        handler.removeCallbacks(checkDestination)
        // incomingAsk는 멈추지 않아요: 내비만 띄워 놓고 운전해도 다른 차의 질문에 자동으로 답해요
        handler.removeCallbacks(checkBrake)
        departAlarm?.let { handler.removeCallbacks(it) }   // 화면에 없을 땐 멈추고, 다시 보이면 이어서
        saveState()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { navi?.removeListener(naviListener) }
        runCatching { navi?.release() }
        runCatching { uxManager?.unregisterListener() }
        runCatching { propManager?.unregisterCallback(gearCallback) }
        if (speedRegistered) runCatching { propManager?.unregisterCallback(speedCallback) }
        runCatching { car?.disconnect() }
        runCatching { vehicle.release() }
        super.onDestroy()
    }
}

// ==================== 출발 알림 (앱이 화면에 없을 때) ====================
// 앱이 화면에 있으면 앱 안의 한 줄 알림으로, 없으면 시스템 알림(짧은 소리 + 한 줄)으로 알려줘요
class DepartReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (MainActivity.inForeground) return          // 화면에 있으면 앱이 직접 알려줘요
        val dest = intent.getStringExtra("dest") ?: "목적지"
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(android.app.NotificationChannel(CH, "출발 알림", android.app.NotificationManager.IMPORTANCE_HIGH))
        val open = android.app.PendingIntent.getActivity(context, 0,
            android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(context, CH)
            .setSmallIcon(R.drawable.ic_campaign)
            .setContentTitle("어디가세요?")
            .setContentText("⏰ 지금 출발하면 ${dest}이(가) 덜 붐벼요")
            .setCategory(android.app.Notification.CATEGORY_NAVIGATION)     // 차량 화면에서는 이동 관련 알림만 위에 잠깐 떠요
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(ID, n) }
    }
    companion object {
        private const val CH = "depart"
        private const val ID = 1
        private fun pi(c: Context, dest: String?) = android.app.PendingIntent.getBroadcast(c, 1,
            android.content.Intent(c, DepartReceiver::class.java).putExtra("dest", dest),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        fun schedule(c: Context, at: Long, dest: String) {
            val am = c.getSystemService(android.app.AlarmManager::class.java) ?: return
            runCatching { am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi(c, dest)) }
        }
        fun cancel(c: Context) {
            runCatching { c.getSystemService(android.app.AlarmManager::class.java)?.cancel(pi(c, null)) }
            clear(c)
        }
        fun clear(c: Context) { runCatching { c.getSystemService(android.app.NotificationManager::class.java)?.cancel(ID) } }
    }
}
