package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable
) {
    private val maxPoint = 1_000_000L

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    fun getUserPoint(id: Long): UserPoint {
        require(id > 0) { "유저 ID에는 0 이하의 값을 입력할 수 없습니다." }
        return userPointTable.selectById(id)
    }

    fun getUserPointHistories(id: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(id)
    }

    fun chargeUserPoint(id: Long, amount: Long): UserPoint {
        require(amount > 0) { "포인트 충전 금액은 0보다 큰 정수여야 합니다." }

        val lock = locks.computeIfAbsent(id) { ReentrantLock() }
        lock.lock()
        try {
            var userPoint = getUserPoint(id)

            if (userPoint.point + amount > maxPoint) {
                throw IllegalStateException("포인트는 1,000,000원을 초과할 수 없습니다.")
            }

            userPoint = userPointTable.insertOrUpdate(id, userPoint.point + amount)
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis())
            return userPoint
        } catch (e: Exception) {
            logger.error("포인트 충전 중 예외 발생: ${e.message}")
            throw e
        } finally {
            lock.unlock()
        }
    }

    fun useUserPoint(id: Long, amount: Long): UserPoint {
        require(amount > 0) { "포인트 사용 금액은 0보다 큰 정수여야 합니다." }

        var userPoint = getUserPoint(id)

        if (userPoint.point < amount) {
            throw IllegalStateException("보유 포인트가 부족합니다.")
        }

        userPoint = userPointTable.insertOrUpdate(id, userPoint.point - amount)
        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis())
        return userPoint
    }
}
