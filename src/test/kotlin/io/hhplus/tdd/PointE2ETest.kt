package io.hhplus.tdd

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
class PointE2ETest {
    @Autowired
    lateinit var mockMvc: MockMvc

    /*
     * 존재하지 않는 사용자를 조회해서 getOrDefault 전략에 따라 새로운 사용자를 생성하고,
     * 생성한 사용자에 대한 포인트 충전 동시성 요청을 수행하고,
     * 또 해당 사용자에 대한 포인트 사용 동시성 요청도 수행한 이후,
     * 해당 사용자의 포인트와 포인트 내역이 정상적으로 생성되었는지 확인
     */
    @Test
    fun `사용자_생성_포인트_충전_포인트_사용_포인트_내역_조회`() {
        val userId = 1L

        // 1. getOrDefault 전략에 따라 새로운 사용자 생성
        mockMvc
            .perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0L))

        // 2. 포인트 충전 동시 요청
        val chargeAmount = 30_000L
        val chargeLatch = CountDownLatch(2)
        val chargeExecutor = Executors.newFixedThreadPool(2)

        repeat(2) {
            chargeExecutor.submit {
                mockMvc.perform(
                    patch("/point/$userId/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeAmount.toString())
                ).andReturn()
                chargeLatch.countDown()
            }
        }

        chargeLatch.await()

        // 3. 포인트 사용 동시 요청
        val useAmount = 20_000L
        val useLatch = CountDownLatch(2)
        val useExecutor = Executors.newFixedThreadPool(2)

        repeat(2) {
            useExecutor.submit {
                mockMvc.perform(
                    patch("/point/$userId/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useAmount.toString())
                ).andReturn()
                useLatch.countDown()
            }
        }

        useLatch.await()

        // 4. 사용자 포인트 다시 조회
        mockMvc
            .perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(20_000L))

        // 5. 사용자 포인트 내역 조회
        mockMvc
            .perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$[1].type").value("CHARGE"))
            .andExpect(jsonPath("$[2].type").value("USE"))
            .andExpect(jsonPath("$[3].type").value("USE"))
            .andExpect(jsonPath("$.length()").value(4))
    }

    /*
     * 사용자를 생성하고,
     * 해당 사용자의 포인트가 100만이 넘도록 동시 요청을 할 경우,
     * 예외가 발생하는지 테스트하며,
     * 테스트 이후 정상적인 포인트 내역만 존재하는지 확인
     */
    @Test
    fun `사용자_포인트_100만_초과에_대한_동시성_테스트`() {
        val userId = 2L

        // 1. getOrDefault 전략에 따라 새로운 사용자 생성
        mockMvc
            .perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0L))

        // 2. 포인트 충전 동시 요청
        val chargeAmount = 600_000L
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<MockHttpServletResponse>()

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

        // 3. 사용자 포인트 내역 조회
        mockMvc
            .perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$.length()").value(1))
    }

    /*
     * 사용자를 생성하고,
     * 해당 사용자의 포인트를 적절히 충전한 이후,
     * 해당 사용자의 포인트가 0보다 적어지도록 동시 사용 요청을 할 경우,
     * 예외가 발생하는지 테스트하며,
     * 테스트 이후 정상적인 포인트 내역만 존재하는지 확인
     */
    @Test
    fun `사용자_포인트_부족에_대한_동시성_테스트`() {
        val userId = 3L

        // 1. getOrDefault 전략에 따라 새로운 사용자 생성
        mockMvc
            .perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0L))

        // 2. 포인트 충전 요청
        val chargeAmount = 50_000L

        mockMvc
            .perform(patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeAmount.toString())
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(chargeAmount))

        // 3. 포인트 사용 동시 요청
        val useAmount = 30_000L
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<MockHttpServletResponse>()

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

        val successCount = results.count { it.status == 200 }
        val failCount = results.count {
            it.status == 500 &&
                String(it.contentAsByteArray, Charsets.UTF_8)
                    .contains("보유 포인트가 부족합니다.")
        }

        assertThat(successCount).isEqualTo(1)
        assertThat(failCount).isEqualTo(1)

        // 3. 사용자 포인트 내역 조회
        mockMvc
            .perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$[1].type").value("USE"))
            .andExpect(jsonPath("$.length()").value(2))
    }
}
