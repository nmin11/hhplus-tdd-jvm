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

    private val mockUserPoint = UserPoint(
        id = 1L,
        point = 5000L,
        updateMillis = now
    )

    private val mockPointHistories = listOf(
        PointHistory(
            id = 1L,
            userId = 1L,
            amount = 10000L,
            type = TransactionType.CHARGE,
            timeMillis = now - 10000
        ),
        PointHistory(
            id = 2L,
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

    /*
     * 사용자 조회 시 id에 0 이하의 값이 주어지면,
     * 0 이하의 id가 존재할 수 없으므로,
     * IllegalArgumentException 예외가 발생하는지 테스트
     */
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

    /*
     * 사용자 조회 시 기존에 없는 id에 대한 조회 요청이 들어오면,
     * getOrDefault 전략에 따라 새로운 UserPoint 생성 및 반환하므로,
     * 새로운 UserPoint가 정상적으로 생성되는지 테스트
     */
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

    /*
     * 사용자 조회 시 기존에 존재하는 id에 대한 정상적인 조회 요청이라면,
     * 해당하는 UserPoint를 정상 반환하는 것에 대해 테스트
     */
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

    /*
     * 사용자 포인트 사용내역 조회 시 기존에 없는 id에 대한 조회 요청이 들어오면,
     * 새로운 UserPoint 생성 및 비어 있는 사용내역이 생성되므로,
     * 비어 있는 사용내역이 정상적으로 생성되는지에 대해 테스트
     */
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

    /*
     * 사용자 포인트 사용내역 조회 시 기존에 존재하는 id에 대한 조회 요청이 들어오면,
     * 해당하는 포인트 사용내역을 정상 반환하는 것에 대해 테스트
     */
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

    /*
     * 사용자 포인트 충전 시 id에 0 이하의 값이 주어지면,
     * 0 이하의 id가 존재할 수 없으므로,
     * IllegalArgumentException 예외가 발생하는지 테스트
     */
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

    /*
     * 사용자 포인트 충전 시 0 이하의 값으로 충전하려는 경우,
     * 유효하지 않은 포인트 충전 요청이므로,
     * IllegalArgumentException 예외가 발생하는지 테스트
     */
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

    /*
     * 사용자 포인트 충전 시 사용자의 보유 포인트가 100만이 넘도록 요청이 들어오면,
     * 사용자 포인트 최대치는 100만을 넘을 수 없으므로,
     * IllegalStateException 예외가 발생하는지 테스트
     */
    @Test
    fun `chargeUserPoint_100만이_넘도록_포인트를_충전하려고_하면_예외_발생`() {
        // given
        // 기존 5,000 포인트가 있으므로 합하면 100만 초과
        val invalidAmount = 996_000L

        every { userPointTable.selectById(1L) } returns mockUserPoint

        // when
        val exception = assertThrows<IllegalStateException> {
            pointService.chargeUserPoint(mockUserPoint.id, mockUserPoint.point + invalidAmount)
        }

        // then
        assertThat(exception.message).isEqualTo("포인트는 1,000,000원을 초과할 수 없습니다.")
    }

    /*
     * 사용자 포인트 충전 시 기존에 없는 id에 대한 조회 요청이 들어오면,
     * getOrDefault 전략에 따라 UserPoint 생성 후 포인트를 충전하므로,
     * 새로운 UserPoint가 정상적으로 생성되고 포인트도 충전되어 있는 상태인지 테스트
     */
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

    /*
     * 사용자 포인트 충전 시 기존에 존재하는 id에 대한 정상적인 충전 요청이라면,
     * 기존 사용자에 대한 포인트 충전이 정상적으로 작동하는지 테스트
     */
    @Test
    fun `chargeUserPoint_기존에_있는_유저의_포인트_충전_성공`() {
        // given
        val chargeAmount = 1000L
        val updatedPoint = mockUserPoint.point + chargeAmount

        every { userPointTable.selectById(1L) } returns mockUserPoint

        every { userPointTable.insertOrUpdate(mockUserPoint.id, updatedPoint) }
            .answers {
                UserPoint(
                    id = mockUserPoint.id,
                    point = updatedPoint,
                    updateMillis = now
                )
            }

        every { pointHistoryTable.insert(mockUserPoint.id, chargeAmount, TransactionType.CHARGE, any()) }
            .answers {
                PointHistory(
                    id = 3L,
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
        assertThat(result.point).isEqualTo(updatedPoint)
        verifySequence {
            userPointTable.selectById(mockUserPoint.id)
            userPointTable.insertOrUpdate(mockUserPoint.id, updatedPoint)
            pointHistoryTable.insert(mockUserPoint.id, chargeAmount, TransactionType.CHARGE, any())
        }
    }

    /*
     * 사용자 포인트 사용 시 id에 0 이하의 값이 주어지면,
     * 0 이하의 id가 존재할 수 없으므로,
     * IllegalArgumentException 예외가 발생하는지 테스트
     */
    @Test
    fun `useUserPoint_id가_0_이하인_경우_예외_발생`() {
        // given
        val invalidId = 0L
        val amount = 1000L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(invalidId, amount)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("유저 ID에는 0 이하의 값을 입력할 수 없습니다.")
    }

    /*
     * 사용자 포인트 사용 시 0 이하의 값으로 사용하려는 경우,
     * 유효하지 않은 포인트 사용 요청이므로,
     * IllegalArgumentException 예외가 발생하는지 테스트
     */
    @Test
    fun `useUserPoint_음수_금액이면_예외_발생`() {
        // given
        val invalidAmount = -1000L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(1L, invalidAmount)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("포인트 사용 금액은 0보다 큰 정수여야 합니다.")
    }

    /*
     * 사용자 포인트 충전 시 사용자의 보유 포인트를 초과하는 사용 요청이 들어오면,
     * 사용자 포인트가 0 미만으로 내려갈 수 없는 정책이므로,
     * IllegalStateException 예외가 발생하는지 테스트
     */
    @Test
    fun `useUserPoint_보유_포인트보다_많이_사용하려고_하면_예외_발생`() {
        // given
        val useAmount = 6000L
        every { userPointTable.selectById(1L) } returns mockUserPoint

        // when
        val exception = assertThrows<IllegalStateException> {
            pointService.useUserPoint(mockUserPoint.id, useAmount)
        }

        // then
        assertThat(exception)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("보유 포인트가 부족합니다.")
    }

    /*
     * 사용자 포인트 사용 시 기존에 존재하는 id에 대한 정상적인 사용 요청이라면,
     * 기존 사용자에 대한 포인트 사용이 정상적으로 작동하는지 테스트
     */
    @Test
    fun `useUserPoint_보유_포인트가_충분하면_포인트_사용_성공`() {
        // given
        val useAmount = 3000L
        val updatedPoint = mockUserPoint.point - useAmount

        every { userPointTable.selectById(1L) } returns mockUserPoint

        every { userPointTable.insertOrUpdate(mockUserPoint.id, updatedPoint) }
            .answers {
                UserPoint(mockUserPoint.id, updatedPoint, now)
            }

        every { pointHistoryTable.insert(mockUserPoint.id, useAmount, TransactionType.USE, any()) }
            .answers {
                PointHistory(
                    id = 3L,
                    userId = mockUserPoint.id,
                    TransactionType.USE,
                    amount = useAmount,
                    timeMillis = System.currentTimeMillis()
                )
            }

        // when
        val result = pointService.useUserPoint(mockUserPoint.id, useAmount)

        // then
        assertThat(result.id).isEqualTo(mockUserPoint.id)
        assertThat(result.point).isEqualTo(updatedPoint)
        verifySequence {
            userPointTable.selectById(mockUserPoint.id)
            userPointTable.insertOrUpdate(mockUserPoint.id, updatedPoint)
            pointHistoryTable.insert(mockUserPoint.id, useAmount, TransactionType.USE, any())
        }
    }
}
