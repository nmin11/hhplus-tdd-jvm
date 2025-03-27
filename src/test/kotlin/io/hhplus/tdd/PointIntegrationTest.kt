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
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PointIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userPointTable: UserPointTable

    @Autowired
    lateinit var pointHistoryTable: PointHistoryTable

    /*
     * 기존에 존재하는 사용자에 대해,
     * 보유하고 있는 포인트 정보, 포인트 내역이 정상적으로 조회되는지 테스트
     */
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

    /*
     * 기존에 존재하지 않는 사용자를 조회할 경우,
     * 새로운 사용자가 생성되며, 포인트가 0이고 포인트 내역이 존재하지 않는 것에 대해 테스트
     */
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

    /*
     * 한 사용자에 대해 100만 포인트가 넘도록 하는 포인트 충전 요청이 동시에 들어오면,
     * 사용자의 포인트는 정책상 100만을 넘을 수 없으므로,
     * 100만을 넘지 않는 요청들만 성공하는 것에 대한 동시성 테스트
     */
    @Test
    fun `95만_포인트를_가진_유저에게_3만_포인트_충전_요청이_2개가_동시에_들어오면_하나는_실패`() {
        // given
        val userId = 1L
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

    /*
     * 한 사용자에 대해 0 포인트보다 적어지도록 포인트 사용 요청이 동시에 들어오면,
     * 사용자의 포인트는 정책상 0보다 적어질 수 없으므로,
     * 포인트가 0이 되지 않도록 하는 요청들만 성공하는 것에 대한 동시성 테스트
     */
    @Test
    fun `5000포인트를_가진_유저에게_3000_포인트_사용_요청이_2개_동시에_들어오면_하나는_실패`() {
        // given
        val userId = 1L
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

    /*
     * 동시에 여러 건의 포인트 충전 및 사용 요청이 들어올 때,
     * 모든 요청이 성공적으로 완수되고, 포인트 및 사용 내역이 정상적인 경우에 대해 테스트
     */
    @Test
    fun `충전과_사용_요청이_동시에_들어와도_값이_유효하다면_정상적으로_처리`() {
        // given
        val userId = 1L
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
