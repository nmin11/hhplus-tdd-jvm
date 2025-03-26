package io.hhplus.tdd

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.point.TransactionType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
        pointHistoryTable.insert(userId, 5000, TransactionType.USE, System.currentTimeMillis())

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
}
