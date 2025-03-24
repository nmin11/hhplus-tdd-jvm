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

    fun chargeUserPoint(id: Long, amount: Long): UserPoint {
        val userPoint = userPointTable.insertOrUpdate(id, amount)
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis())
        return userPoint
    }

    fun useUserPoint(id: Long, amount: Long): UserPoint {
        var userPoint = getUserPoint(id)
        userPoint = userPointTable.insertOrUpdate(id, userPoint.point - amount)
        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis())
        return userPoint
    }
}
