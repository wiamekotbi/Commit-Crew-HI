// Customer order-history panel. Kept separate from bakery browsing and cart
// management so later order-specific actions can evolve without growing the
// customer dashboard controller.
const CustomerOrderHistory = (() => {
    function create(D, target, bakeryName) {
        async function load() {
            const result = await D.api("GET", "/api/orders/mine");
            if (!result.ok) {
                target.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
                return;
            }
            if (result.data.length === 0) {
                target.innerHTML = `<p class="empty">You have no orders yet.</p>`;
                return;
            }
            target.innerHTML = `
                <table>
                    <thead><tr><th>Placed</th><th>Bakery</th><th>Items</th><th>Items subtotal</th><th>Status</th><th></th></tr></thead>
                    <tbody>${result.data.map(order => `
                        <tr>
                            <td>${D.escape(D.time(order.createdAt))}</td>
                            <td>${D.escape(bakeryName(order.bakeryId))}</td>
                            <td>${D.itemsSummary(order)}</td>
                            <td>${D.money(order.total)}</td>
                            <td>${D.badge(order.status)}
                                ${order.rejectionReason ? `<br><span class="muted">${D.escape(order.rejectionReason)}</span>` : ""}</td>
                            <td class="actions">
                                ${["delivered", "refunded"].includes(order.status) ? `<a class="btn secondary"
                                    href="/order-summary.html?orderId=${encodeURIComponent(order.orderId)}">Summary</a>` : ""}
                                <button type="button" class="btn secondary" data-details="${D.escape(order.orderId)}">
                                    <i class="fa-solid fa-circle-info"></i> Details</button>
                                ${order.status === "placed" ? `<button type="button" class="btn danger" data-cancel="${D.escape(order.orderId)}">
                                    <i class="fa-solid fa-xmark"></i> Cancel</button>` : ""}
                            </td>
                        </tr>`).join("")}</tbody>
                </table>`;
            target.querySelectorAll("button[data-details]").forEach(button =>
                button.addEventListener("click", () => D.toggleDetails(button, button.dataset.details, 6)));
            target.querySelectorAll("button[data-cancel]").forEach(button =>
                button.addEventListener("click", () => D.command("PATCH",
                    "/api/orders/" + encodeURIComponent(button.dataset.cancel) + "/cancel", undefined,
                    data => `Order ${D.shortId(data.orderId)} cancelled (event ${D.shortId(data.eventId)}).`)));
        }

        return { load };
    }

    return { create };
})();
