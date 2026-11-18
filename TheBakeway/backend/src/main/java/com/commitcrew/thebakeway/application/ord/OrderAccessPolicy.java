package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.session.Session;

// Who may see an order: the operator sees every order, a customer only their own,
// restaurant staff only their bakery's. Any other role sees none.
@Component
public class OrderAccessPolicy {

    private final BakeryStaffAccessService bakeryStaffAccessService;

    public OrderAccessPolicy(BakeryStaffAccessService bakeryStaffAccessService) {
        this.bakeryStaffAccessService = bakeryStaffAccessService;
    }

    public boolean canView(Session session, Order order) {
        Role role = session.getRole();
        if (role == Role.OPERATOR) {
            return true;
        }
        if (role == Role.CUSTOMER) {
            return session.getAccountId().equals(order.getCustomerId());
        }
        if (role == Role.RESTAURANT) {
            return order.getBakeryId().equals(
                    bakeryStaffAccessService.getBakeryIdForSession(session.getSessionId()));
        }
        return false;
    }

    // An order the caller may not see is reported as not found, so its existence is not revealed.
    public void requireCanView(Session session, Order order) {
        if (!canView(session, order)) {
            throw new OrderNotFoundException();
        }
    }
}
