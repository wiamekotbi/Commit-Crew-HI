// Customer order-history panel. Kept separate from bakery browsing and cart
// management so later order-specific actions can evolve without growing the
// customer dashboard controller.
const CustomerOrderHistory = (() => {
    function create(D, target, bakeryName) {
        async function load() {
            const [ordersResult, ratingsResult] = await Promise.all([
                D.api("GET", "/api/orders/mine"),
                D.api("GET", "/api/order-ratings/mine")
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
                                ${ratingAction(order, ratingsByOrder.get(order.orderId))}
                            </td>
                        </tr>`).join("")}</tbody>
                </table>`;
            target.querySelectorAll("button[data-details]").forEach(button =>
                button.addEventListener("click", () => D.toggleDetails(button, button.dataset.details, 6)));
            target.querySelectorAll("button[data-cancel]").forEach(button =>
                button.addEventListener("click", () => D.command("PATCH",
                    "/api/orders/" + encodeURIComponent(button.dataset.cancel) + "/cancel", undefined,
                    data => `Order ${D.shortId(data.orderId)} cancelled (event ${D.shortId(data.eventId)}).`)));
            target.querySelectorAll("button[data-rate-order]").forEach(button =>
                button.addEventListener("click", () => rateOrder(button.dataset.rateOrder)));
        }

        // UC-RVW-01: a delivered order is rated once, for the bakery and the courier together
        function ratingAction(order, rating) {
            if (rating) {
                return `<span class="muted"><i class="fa-solid fa-star"></i>
                    Rated: bakery ${D.escape(rating.bakeryScore)}/5, courier ${D.escape(rating.courierScore)}/5</span>`;
            }
            if (order.status !== "delivered") {
                return "";
            }
            return `
                ${scoreSelect("bakery", order.orderId)}
                ${scoreSelect("courier", order.orderId)}
                <button type="button" class="btn" data-rate-order="${D.escape(order.orderId)}">
                    <i class="fa-solid fa-star"></i> Rate order</button>`;
        }

        function scoreSelect(rated, orderId) {
            const label = rated === "bakery" ? "Bakery" : "Courier";
            return `
                <label class="muted">${label}
                    <select data-${rated}-rating="${D.escape(orderId)}" aria-label="${label} rating">
                        ${[1, 2, 3, 4, 5].map(score =>
                            `<option value="${score}" ${score === 5 ? "selected" : ""}>${score}</option>`).join("")}
                    </select>
                </label>`;
        }

        function rateOrder(orderId) {
            const bakeryScore = selectedScore("bakery", orderId);
            const courierScore = selectedScore("courier", orderId);
            return D.command(
                "POST",
                `/api/orders/${encodeURIComponent(orderId)}/rating`,
                { bakeryScore, courierScore },
                `Order rated: bakery ${bakeryScore}/5, courier ${courierScore}/5.`);
        }

        function selectedScore(rated, orderId) {
            return Number(target.querySelector(`select[data-${rated}-rating="${CSS.escape(orderId)}"]`).value);
        }

        return { load, rateOrder };
    }

    return { create };
})();
