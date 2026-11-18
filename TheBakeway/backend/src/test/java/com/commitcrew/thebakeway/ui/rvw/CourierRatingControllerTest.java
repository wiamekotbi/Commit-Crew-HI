package com.commitcrew.thebakeway.ui.rvw;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.rvw.CourierRatingService;
import com.commitcrew.thebakeway.application.rvw.CourierRatingView;

@ExtendWith(MockitoExtension.class)
class CourierRatingControllerTest {

    @Mock
    private CourierRatingService ratingService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CourierRatingController(ratingService)).build();
    }

    @Test
    void createsCourierRating() throws Exception {
        when(ratingService.rateCourier("session-1", "order-1", 5))
                .thenReturn(new CourierRatingView(
                        "rating-1", "order-1", "courier-1", 5, Instant.parse("2026-11-18T20:00:00Z")));

        mvc.perform(post("/api/orders/order-1/courier-rating")
                .cookie(new Cookie("BAKEWAY_SESSION_ID", "session-1"))
                .contentType(APPLICATION_JSON)
                .content("{\"score\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.courierId").value("courier-1"))
                .andExpect(jsonPath("$.score").value(5));
    }

    @Test
    void returnsClearDuplicateMessage() throws Exception {
        when(ratingService.rateCourier("session-1", "order-1", 5))
                .thenThrow(new IllegalStateException(
                        "The courier for this order has already been rated"));

        mvc.perform(post("/api/orders/order-1/courier-rating")
                .cookie(new Cookie("BAKEWAY_SESSION_ID", "session-1"))
                .contentType(APPLICATION_JSON)
                .content("{\"score\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("The courier for this order has already been rated"));
    }

    @Test
    void returnsClearInvalidScoreMessage() throws Exception {
        when(ratingService.rateCourier("session-1", "order-1", 0))
                .thenThrow(new IllegalArgumentException(
                        "Courier rating must be between 1 and 5"));

        mvc.perform(post("/api/orders/order-1/courier-rating")
                .cookie(new Cookie("BAKEWAY_SESSION_ID", "session-1"))
                .contentType(APPLICATION_JSON)
                .content("{\"score\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Courier rating must be between 1 and 5"));
    }
}
