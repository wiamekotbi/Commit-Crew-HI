// Bakery staff panel: incoming orders, preparation queue, orders being prepared, menu items, pause
// (UC-ORD-05 to UC-ORD-11)
Dashboard.register("RESTAURANT", (() => {
    const D = Dashboard;

    let container = null;
    let menuItems = [];

    async function render(target, session) {
        container = target;
        container.innerHTML = `
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-store"></i> My bakery</h2>
                    <span class="muted">${D.escape(session.bakeryId)}</span>
                </div>
                <div class="actions">
                    <button type="button" class="btn secondary" id="pauseBakery">
                        <i class="fa-solid fa-pause"></i> Pause bakery</button>
                    <span class="muted">A paused bakery takes no new orders but finishes the ones it has.
                        Only the operator can reopen it.</span>
                </div>
            </section>
            <div class="grid-2">
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-inbox"></i> Incoming orders</h2>
                        <span class="muted">placed</span>
                    </div>
                    <div id="incoming" class="table-wrap"></div>
                </section>
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-list-ol"></i> Preparation queue</h2>
                        <span class="muted">arrival order, only the first can start</span>
                    </div>
                    <div id="queue" class="table-wrap"></div>
                </section>
            </div>
            <div class="grid-2">
                <section class="card">
                    <div class="card-title"><h2><i class="fa-solid fa-fire-burner"></i> Preparing</h2></div>
                    <div id="preparing" class="table-wrap"></div>
                </section>
                <section class="card">
                    <div class="card-title"><h2><i class="fa-solid fa-bell-concierge"></i> Ready and closed</h2></div>
                    <div id="done" class="table-wrap"></div>
                </section>
            </div>
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-book-open"></i> My menu</h2>
                    <span class="muted">sold-out items cannot be ordered</span>
                </div>
                <div id="menuItems" class="table-wrap"></div>
                <h3 class="subheading"><i class="fa-solid fa-plus"></i> Add a menu item</h3>
                <form id="addItem" class="inline-form">
                    <input type="text" id="itemName" placeholder="Name" required>
                    <input type="text" id="itemDescription" placeholder="Description">
                    <input type="number" id="itemPrice" placeholder="Price" min="0" step="0.01" required>
                    <button type="submit" class="btn"><i class="fa-solid fa-plus"></i> Add item</button>
                </form>
            </section>`;

        container.querySelector("#pauseBakery").addEventListener("click", () =>
            D.command("PATCH", "/api/bakeries/" + encodeURIComponent(session.bakeryId) + "/pause", undefined,
                bakery => `Bakery is now ${bakery.bakeryStatus}.`));
        container.querySelector("#addItem").addEventListener("submit", addItem);
        await refresh();
    }

    async function loadOrders() {
        const result = await D.api("GET", "/api/orders/bakery");
        if (!result.ok) {
            container.querySelector("#incoming").innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        const orders = result.data;
        renderIncoming(orders.filter(o => o.status === "placed"));
        renderQueue(orders.filter(o => o.status === "accepted")
            .sort((a, b) => (a.queuePosition ?? 999) - (b.queuePosition ?? 999)));
        renderPreparing(orders.filter(o => o.status === "preparing"));
        renderDone(orders.filter(o => ["ready", "picked_up", "delivered", "refunded", "rejected", "cancelled"].includes(o.status))
            .reverse());
    }

    function orderCells(order) {
        return `<td><strong>${D.escape(D.shortId(order.orderId))}</strong><br>
                    <span class="muted">${D.escape(order.recipientName)}</span></td>
                <td>${D.itemsSummary(order)}</td>
                <td><span class="muted">Items subtotal</span><br>${D.money(order.total)}</td>`;
    }

    function detailsButton(order) {
        return `<button type="button" class="btn secondary" data-details="${D.escape(order.orderId)}" title="Details">
                    <i class="fa-solid fa-circle-info"></i></button>`;
    }

    function wire(target, columns) {
        target.querySelectorAll("button[data-details]").forEach(button =>
            button.addEventListener("click", () => D.toggleDetails(button, button.dataset.details, columns)));
    }

    function transition(orderId, action, label) {
        return D.command("PATCH", "/api/orders/" + encodeURIComponent(orderId) + "/" + action, undefined,
            data => `${label}: order ${D.shortId(data.orderId)} is now ${data.newState}`
                + (data.queuePosition ? ` (position ${data.queuePosition} in the queue)` : "")
                + ` (event ${D.shortId(data.eventId)}).`);
    }

    function renderIncoming(orders) {
        const target = container.querySelector("#incoming");
        if (orders.length === 0) {
            target.innerHTML = `<p class="empty">No incoming orders.</p>`;
            return;
        }
        target.innerHTML = `<table><tbody>${orders.map(order => `
            <tr>
                ${orderCells(order)}
                <td class="actions">
                    <button type="button" class="btn" data-accept="${D.escape(order.orderId)}">
                        <i class="fa-solid fa-check"></i> Accept</button>
                    <input type="text" class="reason" placeholder="Reason" data-reason="${D.escape(order.orderId)}">
                    <button type="button" class="btn danger" data-reject="${D.escape(order.orderId)}">
                        <i class="fa-solid fa-ban"></i> Reject</button>
                    ${detailsButton(order)}
                </td>
            </tr>`).join("")}</tbody></table>`;
        target.querySelectorAll("button[data-accept]").forEach(button =>
            button.addEventListener("click", () => transition(button.dataset.accept, "accept", "Accepted")));
        target.querySelectorAll("button[data-reject]").forEach(button =>
            button.addEventListener("click", () => {
                const reason = target.querySelector(`input[data-reason="${CSS.escape(button.dataset.reject)}"]`).value;
                // The backend refuses an empty reason (UC-ORD-06 3a)
                D.command("PATCH", "/api/orders/" + encodeURIComponent(button.dataset.reject) + "/reject",
                    { reason }, data => `Order ${D.shortId(data.orderId)} rejected (event ${D.shortId(data.eventId)}).`);
            }));
        wire(target, 4);
    }

    // Every queued order has a button; the backend only lets the first one start (R-ORD-03)
    function renderQueue(orders) {
        const target = container.querySelector("#queue");
        if (orders.length === 0) {
            target.innerHTML = `<p class="empty">No orders waiting.</p>`;
            return;
        }
        target.innerHTML = `<table><tbody>${orders.map(order => `
            <tr class="${order.queuePosition === 1 ? "highlight" : ""}">
                <td><span class="badge accepted">#${D.escape(order.queuePosition)}</span></td>
                ${orderCells(order)}
                <td class="actions">
                    <button type="button" class="btn" data-start="${D.escape(order.orderId)}">
                        <i class="fa-solid fa-fire-burner"></i> Start preparing</button>
                    ${detailsButton(order)}
                </td>
            </tr>`).join("")}</tbody></table>`;
        target.querySelectorAll("button[data-start]").forEach(button =>
            button.addEventListener("click", () => transition(button.dataset.start, "start-preparing", "Started")));
        wire(target, 5);
    }

    function renderPreparing(orders) {
        const target = container.querySelector("#preparing");
        if (orders.length === 0) {
            target.innerHTML = `<p class="empty">Nothing is being prepared.</p>`;
            return;
        }
        target.innerHTML = `<table><tbody>${orders.map(order => `
            <tr>
                ${orderCells(order)}
                <td class="actions">
                    <button type="button" class="btn" data-ready="${D.escape(order.orderId)}">
                        <i class="fa-solid fa-bell-concierge"></i> Mark ready</button>
                    ${detailsButton(order)}
                </td>
            </tr>`).join("")}</tbody></table>`;
        target.querySelectorAll("button[data-ready]").forEach(button =>
            button.addEventListener("click", () => transition(button.dataset.ready, "ready", "Ready")));
        wire(target, 4);
    }

    function renderDone(orders) {
        const target = container.querySelector("#done");
        if (orders.length === 0) {
            target.innerHTML = `<p class="empty">No finished orders yet.</p>`;
            return;
        }
        target.innerHTML = `<table><tbody>${orders.map(order => `
            <tr>
                ${orderCells(order)}
                <td>${D.badge(order.status)}
                    ${order.rejectionReason ? `<br><span class="muted">${D.escape(order.rejectionReason)}</span>` : ""}</td>
                <td>${detailsButton(order)}</td>
            </tr>`).join("")}</tbody></table>`;
        wire(target, 5);
    }

    // The bakery's whole menu, with every item's status (UC-ORD-09, UC-ORD-10)
    async function loadMenu() {
        const result = await D.api("GET", "/api/menu-items");
        const target = container.querySelector("#menuItems");
        if (!result.ok) {
            target.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        menuItems = result.data;
        if (menuItems.length === 0) {
            target.innerHTML = `<p class="empty">Your menu is empty.</p>`;
            return;
        }
        target.innerHTML = `<table>
            <thead><tr><th>Item</th><th>Price</th><th>Status</th><th></th></tr></thead>
            <tbody>${menuItems.map(item => `
                <tr data-row="${D.escape(item.id)}">
                    <td><strong>${D.escape(item.name)}</strong><br><span class="muted">${D.escape(item.description)}</span></td>
                    <td>${D.money(item.price)}</td>
                    <td>${D.badge(item.status)}</td>
                    <td class="actions">
                        <button type="button" class="btn secondary" data-toggle="${D.escape(item.id)}">
                            <i class="fa-solid fa-arrows-rotate"></i>
                            ${item.status === "available" ? "Mark sold out" : "Mark available"}</button>
                        <button type="button" class="btn secondary" data-edit="${D.escape(item.id)}">
                            <i class="fa-solid fa-pen"></i> Edit</button>
                    </td>
                </tr>`).join("")}</tbody></table>`;

        target.querySelectorAll("button[data-toggle]").forEach(button =>
            button.addEventListener("click", () => {
                const item = menuItems.find(i => i.id === button.dataset.toggle);
                const status = item.status === "available" ? "sold_out" : "available";
                D.command("PATCH", "/api/menu-items/" + encodeURIComponent(item.id) + "/availability", { status },
                    updated => `"${updated.name}" is now ${updated.status}.`);
            }));
        target.querySelectorAll("button[data-edit]").forEach(button =>
            button.addEventListener("click", () => showEditRow(button.dataset.edit)));
    }

    // Inline form under the item to change its name, description and price
    function showEditRow(itemId) {
        const row = container.querySelector(`tr[data-row="${CSS.escape(itemId)}"]`);
        const next = row.nextElementSibling;
        if (next && next.classList.contains("details-row")) {
            next.remove();
            return;
        }
        const item = menuItems.find(i => i.id === itemId);
        const editRow = document.createElement("tr");
        editRow.className = "details-row";
        editRow.innerHTML = `<td colspan="4">
            <form class="inline-form">
                <input type="text" name="name" value="${D.escape(item.name)}" placeholder="Name" required>
                <input type="text" name="description" value="${D.escape(item.description)}" placeholder="Description">
                <input type="number" name="price" value="${D.escape(item.price)}" min="0" step="0.01" required>
                <button type="submit" class="btn"><i class="fa-solid fa-floppy-disk"></i> Save</button>
            </form></td>`;
        row.after(editRow);
        editRow.querySelector("form").addEventListener("submit", event => {
            event.preventDefault();
            // form.name would be the form's own name attribute, so read the inputs by name explicitly
            const field = name => event.target.elements.namedItem(name).value;
            D.command("PUT", "/api/menu-items/" + encodeURIComponent(itemId), {
                name: field("name"),
                description: field("description"),
                price: Number(field("price"))
            }, updated => `"${updated.name}" updated.`);
        });
    }

    async function addItem(event) {
        event.preventDefault();
        const result = await D.command("POST", "/api/menu-items", {
            name: container.querySelector("#itemName").value,
            description: container.querySelector("#itemDescription").value,
            price: Number(container.querySelector("#itemPrice").value)
        }, item => `Menu item "${item.name}" added (available).`);
        if (result.ok) {
            container.querySelector("#addItem").reset();
        }
    }

    async function refresh() {
        await loadOrders();
        await loadMenu();
    }

    return { render, refresh };
})());
