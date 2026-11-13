// Dashboard core: session, header, messages, output console, and one panel per role.
// Every command goes to the backend, which checks the session and the role (R-REG-02).
const Dashboard = (() => {

    const ROLE_ICONS = {
        CUSTOMER: "fa-user",
        RESTAURANT: "fa-store",
        COURIER: "fa-person-biking",
        OPERATOR: "fa-user-shield"
    };

    const STATUS_ICONS = {
        placed: "fa-receipt",
        accepted: "fa-list-ol",
        preparing: "fa-fire-burner",
        ready: "fa-bell-concierge",
        picked_up: "fa-person-biking",
        delivered: "fa-house-circle-check",
        refunded: "fa-money-bill-transfer",
        rejected: "fa-ban",
        cancelled: "fa-xmark",
        open: "fa-door-open",
        paused: "fa-pause",
        closed: "fa-lock",
        available: "fa-check",
        sold_out: "fa-ban"
    };

    const panels = {};
    let session = null;
    let lastConsoleSequence = 0;
    let mapRefreshInProgress = false;
    let mapRefreshTimer = null;
    let mapReady = false;

    // Calls the API; returns { ok, status, data }. A lost session sends the user back to login.
    async function api(method, url, body) {
        const options = { method, credentials: "same-origin", headers: {} };
        if (body !== undefined) {
            options.headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }

        const response = await fetch(url, options);
        let data = null;
        const text = await response.text();
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = { error: text };
            }
        }

        if (response.status === 401 && url !== "/api/session") {
            window.location.href = "/login.html";
        }
        return { ok: response.ok, status: response.status, data };
    }

    // Runs a command, shows the server's answer, then refreshes the panel and the console
    async function command(method, url, body, successMessage) {
        const result = await api(method, url, body);
        // Refresh first, so the message and the lists always show the same state
        await refresh();
        if (result.ok) {
            showMessage(typeof successMessage === "function" ? successMessage(result.data) : successMessage, "success");
        } else {
            showMessage(errorText(result), "error");
        }
        return result;
    }

    function errorText(result) {
        return (result.data && result.data.error) || ("Request refused (HTTP " + result.status + ")");
    }

    function showMessage(text, type) {
        const banner = document.getElementById("banner");
        banner.innerHTML = "";
        const icon = document.createElement("i");
        icon.className = "fa-solid " + (type === "success" ? "fa-circle-check" : "fa-triangle-exclamation");
        banner.append(icon, " ", text);
        banner.className = "banner " + type;
        banner.hidden = false;
    }

    // Escapes text before it goes into HTML
    function escape(value) {
        return String(value ?? "").replace(/[&<>"']/g, character => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
        })[character]);
    }

    function badge(status) {
        const icon = STATUS_ICONS[status] || "fa-circle";
        return `<span class="badge ${escape(status)}"><i class="fa-solid ${icon}"></i> ${escape(status)}</span>`;
    }

    function money(amount) {
        return "$" + Number(amount).toFixed(2);
    }

    function time(value) {
        return value ? new Date(value).toLocaleString() : "";
    }

    function shortId(id) {
        return id ? String(id).substring(0, 8) : "";
    }

    function itemsSummary(order) {
        return order.lines.map(line => `${line.quantity} x ${escape(line.itemName)}`).join(", ");
    }

    // Lines and state history (event ids) of one order (UC-ORD-12, UC-ORD-13)
    async function orderDetails(orderId) {
        const [result, pricingResult] = await Promise.all([
            api("GET", "/api/orders/" + encodeURIComponent(orderId)),
            api("GET", "/api/orders/" + encodeURIComponent(orderId) + "/pricing")
        ]);
        if (!result.ok) {
            return `<p class="empty">${escape(errorText(result))}</p>`;
        }
        const order = result.data;
        const lines = order.lines.map(line =>
            `<li>${line.quantity} x ${escape(line.itemName)} at ${money(line.unitPrice)} = ${money(line.subtotal)}</li>`
        ).join("");
        const history = order.history.map(transition =>
            `<li>${escape(time(transition.timestamp))}: ${escape(transition.previousState || "none")}
             <i class="fa-solid fa-arrow-right"></i> ${escape(transition.newState)}
             <span class="muted">(event ${escape(shortId(transition.eventId))})</span></li>`
        ).join("");
        const reason = order.rejectionReason ? `<p><strong>Rejection reason:</strong> ${escape(order.rejectionReason)}</p>` : "";
        const pricing = pricingResult.ok
            ? `<h4>${pricingResult.data.finalFare ? "Final charge" : "Estimated charges"}</h4>
                <table><tbody>
                    <tr><th scope="row">Items subtotal</th><td>${money(pricingResult.data.pricing.subtotal)}</td></tr>
                    <tr><th scope="row">Delivery fee</th><td>${money(pricingResult.data.pricing.deliveryFee)}</td></tr>
                    <tr><th scope="row">Service fee</th><td>${money(pricingResult.data.pricing.serviceFee)}</td></tr>
                    ${pricingResult.data.pricing.smallOrderSurcharge > 0
                        ? `<tr><th scope="row">Small-order surcharge</th>
                            <td>${money(pricingResult.data.pricing.smallOrderSurcharge)}</td></tr>` : ""}
                    <tr><th scope="row">Final total</th><td><strong>${money(pricingResult.data.pricing.total)}</strong></td></tr>
                </tbody></table>
                ${pricingResult.data.finalFare ? "" : '<p class="muted">Estimated using current fees; final fees are recorded when delivered.</p>'}`
            : `<p class="empty">${escape(errorText(pricingResult))}</p>`;
        return `
            <strong>Order ${escape(shortId(order.orderId))}</strong> for ${escape(order.recipientName)},
            ${escape(order.deliveryAddress)}
            ${reason}
            <ul class="history">${lines}</ul>
            ${pricing}
            <strong><i class="fa-solid fa-clock-rotate-left"></i> History</strong>
            <ul class="history">${history}</ul>`;
    }

    // Shows or hides an order's details in a row under it
    async function toggleDetails(button, orderId, columns) {
        const row = button.closest("tr");
        const next = row.nextElementSibling;
        if (next && next.classList.contains("details-row")) {
            next.remove();
            return;
        }
        const details = document.createElement("tr");
        details.className = "details-row";
        details.innerHTML = `<td colspan="${columns}">${await orderDetails(orderId)}</td>`;
        row.after(details);
    }

    async function loadConsole() {
        const result = await api("GET", "/api/console?after=" + lastConsoleSequence);
        if (!result.ok || !Array.isArray(result.data)) {
            return;
        }

        const list = document.getElementById("console");
        for (const entry of result.data) {
            const item = document.createElement("li");
            const when = new Date(entry.time).toLocaleTimeString();
            item.innerHTML = `<span class="time">${escape(when)}</span> `
                + `<span class="${entry.success ? "ok" : "err"}">${entry.success ? "OK   " : "ERROR"}</span> `
                + `${escape(entry.user)} (${escape(entry.role)}) <strong>${escape(entry.command)}</strong>`
                + (entry.detail ? ` - ${escape(entry.detail)}` : "");
            list.prepend(item);
            lastConsoleSequence = Math.max(lastConsoleSequence, entry.sequence);
        }
        while (list.children.length > 200) {
            list.lastElementChild.remove();
        }
    }

    async function loadMapBakeries() {
        const result = await api("GET", "/api/bakeries/map");

        if (!result.ok || !Array.isArray(result.data)) {
            console.error("Failed to load map bakeries");
            return;
        }

        const bakeries = result.data;

        BakewayMap.setBakeries(bakeries);

        await Promise.all(bakeries.map(async bakery => {
            try {
                const queueResult = await api(
                    "GET",
                    "/api/bakeries/" +
                        encodeURIComponent(bakery.bakeryId) +
                        "/preparation-queue-size"
                );

                if (queueResult.ok) {
                    BakewayMap.updateBakeryQueue(
                        bakery.bakeryId,
                        queueResult.data
                    );
                }
            } catch (error) {
                console.error(
                    "Failed to load queue for bakery",
                    bakery.bakeryId,
                    error
                );
            }
        }));
    }

    async function loadMapCouriers() {
        const result = await api("GET", "/api/couriers/map");

        if (!result.ok || !Array.isArray(result.data)) {
            console.error("Failed to load map couriers");
            return;
        }

        BakewayMap.setCouriers(result.data);
    }

    async function refreshMap() {
        if (!mapReady || mapRefreshInProgress) {
            return;
        }

        mapRefreshInProgress = true;

        try {
            await Promise.allSettled([
                loadMapBakeries(),
                loadMapCouriers()
            ]);
            BakewayMap.fitToData();
        } finally {
            mapRefreshInProgress = false;
        }
    }


    async function refresh() {
        const panel = panels[session.role];
        if (panel && panel.refresh) {
            await panel.refresh();
        }
        await loadConsole();
    }

    function renderHeader() {
        const name = document.getElementById("userName");
        name.innerHTML = `<i class="fa-solid fa-circle-user"></i> ${escape(session.displayName || session.username || session.accountId)}`;
        const role = document.getElementById("userRole");
        role.innerHTML = `<i class="fa-solid ${ROLE_ICONS[session.role] || "fa-user"}"></i> ${escape(session.role)}`;

        document.getElementById("logoutButton").addEventListener("click", async () => {
            await api("POST", "/api/logout");
            window.location.href = "/login.html";
        });
    }

    async function start() {
        const result = await api("GET", "/api/session");
        if (!result.ok) {
            window.location.href = "/login.html";
            return;
        }
        session = result.data;
        renderHeader();

        const container = document.getElementById("panel");
        const panel = panels[session.role];
        if (!panel) {
            container.innerHTML = `<p class="empty">No dashboard for role ${escape(session.role)}.</p>`;
            return;
        }
        await panel.render(container, session);
        // Initialize the shared map.
        // Wait for the Google Maps API before proceeding.
        try {
            await BakewayMap.init("map");
            mapReady = true;

            // Load initial bakery and courier markers.
            await refreshMap();

            // Refresh map data every 50 seconds.
            mapRefreshTimer = setInterval(() => {void refreshMap();}, 5000);
        } catch (error) {console.error("Unable to initialize map:", error);}
        await loadConsole();
        setInterval(loadConsole, 3000);
    }

    return {
        api, command, showMessage, errorText, escape, badge, money, time, shortId, itemsSummary,
        toggleDetails, register: (role, panel) => { panels[role] = panel; }, start,
        get session() { return session; }
    };
})();
