// Customer order-history panel. Kept separate from bakery browsing and cart
// management so later order-specific actions can evolve without growing the
// customer dashboard controller.
const CustomerOrderHistory = (() => {
    function create(D, target, bakeryName) {
        async function load() {
            const [ordersResult, ratingsResult] = await Promise.all([
                D.api("GET", "/api/orders/mine"),
                D.api("GET", "/api/courier-ratings/mine")
            ]);
            if (!ordersResult.ok) {
                target.innerHTML = `<p class="empty">${D.escape(D.errorText(ordersResult))}</p>`;
                return;
            }
            if (ordersResult.data.length === 0) {
                target.innerHTML = `<p class="empty">You have no orders yet.</p>`;
                return;
            }
            const ratingsByOrder = new Map(
                ratingsResult.ok
                    ? ratingsResult.data.map(rating => [rating.orderId, rating])
                    : []);
            target.innerHTML = `
                <table>
                    <thead><tr><th>Placed</th><th>Bakery</th><th>Items</th><th>Items subtotal</th><th>Status</th><th></th></tr></thead>
                    <tbody>${ordersResult.data.map(order => `
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
                                ${courierRatingAction(order, ratingsByOrder.get(order.orderId))}
                            </td>
                        </tr>`).join("")}</tbody>
                </table>`;
            target.querySelectorAll("button[data-details]").forEach(button =>
                button.addEventListener("click", () => D.toggleDetails(button, button.dataset.details, 6)));
            target.querySelectorAll("button[data-cancel]").forEach(button =>
                button.addEventListener("click", () => D.command("PATCH",
                    "/api/orders/" + encodeURIComponent(button.dataset.cancel) + "/cancel", undefined,
                    data => `Order ${D.shortId(data.orderId)} cancelled (event ${D.shortId(data.eventId)}).`)));
            target.querySelectorAll("button[data-rate-courier]").forEach(button =>
                button.addEventListener("click", () => rateCourier(button.dataset.rateCourier)));
        }

        function courierRatingAction(order, rating) {
            if (order.status !== "delivered") {
                return "";
            }
            if (rating) {
                return `<span class="muted">Courier rated ${D.escape(rating.score)}/5</span>`;
            }
            return `
                <label class="muted">Courier rating
                    <select data-courier-rating="${D.escape(order.orderId)}" aria-label="Courier rating">
                        ${[1, 2, 3, 4, 5].map(score =>
                            `<option value="${score}" ${score === 5 ? "selected" : ""}>${score}</option>`).join("")}
                    </select>
                </label>
                <button type="button" class="btn" data-rate-courier="${D.escape(order.orderId)}">
                    Rate courier</button>`;
        }

        function rateCourier(orderId) {
            const score = Number(target.querySelector(
                `select[data-courier-rating="${CSS.escape(orderId)}"]`).value);
            return D.command(
                "POST",
                `/api/orders/${encodeURIComponent(orderId)}/courier-rating`,
                { score },
                `Courier rated ${score}/5.`);
        }

        return { load, rateCourier };
    }

    return { create };
})();
