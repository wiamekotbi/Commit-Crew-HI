(() => {
    const D = Dashboard;
    const target = document.getElementById("orderSummary");
    const retry = document.getElementById("retrySummary");
    const orderId = new URLSearchParams(window.location.search).get("orderId");

    function showError(message) {
        target.innerHTML = `<p class="empty" role="alert">${D.escape(message)}</p>`;
    }

    function render(summary) {
        const pricing = summary.pricing;
        const refunds = summary.refunds || [];
        const refundSection = refunds.length > 0 ? `
            <h3>Refund</h3>
            <p class="muted">Refunds are shown separately. The original charge above is unchanged.</p>
            <ul>${refunds.map(refund => `<li>
                ${D.escape(refund.refundId)}: ${D.money(refund.amount)}
                ${refund.refundedAt ? `on ${D.escape(D.time(refund.refundedAt))}` : ""}
                ${refund.reason ? `— ${D.escape(refund.reason)}` : ""}
            </li>`).join("")}</ul>` : summary.status === "refunded"
                ? "<h3>Refund</h3><p>Refund details are not available yet. The original charge is shown above.</p>" : "";

        target.innerHTML = `
            <p><strong>Order ID:</strong> ${D.escape(summary.orderId)}</p>
            <p><strong>Status:</strong> ${D.escape(summary.status)}</p>
            <p><strong>Delivered:</strong> ${D.escape(D.time(summary.deliveredAt))}</p>
            <div class="table-wrap">
                <table>
                    <thead><tr><th>Item</th><th>Quantity</th><th>Unit price</th><th>Subtotal</th></tr></thead>
                    <tbody>${summary.items.map(item => `<tr>
                        <td>${D.escape(item.itemName)}</td>
                        <td>${D.escape(item.quantity)}</td>
                        <td>${D.money(item.unitPrice)}</td>
                        <td>${D.money(item.subtotal)}</td>
                    </tr>`).join("")}</tbody>
                </table>
            </div>
            <h3>Original charge</h3>
            <div class="table-wrap">
                <table aria-label="Pricing breakdown">
                    <tbody>
                        <tr><th scope="row">Subtotal</th><td>${D.money(pricing.subtotal)}</td></tr>
                        <tr><th scope="row">Delivery fee</th><td>${D.money(pricing.deliveryFee)}</td></tr>
                        <tr><th scope="row">Service fee</th><td>${D.money(pricing.serviceFee)}</td></tr>
                        ${pricing.smallOrderSurcharge > 0 ? `<tr><th scope="row">Small-order surcharge</th>
                            <td>${D.money(pricing.smallOrderSurcharge)}</td></tr>` : ""}
                        <tr><th scope="row">Final total</th><td><strong>${D.money(pricing.total)}</strong></td></tr>
                    </tbody>
                </table>
            </div>
            ${refundSection}`;
    }

    async function loadSummary() {
        retry.disabled = true;
        target.setAttribute("aria-busy", "true");
        target.innerHTML = '<p class="muted">Loading order summary...</p>';
        try {
            const session = await D.api("GET", "/api/session");
            if (!session.ok) {
                if (session.status === 401) {
                    window.location.href = "/login.html";
                }
                showError(D.errorText(session));
                return;
            }
            if (session.data.role !== "CUSTOMER") {
                showError("Only customers can view order summaries.");
                return;
            }
            document.getElementById("userName").innerHTML = `<i class="fa-solid fa-circle-user"></i> ${D.escape(
                session.data.displayName || session.data.username || session.data.accountId)}`;
            document.getElementById("userRole").innerHTML = `<i class="fa-solid fa-user"></i> ${D.escape(session.data.role)}`;
            if (!orderId || !orderId.trim()) {
                showError("No order selected. Open a delivered order from My orders.");
                return;
            }
            const result = await D.api("GET", "/api/orders/" + encodeURIComponent(orderId) + "/summary");
            if (!result.ok) {
                showError(D.errorText(result));
                return;
            }
            render(result.data);
        } catch (error) {
            showError("Unable to load the order summary. Please try again.");
        } finally {
            target.setAttribute("aria-busy", "false");
            retry.disabled = false;
        }
    }

    retry.addEventListener("click", loadSummary);
    document.getElementById("logoutButton").addEventListener("click", async () => {
        try {
            const result = await D.api("POST", "/api/logout");
            if (result.ok) {
                window.location.href = "/login.html";
            } else {
                showError(D.errorText(result));
            }
        } catch (error) {
            showError("Unable to log out. Please try again.");
        }
    });
    loadSummary();
})();
