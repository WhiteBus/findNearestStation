package com.example.temp_odsay_project

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.temp_odsay_project.remote.Adapter.TransitAdapter
import com.example.temp_odsay_project.remote.dto.PathInfoStation
import com.example.temp_odsay_project.remote.dto.PathResult
import com.example.temp_odsay_project.remote.dto.SubPathInfo
import com.example.temp_odsay_project.remote.dto.TransitInfo
import com.example.temp_odsay_project.remote.service.PathService
import com.example.temp_odsay_project.remote.view.PathView
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit


class searchPubPathT : AppCompatActivity(), PathView {

    private lateinit var pathService: PathService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PathService 초기화
        val retrofit = RetrofitModule.getRetrofit()
        pathService = PathService(retrofit)

        // 시작점 = 현위치 가져오기
        val startX = FindCurrentPosition.GlobalValue_current.current_x
        val startY = FindCurrentPosition.GlobalValue_current.current_y

        println("${startX},${startY}")

        // 끝점 = 검색한 정류장 x,y 가져오기
        val endStation = Main_vi_Search_des.GlobalValues_last.lastPointStation




        if (startX != null && startY != null && endStation != null) {
            val endX = endStation.x
            val endY = endStation.y
            pathService.searchPath("0", startX, startY, endX, endY, 2, "9pGlz1x7Ic6zBCmZBccmM/QF2qYHiLksHbxjUBdiv3I", this)
        } else {
            println("Please select both start and end stations.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSearchPathSuccess(response: PathResult, startX: Double, startY: Double, endX: Double, endY: Double) {
        val paths = response.result?.paths
        if (paths != null) {
            val pathInfoList = mutableListOf<PathInfoStation>()
            val busNumbersMap = mutableMapOf<Int, MutableList<String>>() // pathIndex별로 bus numbers를 저장할 맵
            val stationIDList = mutableSetOf<Int>() // 시작할 수 있는 stationID를 저장할 세트

            paths.forEachIndexed { pathIndex, path ->
                val subPathInfoList = mutableListOf<SubPathInfo>()
                val subPathMap = mutableMapOf<Int, MutableList<String>>()

                path.subPaths.forEachIndexed { subPathIndex, subPath ->
                    subPath.lane?.forEach { lane ->
                        val busNo = lane.busNo
                        if (busNo != null) {
                            subPathMap.computeIfAbsent(subPathIndex) { mutableListOf() }.add(busNo)

                            subPathInfoList.add(
                                SubPathInfo(
                                    index = subPathIndex,
                                    distance = subPath.distance,
                                    sectionTime = subPath.sectionTime,
                                    startName = subPath.startName ?: "Unknown",
                                    startX = subPath.startX,
                                    startY = subPath.startY,
                                    startID = subPath.startID,
                                    endName = subPath.endName ?: "Unknown",
                                    endX = subPath.endX,
                                    endY = subPath.endY,
                                    busNo = busNo
                                )
                            )

                            subPath.startID?.let { stationIDList.add(it) }

                            // pathIndex에 해당하는 busNumbers 리스트에 busNo 추가
                            busNumbersMap.computeIfAbsent(pathIndex) { mutableListOf() }.add(busNo)
                        }
                    }
                }

                pathInfoList.add(
                    PathInfoStation(
                        index = pathIndex,
                        busTransitCount = path.pathInfo.busTransitCount,
                        firstStartStation = path.pathInfo.firstStartStation,
                        firstStartStationX = startX,
                        firstStartStationY = startY,
                        lastEndStation = path.pathInfo.lastEndStation,
                        lastEndStationX = endX,
                        lastEndStationY = endY,
                        totalTime = path.pathInfo.totalTime,
                        totalDistance = path.pathInfo.totalDistance,
                        subPaths = subPathInfoList
                    )
                )



                // Print path details
                println("Path Index: $pathIndex")
                println("Bus Transit Count: ${path.pathInfo.busTransitCount}")
                println("All Bus Numbers for Path Index $pathIndex: ${busNumbersMap[pathIndex]?.joinToString(", ")}")
                println("Total Time: ${path.pathInfo.totalTime}")
                println("Total Distance: ${path.pathInfo.totalDistance}")

                subPathMap.forEach { (subPathIndex, busNos) ->
                    val subPathInfo = subPathInfoList.find { it.index == subPathIndex }
                    if (subPathInfo != null) {
                        println("  SubPath Index: $subPathIndex")
                        println("  ${subPathInfo.startName} (${subPathInfo.startID}) to ${subPathInfo.endName} - Bus No: ${busNos.joinToString(", ")}")
                        println("    Distance: ${subPathInfo.distance}")
                        println("    Section Time: ${subPathInfo.sectionTime} minutes")
                        println("    Start Coordinates: (${subPathInfo.startX}, ${subPathInfo.startY})")
                        println("    End Coordinates: (${subPathInfo.endX}, ${subPathInfo.endY})")
                    }
                }
            }

            // 끝점 = 검색한 정류장 이름 가져오기 (xml에 보여줄려고)
            val lastStationName = intent.getStringExtra("laststationname")
            // Main_Bus_Arrival로 이동
            val intent = Intent(this, Main_Bus_Arrival::class.java)
            intent.putIntegerArrayListExtra("stationIDList", ArrayList(stationIDList)) // 중복 제거된 stationID 리스트 전달
            intent.putParcelableArrayListExtra("pathInfoList", ArrayList(pathInfoList))
            // 각 pathIndex별로 busNumbers를 전달
            busNumbersMap.forEach { (pathIndex, busNumbers) ->
                intent.putStringArrayListExtra("busNumbers_$pathIndex", ArrayList(busNumbers))
            }
            //목적지 station이름
            intent.putExtra("selectedStationName", lastStationName) // 선택된 역 이름을 전달

            startActivity(intent)
        } else {
            println("Error: PathResult paths is null")
        }
    }

    override fun onSearchPathFailure(errorMessage: String) {
        println("Error occurred: $errorMessage")
    }

    // class selectPath에서 subPathInfo.startID를 기준으로 가장 빨리 오는 allBusNos 버스 구해서 경로 선택하기
}