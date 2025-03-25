package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable
) {
    private val MAX_POINT = 1_000_000L

    fun getUserPoint(id: Long): UserPoint {
        require(id > 0) { "유저 ID에는 0 이하의 값을 입력할 수 없습니다." }
        return userPointTable.selectById(id)
    }

    fun getUserPointHistories(id: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(id)
    }

    fun chargeUserPoint(id: Long, amount: Long): UserPoint {
        require(amount > 0) { "포인트 충전 금액은 0보다 큰 정수여야 합니다." }

        var userPoint = getUserPoint(id)

        if (userPoint.point + amount > MAX_POINT) {
            throw IllegalStateException("포인트는 1,000,000원을 초과할 수 없습니다.")
        }

        userPoint = userPointTable.insertOrUpdate(id, userPoint.point + amount)
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
