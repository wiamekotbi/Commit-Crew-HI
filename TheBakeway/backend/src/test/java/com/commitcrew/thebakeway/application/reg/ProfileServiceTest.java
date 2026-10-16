package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private AccountRegistry accountRegistry;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getsProfile() {
        BakewayAccount account = new BakewayAccount(
                "1",
                "maria",
                "password",
                "Maria",
                "",
                "Montreal",
                Role.CUSTOMER);

        when(accountRegistry.findById("1")).thenReturn(Optional.of(account));

        BakewayAccount result = profileService.getProfile("1");

        assertEquals("Maria", result.getDisplayName());
        assertEquals("maria", result.getUsername());
        assertEquals(Role.CUSTOMER, result.getRole());
    }

    @Test
    void updatesProfile() {
        BakewayAccount account = new BakewayAccount(
                "1",
                "maria",
                "password",
                "Maria",
                "",
                "Old Address",
                Role.CUSTOMER);

        when(accountRegistry.findById("1")).thenReturn(Optional.of(account));
        when(accountRegistry.save(account)).thenReturn(account);

        BakewayAccount result = profileService.updateProfile(
                "1",
                "Maria Laghmari",
                "maria@email.com",
                "New Address");

        assertEquals("Maria Laghmari", result.getDisplayName());
        assertEquals("maria@email.com", result.getContactEmail());
        assertEquals("New Address", result.getDeliveryAddress());
        assertEquals("maria", result.getUsername());
        assertEquals(Role.CUSTOMER, result.getRole());
    }

    @Test
    void rejectsMissingAccount() {
        when(accountRegistry.findById("1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> profileService.getProfile("1"));
    }
}