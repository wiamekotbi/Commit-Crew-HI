package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;

@ExtendWith(MockitoExtension.class)
class AccountSeedingServiceTest {

    @Mock
    private AccountRegistry accountRegistry;

    @Mock
    private SessionManager sessionManager;

    private AccountSeedingService accountSeedingService;

    private static final String VALID_ACCOUNTS = String.join("\n",
            "1,customer,password1,Customer,customer@example.com,Montreal,CUSTOMER,",
            "2,baker,password2,Baker,baker@example.com,,RESTAURANT,B01",
            "3,courier,password3,Courier,courier@example.com,,COURIER,",
            "4,operator,password4,Operator,operator@example.com,,OPERATOR,");

    @BeforeEach
    void setUp() {
        // Simple test-only encoder: no mocking or real password hashing needed.
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence password) {
                return "encoded-" + password;
            }

            @Override
            public boolean matches(CharSequence password, String encodedPassword) {
                return encode(password).equals(encodedPassword);
            }
        };

        accountSeedingService = new AccountSeedingService(
                accountRegistry, passwordEncoder, sessionManager);
    }

    private void seed(String accountData) {
        accountSeedingService.seedAccounts(new Scanner(accountData));
    }

    private List<BakewayAccount> getSavedAccounts(int expectedCount) {
        ArgumentCaptor<BakewayAccount> accounts =
                ArgumentCaptor.forClass(BakewayAccount.class);
        verify(accountRegistry, times(expectedCount)).save(accounts.capture());
        return accounts.getAllValues();
    }

    @Test
    void loadsValidAccounts() {
        seed(VALID_ACCOUNTS);

        List<BakewayAccount> accounts = getSavedAccounts(4);
        BakewayAccount customer = accounts.get(0);

        assertEquals("1", customer.getAccountId());
        assertEquals("customer", customer.getUsername());
        assertEquals("encoded-password1", customer.getPassword());
        assertEquals("Customer", customer.getDisplayName());
        assertEquals("Montreal", customer.getDeliveryAddress());
        assertEquals(Role.CUSTOMER, customer.getRole());
    }

    @Test
    void loadsAllFourRoles() {
        seed(VALID_ACCOUNTS);

        List<BakewayAccount> accounts = getSavedAccounts(4);
        boolean customerFound = false;
        boolean restaurantFound = false;
        boolean courierFound = false;
        boolean operatorFound = false;

        for (BakewayAccount account : accounts) {
            if (account.getRole() == Role.CUSTOMER) customerFound = true;
            if (account.getRole() == Role.RESTAURANT) restaurantFound = true;
            if (account.getRole() == Role.COURIER) courierFound = true;
            if (account.getRole() == Role.OPERATOR) operatorFound = true;
        }

        assertTrue(customerFound);
        assertTrue(restaurantFound);
        assertTrue(courierFound);
        assertTrue(operatorFound);
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        String invalid = String.join("\n",
                ",missingId,password,Missing,one@example.com,Montreal,CUSTOMER,",
                "5,,password,Missing,two@example.com,Montreal,CUSTOMER,",
                "6,missingPassword,,Missing,three@example.com,Montreal,CUSTOMER,",
                "7,missingName,password,,four@example.com,Montreal,CUSTOMER,");

        seed(VALID_ACCOUNTS + "\n" + invalid);
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void rejectsInvalidRole() {
        seed(VALID_ACCOUNTS +
                "\n5,invalid,password,Invalid,invalid@example.com,,MANAGER,");
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void rejectsRestaurantWithoutBakeryId() {
        seed(VALID_ACCOUNTS +
                "\n5,restaurant2,password,Restaurant,restaurant2@example.com,,RESTAURANT,");
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void rejectsInvalidRoleSpecificAttributes() {
        String invalid = String.join("\n",
                "5,wrongBakery,password,Wrong,one@example.com,,COURIER,B02",
                "6,wrongAddress,password,Wrong,two@example.com,Montreal,OPERATOR,",
                "7,missingAddress,password,Wrong,three@example.com,,CUSTOMER,");

        seed(VALID_ACCOUNTS + "\n" + invalid);
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void rejectsDuplicateAccountId() {
        seed(VALID_ACCOUNTS +
                "\n1,duplicate,password,Duplicate,duplicate@example.com,,COURIER,");

        List<BakewayAccount> accounts = getSavedAccounts(4);
        int occurrences = 0;
        for (BakewayAccount account : accounts) {
            if (account.getAccountId().equals("1")) {
                occurrences++;
            }
        }
        assertEquals(1, occurrences);
    }

    @Test
    void rejectsMalformedEntries() {
        String invalid = String.join("\n",
                "",
                "5,tooFew,password,TooFew,email@example.com,COURIER",
                "6,tooMany,password,TooMany,email@example.com,,COURIER,,extra");

        seed(VALID_ACCOUNTS + "\n" + invalid);
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void doesNotReplaceAccountsWhenRoleMissing() {
        String missingOperator = String.join("\n",
                "1,customer,password1,Customer,customer@example.com,Montreal,CUSTOMER,",
                "2,baker,password2,Baker,baker@example.com,,RESTAURANT,B01",
                "3,courier,password3,Courier,courier@example.com,,COURIER,");

        seed(missingOperator);
        verifyNoInteractions(accountRegistry, sessionManager);
    }

    @Test
    void replacesAccountsWhenAllRolesPresent() {
        seed(VALID_ACCOUNTS);

        verify(accountRegistry).deleteAll();
        verify(sessionManager).invalidateAllSessions();
        assertEquals(4, getSavedAccounts(4).size());
    }

    @Test
    void createsSessionsForSeededAccounts() {
        String accountData =
                "1,customer,password,Customer,email1,Montreal,CUSTOMER,\n"
            + "2,restaurant,password,Restaurant,email2,,RESTAURANT,B01\n"
            + "3,courier,password,Courier,email3,,COURIER,\n"
            + "4,operator,password,Operator,email4,,OPERATOR,";

        Scanner scanner = new Scanner(accountData);

        accountSeedingService.seedAccounts(scanner);

        verify(sessionManager).createSession("1", Role.CUSTOMER);
        verify(sessionManager).createSession("2", Role.RESTAURANT);
        verify(sessionManager).createSession("3", Role.COURIER);
        verify(sessionManager).createSession("4", Role.OPERATOR);
    }
}
