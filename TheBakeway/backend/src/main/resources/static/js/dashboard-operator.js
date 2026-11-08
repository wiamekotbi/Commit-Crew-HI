// Platform operator panel: every bakery (pause / reopen), every order (filter, details, cancel),
// and restore the initial state (UC-ORD-04, UC-ORD-11, UC-ORD-13, UC-REG-03, UC-ORD-01)
Dashboard.register("OPERATOR", (() => {
    const D = Dashboard;

    const STATES = ["placed", "accepted", "preparing", "ready", "picked_up", "delivered", "rejected", "cancelled"];
    const CANCELLABLE = ["placed", "accepted", "preparing", "ready"];

    let container = null;
    let bakeries = [];

    function bakeryName(bakeryId) {
        const bakery = bakeries.find(b => b.bakeryId === bakeryId);
        return bakery ? bakery.bakeryName : bakeryId;
    }

    async function render(target) {
        container = target;
        container.innerHTML = `
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-store"></i> Bakeries</h2>
                    <button type="button" class="btn danger" id="restore">
                        <i class="fa-solid fa-clock-rotate-left"></i> Restore initial state</button>
                </div>
                <div id="bakeries" class="table-wrap"></div>
            </section>
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-truck-fast"></i> Waiting dispatch orders</h2>
                    <span class="muted">Orders without a courier or left by an offline courier</span>
                </div>
                <div id="waitingOrders" class="table-wrap"></div>
            </section>
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-receipt"></i> All orders</h2>
                    <div class="inline-form">
                        <select id="bakeryFilter"><option value="">All bakeries</option></select>
                        <select id="statusFilter"><option value="">All states</option>
                            ${STATES.map(s => `<option value="${s}">${s}</option>`).join("")}</select>
                    </div>
                </div>
                <div id="orders" class="table-wrap"></div>
            </section>`;

        container.querySelector("#bakeryFilter").addEventListener("change", loadOrders);
        container.querySelector("#statusFilter").addEventListener("change", loadOrders);
        container.querySelector("#restore").addEventListener("click", restore);
        await refresh();
    }

    async function refresh() {
        await loadBakeries();
        await loadWaitingOrders();
        await loadOrders();
    }

    async function loadBakeries() {
        const result = await D.api("GET", "/api/bakeries/all");
        const target = container.querySelector("#bakeries");
        if (!result.ok) {
            target.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        bakeries = result.data;

        const filter = container.querySelector("#bakeryFilter");
        const selected = filter.value;
        filter.innerHTML = `<option value="">All bakeries</option>` + bakeries.map(b =>
            `<option value="${D.escape(b.bakeryId)}">${D.escape(b.bakeryName)}</option>`).join("");
        filter.value = selected;

        target.innerHTML = `<table>
            <thead><tr><th>Bakery</th><th>Address</th><th>Status</th><th></th></tr></thead>
            <tbody>${bakeries.map(bakery => `
                <tr>
                    <td><strong>${D.escape(bakery.bakeryName)}</strong></td>
                    <td>${D.escape(bakery.bakeryAddress)}</td>
                    <td>${D.badge(bakery.bakeryStatus)}</td>
                    <td class="actions">
                        <button type="button" class="btn secondary" data-pause="${D.escape(bakery.bakeryId)}">
                            <i class="fa-solid fa-pause"></i> Pause</button>
                        <button type="button" class="btn secondary" data-reopen="${D.escape(bakery.bakeryId)}">
                            <i class="fa-solid fa-play"></i> Reopen</button>
                    </td>
                </tr>`).join("")}</tbody></table>`;

        // Both buttons stay available: the backend answers "no change" or refuses a closed bakery
        target.querySelectorAll("button[data-pause]").forEach(button =>
            button.addEventListener("click", () => bakeryCommand(button.dataset.pause, "pause")));
        target.querySelectorAll("button[data-reopen]").forEach(button =>
            button.addEventListener("click", () => bakeryCommand(button.dataset.reopen, "reopen")));
    }

    function bakeryCommand(bakeryId, action) {
        return D.command("PATCH", "/api/bakeries/" + encodeURIComponent(bakeryId) + "/" + action, undefined,
            bakery => `${bakery.bakeryName} is now ${bakery.bakeryStatus}.`);
    }

    async function loadWaitingOrders() {
        const result = await D.api("GET", "/api/dispatch/waiting-orders");
        const target = container.querySelector("#waitingOrders");
        if (!result.ok) {
            // Keep dispatch failures inside this card so the operator can
            // continue using unrelated bakery and order controls.
            target.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        if (result.data.length === 0) {
            target.innerHTML = `<p class="empty">No orders are waiting for dispatch.</p>`;
            return;
        }

        target.innerHTML = `<table>
            <thead><tr><th>Order</th><th>Bakery</th><th>Status</th><th>Queue</th>
                <th>Previous courier</th><th></th></tr></thead>
            <tbody>${result.data.map(order => `
                <tr>
                    <td><strong>${D.escape(D.shortId(order.orderId))}</strong></td>
                    <td>${D.escape(bakeryName(order.bakeryId))}</td>
                    <td>${D.badge(order.status)}</td>
                    <td>${order.queuePosition == null
                        ? `<span class="muted">not queued</span>`
                        : `#${D.escape(order.queuePosition)}`}</td>
                    <td>${order.stalledCourierId
                        ? D.escape(D.shortId(order.stalledCourierId))
                        : `<span class="muted">none</span>`}</td>
                    <td class="actions">
                        ${order.reassignable
                            ? `<button type="button" class="btn" data-reassign="${D.escape(order.orderId)}">
                                <i class="fa-solid fa-rotate"></i> Reassign</button>`
                            : `<span class="muted">Waiting for an available courier</span>`}
                    </td>
                </tr>`).join("")}</tbody></table>`;

        // The backend rechecks operator permission and stalled eligibility;
        // displaying a button never acts as proof that reassignment is valid.
        target.querySelectorAll("button[data-reassign]").forEach(button =>
            button.addEventListener("click", () => reassignOrder(
                button.dataset.reassign)));
    }

    function reassignOrder(orderId) {
        return D.command(
            "PATCH",
            "/api/dispatch/waiting-orders/" + encodeURIComponent(orderId) + "/reassign",
            undefined,
            result => result.waiting
                ? `Order ${D.shortId(result.orderId)} remains queued because no replacement courier is available.`
                : `Order ${D.shortId(result.orderId)} was offered to courier ${D.shortId(result.offeredCourierId)}.`);
    }

    async function loadOrders() {
        const bakeryId = container.querySelector("#bakeryFilter").value;
        const status = container.querySelector("#statusFilter").value;
        const query = new URLSearchParams();
        if (bakeryId) query.set("bakeryId", bakeryId);
        if (status) query.set("status", status);

        const result = await D.api("GET", "/api/orders" + (query.toString() ? "?" + query.toString() : ""));
        const target = container.querySelector("#orders");
        if (!result.ok) {
            target.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        if (result.data.length === 0) {
            target.innerHTML = `<p class="empty">No order matches.</p>`;
            return;
        }
        target.innerHTML = `<table>
            <thead><tr><th>Placed</th><th>Order</th><th>Bakery</th><th>Customer</th><th>Items</th>
                <th>Total</th><th>Status</th><th></th></tr></thead>
            <tbody>${result.data.map(order => `
                <tr>
                    <td>${D.escape(D.time(order.createdAt))}</td>
                    <td>${D.escape(D.shortId(order.orderId))}</td>
                    <td>${D.escape(bakeryName(order.bakeryId))}</td>
                    <td>${D.escape(order.recipientName)}</td>
                    <td>${D.itemsSummary(order)}</td>
                    <td>${D.money(order.total)}</td>
                    <td>${D.badge(order.status)}
                        ${order.queuePosition ? `<br><span class="muted">queue #${D.escape(order.queuePosition)}</span>` : ""}</td>
                    <td class="actions">
                        <button type="button" class="btn secondary" data-details="${D.escape(order.orderId)}">
                            <i class="fa-solid fa-circle-info"></i></button>
                        ${CANCELLABLE.includes(order.status) ? `<button type="button" class="btn danger"
                            data-cancel="${D.escape(order.orderId)}"><i class="fa-solid fa-xmark"></i> Cancel</button>` : ""}
                    </td>
                </tr>`).join("")}</tbody></table>`;

        target.querySelectorAll("button[data-details]").forEach(button =>
            button.addEventListener("click", () => D.toggleDetails(button, button.dataset.details, 8)));
        target.querySelectorAll("button[data-cancel]").forEach(button =>
            button.addEventListener("click", () => D.command("PATCH",
                "/api/orders/" + encodeURIComponent(button.dataset.cancel) + "/cancel", undefined,
                data => `Order ${D.shortId(data.orderId)} cancelled (event ${D.shortId(data.eventId)}).`)));
    }

    // Reloads accounts and the city from the config files; every session ends, so the operator logs in again
    async function restore() {
        if (!window.confirm("Restore the initial state? All orders and events are deleted and everyone is logged out.")) {
            return;
        }
        const result = await D.api("POST", "/api/initial-state/restore");
        if (!result.ok) {
            D.showMessage(D.errorText(result), "error");
            return;
        }
        window.location.href = "/login.html";
    }

    return { render, refresh };
})());
