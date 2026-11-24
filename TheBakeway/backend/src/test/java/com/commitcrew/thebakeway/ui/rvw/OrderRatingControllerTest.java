package com.commitcrew.thebakeway.ui.rvw;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.rvw.OrderRatingService;
import com.commitcrew.thebakeway.application.rvw.OrderRatingView;

class OrderRatingControllerTest {

    private static final Cookie SESSION = new Cookie("BAKEWAY_SESSION_ID", "session-1");
    private static final OrderRatingView RATED = new OrderRatingView(
            "order-1", "bakery-1", 4, "courier-1", 5, Instant.parse("2026-11-18T20:00:00Z"));

    private final OrderRatingService ratingService = mock(OrderRatingService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrderRatingController(ratingService)).build();
    }

    @Test
    void ratesTheBakeryAndTheCourierInOneSubmission() throws Exception {
        when(ratingService.rateOrder("session-1", "order-1", 4, 5)).thenReturn(RATED);

        mvc.perform(post("/api/orders/order-1/rating").cookie(SESSION)
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4,\"courierScore\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bakeryScore").value(4))
                .andExpect(jsonPath("$.courierScore").value(5));
    }

    @Test
    void aMissingScoreReachesTheServiceAsNull() throws Exception {
        when(ratingService.rateOrder("session-1", "order-1", 4, null))
                .thenThrow(new IllegalArgumentException("The courier rating must be from 1 to 5"));

        mvc.perform(post("/api/orders/order-1/rating").cookie(SESSION)
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The courier rating must be from 1 to 5"));
    }

    @Test
    void aSecondRatingIsAConflict() throws Exception {
        when(ratingService.rateOrder("session-1", "order-1", 4, 5))
                .thenThrow(new IllegalStateException("This order has already been rated"));

        mvc.perform(post("/api/orders/order-1/rating").cookie(SESSION)
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4,\"courierScore\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This order has already been rated"));
    }

    @Test
    void anotherCustomersOrderIsNotFound() throws Exception {
        when(ratingService.rateOrder("session-1", "order-1", 4, 5)).thenThrow(new OrderNotFoundException());

        mvc.perform(post("/api/orders/order-1/rating").cookie(SESSION)
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4,\"courierScore\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRoleOtherThanCustomerIsForbiddenAndNoSessionIsUnauthorized() throws Exception {
        when(ratingService.rateOrder("session-1", "order-1", 4, 5))
                .thenThrow(new SecurityException("Only customers can rate their orders"));
        when(ratingService.rateOrder(null, "order-1", 4, 5))
                .thenThrow(new IllegalStateException("Authentication required"));

        mvc.perform(post("/api/orders/order-1/rating").cookie(SESSION)
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4,\"courierScore\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/order-1/rating")
                        .contentType(APPLICATION_JSON).content("{\"bakeryScore\":4,\"courierScore\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsTheCustomersRatings() throws Exception {
        when(ratingService.getMyRatings("session-1")).thenReturn(List.of(RATED));

        mvc.perform(get("/api/order-ratings/mine").cookie(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("order-1"))
                .andExpect(jsonPath("$[0].bakeryScore").value(4));
    }

    @Test
    void returnsBakeryAverageRating() throws Exception {
        when(ratingService.getBakeryAvgRating("session-1", "bakery-1"))
                .thenReturn(4.7);

        mvc.perform(get("/api/order-ratings/bakery-1/average-rating").cookie(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(4.7));
    }

    @Test
    void returnsZeroWhenBakeryHasNoRatings() throws Exception {
        when(ratingService.getBakeryAvgRating("session-1", "bakery-1"))
                .thenReturn(0.0);

        mvc.perform(get("/api/order-ratings/bakery-1/average-rating").cookie(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0.0));
    }
}
