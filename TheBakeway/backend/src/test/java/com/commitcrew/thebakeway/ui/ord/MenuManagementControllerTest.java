package com.commitcrew.thebakeway.ui.ord;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

@ExtendWith(MockitoExtension.class)
class MenuManagementControllerTest {

    private static final String SESSION_ID = "session-1";
    private static final String ITEM_ID = "item-1";
    private static final String ITEMS_URL = "/api/menu-items";

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Standalone MockMvc verifies routing, cookie binding, request validation,
        // JSON conversion, status codes, and local exception handling without a
        // database or a complete Spring application context.
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MenuManagementController(orderService))
                .build();
    }

    @Test
    void createsMenuItemAndForwardsAuthenticatedSession() throws Exception {
        MenuItem created = item(MenuItemStatus.available);
        when(orderService.addMenuItem(
                SESSION_ID, "Croissant", "Butter croissant", 3.50)).thenReturn(created);

        mockMvc.perform(post(ITEMS_URL)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Croissant",
                                  "description": "Butter croissant",
                                  "price": 3.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ITEM_ID))
                .andExpect(jsonPath("$.bakeryId").value("bakery-1"))
                .andExpect(jsonPath("$.status").value("available"));

        // There is intentionally no bakery ID in the JSON request. Ownership
        // will be resolved from this session inside the application layer.
        verify(orderService).addMenuItem(
                SESSION_ID, "Croissant", "Butter croissant", 3.50);
    }

    @Test
    void editsMenuItemDetails() throws Exception {
        MenuItem edited = item(MenuItemStatus.available);
        when(orderService.editMenuItem(
                SESSION_ID, ITEM_ID, "Updated", "New description", 4.25))
                .thenReturn(edited);

        mockMvc.perform(put(ITEMS_URL + "/" + ITEM_ID)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "description": "New description",
                                  "price": 4.25
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ITEM_ID));

        verify(orderService).editMenuItem(
                SESSION_ID, ITEM_ID, "Updated", "New description", 4.25);
    }

    @Test
    void changesMenuItemAvailability() throws Exception {
        MenuItem soldOut = item(MenuItemStatus.sold_out);
        when(orderService.updateMenuItemAvailability(
                SESSION_ID, ITEM_ID, MenuItemStatus.sold_out)).thenReturn(soldOut);

        mockMvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/availability")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "sold_out" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sold_out"));

        verify(orderService).updateMenuItemAvailability(
                SESSION_ID, ITEM_ID, MenuItemStatus.sold_out);
    }

    @Test
    void rejectsInvalidDetailsBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(post(ITEMS_URL)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "description": "Invalid item",
                                  "price": -1.00
                                }
                                """))
                .andExpect(status().isBadRequest());

        // Bean validation must stop invalid transport data at the HTTP boundary.
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsMissingAvailabilityBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/availability")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void mapsMissingSessionToUnauthorized() throws Exception {
        when(orderService.addMenuItem(null, "Croissant", "Butter croissant", 3.50))
                .thenThrow(new IllegalStateException("Authentication required"));

        mockMvc.perform(post(ITEMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Croissant",
                                  "description": "Butter croissant",
                                  "price": 3.50
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void mapsDeniedMenuManagementPermissionToForbidden() throws Exception {
        when(orderService.updateMenuItemAvailability(
                SESSION_ID, ITEM_ID, MenuItemStatus.sold_out))
                .thenThrow(new SecurityException(
                        "Access denied for operation: MANAGE_MENU_ITEMS"));

        mockMvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/availability")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "sold_out" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "Access denied for menu management"));
    }

    private Cookie sessionCookie() {
        return new Cookie("BAKEWAY_SESSION_ID", SESSION_ID);
    }

    private MenuItem item(MenuItemStatus status) {
        MenuItem item = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50, status);
        // Production IDs are generated by JPA. The controlled ID keeps this
        // controller test independent from persistence while verifying JSON.
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }
}
