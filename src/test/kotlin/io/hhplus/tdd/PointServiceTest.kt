package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.PointHistory
import io.hhplus.tdd.point.PointService
import io.hhplus.tdd.point.TransactionType
import io.hhplus.tdd.point.UserPoint
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PointServiceTest {
    @MockK
    private lateinit var userPointTable: UserPointTable

    @MockK
    private lateinit var pointHistoryTable: PointHistoryTable

    private lateinit var pointService: PointService

    private val now = System.currentTimeMillis()

    private var mockUserPoint = UserPoint(
        id = 1L,
        point = 5000L,
        updateMillis = now
    )

    private var mockPointHistories = mutableListOf(
        PointHistory(
            id = 1L,
            userId = 1L,
            amount = 10000L,
            type = TransactionType.CHARGE,
            timeMillis = now - 10000
        ),
        PointHistory(
            id = 1L,
            userId = 1L,
            amount = 5000L,
            type = TransactionType.USE,
            timeMillis = now
        )
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    @Test
    fun `getUserPoint_id가_0_이하인_경우_예외_발생`() {
        // given
        val invalidId = 0L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            pointService.getUserPoint(invalidId)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("유저 ID에는 0 이하의 값을 입력할 수 없습니다.")
    }

    @Test
    fun `getUserPoint_기존에_없는_id이면_새로운_유저_반환`() {
        // given
        val newUserId = 999L
        every { userPointTable.selectById(newUserId) } answers {
            UserPoint(
                id = newUserId,
                point = 0L,
                updateMillis = now
            )
        }

        // when
        val result = pointService.getUserPoint(newUserId)

        // then
        assertThat(result.id).isEqualTo(newUserId)
        assertThat(result.point).isEqualTo(0L)
        assertThat(result.updateMillis).isEqualTo(now)
    }

    @Test
    fun `getUserPoint_올바른_유저_정보_반환`() {
        // given
        every { userPointTable.selectById(1L) } returns mockUserPoint

        // when
        val result = pointService.getUserPoint(1L)

        // then
        assertThat(result.id).isEqualTo(mockUserPoint.id)
        assertThat(result.point).isEqualTo(mockUserPoint.point)
        assertThat(result.updateMillis).isEqualTo(mockUserPoint.updateMillis)
        verify(exactly = 1) { userPointTable.selectById(1L) }
    }

    @Test
    fun `getUserPointHistories_유저가_없거나_사용_내역이_없으면_빈_배열_반환`() {
        // given
        val userId = 2L
        every { pointHistoryTable.selectAllByUserId(userId) } returns emptyList()

        // when
        val result = pointService.getUserPointHistories(userId)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getUserPointHistories_올바른_포인트_사용_내역_반환`() {
        // given
        every { pointHistoryTable.selectAllByUserId(1L) } returns mockPointHistories

        // when
        val result = pointService.getUserPointHistories(1L)

        // then
        assertThat(result).hasSize(2)
        assertThat(result).containsExactlyElementsOf(mockPointHistories)
        verify(exactly = 1) { pointHistoryTable.selectAllByUserId(1L) }
    }

    @Test
    fun `chargeUserPoint_id가_0_이하인_경우_예외_발생`() {
        // given
        val invalidId = 0L
        val amount = 1000L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            pointService.chargeUserPoint(invalidId, amount)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("유저 ID에는 0 이하의 값을 입력할 수 없습니다.")
    }

    @Test
    fun `chargeUserPoint_음수_금액이면_예외_발생`() {
        // given
        val invalidAmount = -1000L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            pointService.chargeUserPoint(1L, invalidAmount)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("포인트 충전 금액은 0보다 큰 정수여야 합니다.")
    }

    @Test
    fun `chargeUserPoint_기존에_없는_id이면_새로운_유저_생성_후_포인트_충전`() {
        // given
        val newUserId = 2L
        val chargeAmount = 1000L

        every { userPointTable.selectById(newUserId) } answers {
            UserPoint(id = newUserId, point = 0L, updateMillis = now)
        }

        every { userPointTable.insertOrUpdate(newUserId, chargeAmount) } answers {
            UserPoint(id = newUserId, point = chargeAmount, updateMillis = now)
        }

        every { pointHistoryTable.insert(newUserId, chargeAmount, TransactionType.CHARGE, any()) } answers {
            PointHistory(
                id = 1,
                userId = newUserId,
                type = TransactionType.CHARGE,
                amount = chargeAmount,
                timeMillis = System.currentTimeMillis()
            )
        }

        // when
        val result = pointService.chargeUserPoint(newUserId, chargeAmount)

        // then
        assertThat(result.id).isEqualTo(newUserId)
        assertThat(result.point).isEqualTo(chargeAmount)
        verifySequence {
            userPointTable.selectById(newUserId)
            userPointTable.insertOrUpdate(newUserId, chargeAmount)
            pointHistoryTable.insert(newUserId, chargeAmount, TransactionType.CHARGE, any())
        }
    }

    @Test
    fun `chargeUserPoint_기존에_있는_유저의_포인트_충전_성공`() {
        // given
        val chargeAmount = 1000L

        every { userPointTable.selectById(1L) } returns mockUserPoint

        every { userPointTable.insertOrUpdate(mockUserPoint.id, mockUserPoint.point + chargeAmount) }
            .answers {
                UserPoint(
                    id = mockUserPoint.id,
                    point = mockUserPoint.point + chargeAmount,
                    updateMillis = now
                )
            }

        every { pointHistoryTable.insert(mockUserPoint.id, chargeAmount, TransactionType.CHARGE, any()) }
            .answers {
                PointHistory(
                    id = 3,
                    userId = mockUserPoint.id,
                    type = TransactionType.CHARGE,
                    amount = chargeAmount,
                    timeMillis = System.currentTimeMillis()
                )
            }

        // when
        val result = pointService.chargeUserPoint(mockUserPoint.id, amount = chargeAmount)

        // then
        assertThat(result.id).isEqualTo(mockUserPoint.id)
        assertThat(result.point).isEqualTo(mockUserPoint.point + chargeAmount)
        verifySequence {
            userPointTable.selectById(mockUserPoint.id)
            userPointTable.insertOrUpdate(mockUserPoint.id, mockUserPoint.point + chargeAmount)
            pointHistoryTable.insert(mockUserPoint.id, chargeAmount, TransactionType.CHARGE, any())
        }
    }
}
