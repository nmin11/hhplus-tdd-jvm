package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@AutoConfigureMockMvc
class PointIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userPointTable: UserPointTable

    @Autowired
    lateinit var pointHistoryTable: PointHistoryTable

    @Test
    fun `포인트를_보유하고_있고_포인트_관련_내역이_있는_기존_사용자_조회`() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 5000L)
        pointHistoryTable.insert(userId, 10000L, TransactionType.CHARGE, System.currentTimeMillis())
        pointHistoryTable.insert(userId, 5000L, TransactionType.USE, System.currentTimeMillis())

        // when & then
        mockMvc
            .perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(5000L))

        mockMvc
            .perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `기존에_없는_유저를_조회하면_포인트_및_포인트_사용_내역이_없는_새_유저_생성`() {
        // given
        val nonExistenceId = 2L

        // when & then
        mockMvc
            .perform(get("/point/$nonExistenceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(nonExistenceId))
            .andExpect(jsonPath("$.point").value(0))

        mockMvc
            .perform(get("/point/$nonExistenceId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `95만_포인트를_가진_유저에게_3만_포인트_충전_요청이_2개가_동시에_들어오면_하나는_실패`() {
        // given
        val userId = 3L
        userPointTable.insertOrUpdate(userId, 950_000L)
        val chargeAmount = 30000L
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<MockHttpServletResponse>()

        // when
        repeat(2) {
            executor.submit {
                try {
                    val result = mockMvc
                        .perform(
                            patch("/point/$userId/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(chargeAmount.toString())
                        ).andReturn().response
                    results.add(result)
                } catch (e: Exception) {
                    println("예외 발생: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        val successCount = results.count { it.status == 200 }
        val failCount = results.count {
            it.status == 500 &&
            String(it.contentAsByteArray, Charsets.UTF_8)
                .contains("포인트는 1,000,000원을 초과할 수 없습니다.")
        }

        assertThat(successCount).isEqualTo(1)
        assertThat(failCount).isEqualTo(1)

        val userPoint = userPointTable.selectById(userId)
        val pointHistories = pointHistoryTable.selectAllByUserId(userId)

        assertThat(userPoint.point).isEqualTo(980_000L)
        assertThat(pointHistories.size).isEqualTo(1)
    }

    @Test
    fun `5000포인트를_가진_유저에게_3000_포인트_사용_요청이_2개_동시에_들어오면_하나는_실패`() {
        // given
        val userId = 4L
        userPointTable.insertOrUpdate(userId, 5_000L)
        val useAmount = 3_000L
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<MockHttpServletResponse>()

        // when
        repeat(2) {
            executor.submit {
                try {
                    val result = mockMvc
                        .perform(
                            patch("/point/$userId/use")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(useAmount.toString())
                        ).andReturn().response
                    results.add(result)
                } catch (e: Exception) {
                    println("예외 발생: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // then
        val successCount = results.count { it.status == 200 }
        val failCount = results.count {
            it.status == 500 &&
                String(it.contentAsByteArray, Charsets.UTF_8)
                    .contains("보유 포인트가 부족합니다.")
        }

        assertThat(successCount).isEqualTo(1)
        assertThat(failCount).isEqualTo(1)

        val userPoint = userPointTable.selectById(userId)
        val pointHistories = pointHistoryTable.selectAllByUserId(userId)

        assertThat(userPoint.point).isEqualTo(2_000L)
        assertThat(pointHistories.size).isEqualTo(1)
    }

    @Test
    fun `충전과_사용_요청이_동시에_들어와도_값이_유효하다면_정상적으로_처리`() {
        // given
        val userId = 5L
        userPointTable.insertOrUpdate(userId, 10_000L)
        val chargeAmount = 1_000L
        val useAmount = 500L
        val latch = CountDownLatch(10)
        val executor = Executors.newFixedThreadPool(10)
        val results = mutableListOf<MockHttpServletResponse>()

        // when
        val tasks = mutableListOf<() -> MockHttpServletResponse>()

        tasks.addAll(List(5) {
            {
                mockMvc.perform(
                    patch("/point/$userId/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeAmount.toString())
                ).andReturn().response
            }
        })

        tasks.addAll(List(5) {
            {
                mockMvc.perform(
                    patch("/point/$userId/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useAmount.toString())
                ).andReturn().response
            }
        })

        tasks.shuffled().forEach { task ->
            executor.submit {
                try {
                    results.add(task())
                } catch (e: Exception) {
                    println("예외 발생: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // then
        val successCount = results.count { it.status == 200 }
        val userPoint = userPointTable.selectById(userId)
        val pointHistories = pointHistoryTable.selectAllByUserId(userId)

        assertThat(successCount).isEqualTo(10)
        assertThat(userPoint.point).isEqualTo(12_500L)
        assertThat(pointHistories.size).isEqualTo(10)
    }
}
