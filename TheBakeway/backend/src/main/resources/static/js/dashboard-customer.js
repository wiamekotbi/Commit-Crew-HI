// Customer panel: browse open bakeries and their menus, place an order, follow and cancel own orders
// (UC-ORD-02, UC-ORD-03, UC-ORD-04, UC-ORD-12)
Dashboard.register("CUSTOMER", (() => {
    const D = Dashboard;

    let container = null;
    let bakeries = [];
    let selectedBakery = null;
    let menu = [];
    let cart = [];   // { menuItemId, name, price, quantity }

    function bakeryName(bakeryId) {
        const bakery = bakeries.find(b => b.bakeryId === bakeryId);
        return bakery ? bakery.bakeryName : bakeryId;
    }

    async function render(target) {
        container = target;
        container.innerHTML = `
            <div class="grid-2">
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-store"></i> Open bakeries</h2>
                        <button type="button" class="btn secondary" id="refreshBakeries">
                            <i class="fa-solid fa-rotate"></i> Refresh
                        </button>
                    </div>
                    <div id="bakeryList" class="bakery-list"></div>
                </section>
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-cart-shopping"></i> Cart</h2>
                        <span id="cartBakery" class="muted"></span>
                    </div>
                    <div id="cart"></div>
                </section>
            </div>
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-book-open"></i> Menu</h2>
                    <span id="menuBakery" class="muted">Pick a bakery to see its menu</span>
                </div>
                <div id="menu" class="table-wrap"></div>
            </section>
            <section class="card">
                <div class="card-title"><h2><i class="fa-solid fa-receipt"></i> My orders</h2></div>
                <div id="orders" class="table-wrap"></div>
            </section>
            <section class="card map-card">
                <div class="card-title">
                    <h2>
                        <i class="fa-solid fa-map-location-dot"></i>
                        Delivery Map
                    </h2>
                </div>
                <div id="map"></div>
            </section>`;

        // Initialize Google Maps
        BakewayMap.init();

        container.querySelector("#refreshBakeries").addEventListener("click", loadBakeries);
        await loadBakeries();
        renderCart();
        await loadOrders();
    }

    async function loadBakeries() {
        const result = await D.api("GET", "/api/bakeries");
        const list = container.querySelector("#bakeryList");
        if (!result.ok) {
            list.innerHTML = `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        bakeries = result.data;
        if (bakeries.length === 0) {
            list.innerHTML = `<p class="empty">No bakery is open right now.</p>`;
            return;
        }
        BakewayMap.setBakeries(bakeries);
        void loadBakeryQueueSizes();
        list.innerHTML = bakeries.map(bakery => `
            <div class="bakery-item ${selectedBakery === bakery.bakeryId ? "selected" : ""}">
                <div>
                    <strong>${D.escape(bakery.bakeryName)}</strong><br>
                    <span class="muted"><i class="fa-solid fa-location-dot"></i> ${D.escape(bakery.bakeryAddress)}</span>
                </div>
                <button type="button" class="btn" data-bakery="${D.escape(bakery.bakeryId)}">
                    <i class="fa-solid fa-book-open"></i> Menu
                </button>
            </div>`).join("");
        list.querySelectorAll("button[data-bakery]").forEach(button =>
            button.addEventListener("click", () => loadMenu(button.dataset.bakery)));
    }

    async function loadMenu(bakeryId) {
        const result = await D.api("GET", "/api/bakeries/" + encodeURIComponent(bakeryId) + "/menu");
        const target = container.querySelector("#menu");
        if (!result.ok) {
            // UC-ORD-02 3a: the bakery was paused or closed in the meantime
            D.showMessage(D.errorText(result), "error");
            target.innerHTML = "";
            await loadBakeries();
            return;
        }

        if (cart.length > 0 && selectedBakery !== bakeryId) {
            // An order belongs to one bakery
            cart = [];
            D.showMessage("Cart emptied: an order can only contain items from one bakery.", "success");
        }
        selectedBakery = bakeryId;
        menu = result.data;
        container.querySelector("#menuBakery").textContent = bakeryName(bakeryId);
        await loadBakeries();
        renderCart();

        if (menu.length === 0) {
            target.innerHTML = `<p class="empty">This bakery has no menu items yet.</p>`;
            return;
        }
        target.innerHTML = `
            <table>
                <thead><tr><th>Item</th><th>Price</th><th>Status</th><th>Quantity</th><th></th></tr></thead>
                <tbody>${menu.map(item => {
                    // Sold-out (and discontinued) items are shown but cannot be added (UC-ORD-02 step 4)
                    const soldOut = item.status !== "available" || item.discontinued;
                    return `<tr>
                        <td><strong>${D.escape(item.name)}</strong><br>
                            <span class="muted">${D.escape(item.description)}</span></td>
                        <td>${D.money(item.price)}</td>
                        <td>${D.badge(item.status)}</td>
                        <td><input class="qty" type="number" min="1" value="1" data-qty="${D.escape(item.id)}"
                            ${soldOut ? "disabled" : ""}></td>
                        <td><button type="button" class="btn" data-add="${D.escape(item.id)}" ${soldOut ? "disabled" : ""}>
                            <i class="fa-solid fa-cart-plus"></i> Add</button></td>
                    </tr>`;
                }).join("")}</tbody>
            </table>`;
        target.querySelectorAll("button[data-add]").forEach(button =>
            button.addEventListener("click", () => addToCart(button.dataset.add)));
    }

    function addToCart(menuItemId) {
        const item = menu.find(i => i.id === menuItemId);
        const input = container.querySelector(`input[data-qty="${CSS.escape(menuItemId)}"]`);
        const quantity = parseInt(input.value, 10);
        const line = cart.find(l => l.menuItemId === menuItemId);
        if (line) {
            line.quantity += quantity;
        } else {
            cart.push({ menuItemId, name: item.name, price: item.price, quantity });
        }
        renderCart();
    }

    function renderCart() {
        const target = container.querySelector("#cart");
        container.querySelector("#cartBakery").textContent = selectedBakery ? bakeryName(selectedBakery) : "";
        if (cart.length === 0) {
            target.innerHTML = `<p class="empty">Your cart is empty.</p>`;
            return;
        }
        const total = cart.reduce((sum, line) => sum + line.price * line.quantity, 0);
        target.innerHTML = `
            <table>
                <tbody>${cart.map((line, index) => `
                    <tr>
                        <td>${line.quantity} x ${D.escape(line.name)}</td>
                        <td>${D.money(line.price * line.quantity)}</td>
                        <td><button type="button" class="btn secondary" data-remove="${index}" title="Remove">
                            <i class="fa-solid fa-trash"></i></button></td>
                    </tr>`).join("")}
                    <tr><td><strong>Total</strong></td><td><strong>${D.money(total)}</strong></td><td></td></tr>
                </tbody>
            </table>
            <div class="inline-form" style="margin-top:12px">
                <input type="text" id="deliveryAddress" placeholder="Delivery address (empty: your profile address)">
                <button type="button" class="btn" id="placeOrder"><i class="fa-solid fa-paper-plane"></i> Place order</button>
            </div>`;
        target.querySelectorAll("button[data-remove]").forEach(button =>
            button.addEventListener("click", () => {
                cart.splice(Number(button.dataset.remove), 1);
                renderCart();
            }));
        target.querySelector("#placeOrder").addEventListener("click", placeOrder);
    }

    async function loadBakeryQueueSizes() {
        await Promise.all(bakeries.map(async bakery => {
            const result = await D.api(
                "GET",
                "/api/bakeries/" +
                    encodeURIComponent(bakery.bakeryId) +
                    "/preparation-queue-size"
            );

            if (result.ok) {
                BakewayMap.updateBakeryQueue(
                    bakery.bakeryId,
                    result.data
                );
            } else {
                console.error(
                    "Failed to load preparation queue for",
                    bakery.bakeryName,
                    D.errorText(result)
                );
            }
        }));
    }

    async function placeOrder() {
        const result = await D.command("POST", "/api/orders", {
            bakeryId: selectedBakery,
            items: cart.map(line => ({ menuItemId: line.menuItemId, quantity: line.quantity })),
            deliveryAddress: container.querySelector("#deliveryAddress").value
        }, data => `Order ${D.shortId(data.orderId)} placed (event ${D.shortId(data.eventId)}).`);

        if (result.ok) {
            cart = [];
            renderCart();
        } else if (selectedBakery) {
            // The menu may have changed (sold-out item, paused bakery)
            await loadMenu(selectedBakery);
        }
    }

    async function loadOrders() {
        const result = await D.api("GET", "/api/orders/mine");
        const target = container.querySelector("#orders");
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
                <thead><tr><th>Placed</th><th>Bakery</th><th>Items</th><th>Total</th><th>Status</th><th></th></tr></thead>
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

    return { render, refresh: loadOrders };
})());
