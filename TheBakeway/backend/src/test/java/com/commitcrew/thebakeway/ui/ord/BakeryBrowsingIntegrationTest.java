package com.commitcrew.thebakeway.ui.ord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.BakeryService;
import com.commitcrew.thebakeway.application.ord.MenuService;
import com.commitcrew.thebakeway.application.ord.OrderServiceImpl;
import com.commitcrew.thebakeway.application.ord.OrderServiceProxy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.session.Session;

class BakeryBrowsingIntegrationTest {

    @Test
    void customerCanListBakeriesThenBrowseMenuThroughRealProtectedServices() throws Exception {
        // Only persistence is mocked. All application components below are real,
        // so this tests their collaboration rather than separate service stubs.
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        MenuItemRegistry menuItemRegistry = mock(MenuItemRegistry.class);
        BakeryService bakeryService = new BakeryService(bakeryRegistry);
        MenuService menuService = new MenuService(menuItemRegistry, bakeryService);
        SessionManager sessionManager = new SessionManager();
        OrderServiceImpl implementation = new OrderServiceImpl(bakeryService, menuService);
        OrderServiceProxy proxy = new OrderServiceProxy(
                implementation, sessionManager, new PermissionPolicy());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BakeryController(proxy)).build();

        // Create an actual in-memory customer session; login/password verification
        // and Spring's automatic bean wiring are outside this focused check.
        Session session = sessionManager.createSession("customer-1", Role.CUSTOMER);
        Cookie cookie = new Cookie("BAKEWAY_SESSION_ID", session.getSessionId());
        Bakery bakery = new Bakery("bakery-1", "Sweet Treats", BakeryStatus.open, "123 Main St");
        MenuItem available = new MenuItem("bakery-1", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);
        MenuItem soldOut = new MenuItem("bakery-1", "Baguette", "French baguette", 4.00,
                MenuItemStatus.sold_out);
        // Simulate persisted item IDs without changing the production entity API.
        ReflectionTestUtils.setField(available, "id", "item-1");
        ReflectionTestUtils.setField(soldOut, "id", "item-2");
        when(bakeryRegistry.findByBakeryStatus(BakeryStatus.open)).thenReturn(List.of(bakery));
        when(bakeryRegistry.findById("bakery-1")).thenReturn(Optional.of(bakery));
        when(menuItemRegistry.findByBakeryId("bakery-1")).thenReturn(List.of(available, soldOut));

        // Browse the list, then select that bakery using the same session cookie.
        mvc.perform(get("/api/bakeries").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bakeryId").value("bakery-1"))
                .andExpect(jsonPath("$[0].bakeryStatus").value("open"))
                .andExpect(jsonPath("$[0].bakeryAddress").value("123 Main St"));

        mvc.perform(get("/api/bakeries/bakery-1/menu").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("item-1"))
                .andExpect(jsonPath("$[0].bakeryId").value("bakery-1"))
                .andExpect(jsonPath("$[0].status").value("available"))
                .andExpect(jsonPath("$[1].id").value("item-2"))
                .andExpect(jsonPath("$[1].bakeryId").value("bakery-1"))
                .andExpect(jsonPath("$[1].status").value("sold_out"));

        // Confirm the real service chain reached the intended repository operations.
        verify(bakeryRegistry).findByBakeryStatus(BakeryStatus.open);
        verify(bakeryRegistry).findById("bakery-1");
        verify(menuItemRegistry).findByBakeryId("bakery-1");
    }
}
