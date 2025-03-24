package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.PointService
import io.mockk.MockKAnnotations
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
}
