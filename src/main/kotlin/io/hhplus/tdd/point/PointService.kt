package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable
) {
    fun getUserPoint(id: Long): UserPoint {
        return userPointTable.selectById(id)
    }

    fun getUserPointHistory(id: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(id)
    }
}
