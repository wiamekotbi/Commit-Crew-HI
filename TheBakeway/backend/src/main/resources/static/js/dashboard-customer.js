// Customer panel: browse open bakeries and their menus, place an order, follow and cancel own orders
// (UC-ORD-02, UC-ORD-03, UC-ORD-04, UC-ORD-12)
Dashboard.register("CUSTOMER", (() => {
    const D = Dashboard;

    let selectedBakery = null;
    let bakeries = [];
    let menu = [];
    let cart = [];   // { menuItemId, name, price, quantity }
    let cartPricingRequest = 0;
    let appliedPromoCode = "";
    let orderHistory = null;

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
            </section>`;

        orderHistory = CustomerOrderHistory.create(
            D, container.querySelector("#orders"), bakeryName);
        container.querySelector("#refreshBakeries").addEventListener("click", refresh);
        await refresh();
    }

    async function refresh() {
        await loadBakeries();
        if (selectedBakery && selectedBakeryAcceptsOrders()) {
            await loadMenu(selectedBakery);
        } else {
            if (selectedBakery) menu = [];
            renderCart();
        }
        await orderHistory.load();
    }

    function selectedBakeryAcceptsOrders() {
        return selectedBakery != null
            && bakeries.some(bakery => bakery.bakeryId === selectedBakery);
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
            menu = [];
            renderCart();
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
            appliedPromoCode = "";
            target.innerHTML = `<p class="empty">Your cart is empty.</p>`;
            return;
        }
        const total = cart.reduce((sum, line) => sum + line.price * line.quantity, 0);
        const selectedItemUnavailable = cart.some(line => {
            const item = menu.find(candidate => candidate.id === line.menuItemId);
            return !item || item.status !== "available" || item.discontinued;
        });
        const placeOrderDisabled = !selectedBakeryAcceptsOrders() || selectedItemUnavailable;
        target.innerHTML = `
            <table>
                <tbody>${cart.map((line, index) => `
                    <tr>
                        <td>${line.quantity} x ${D.escape(line.name)}</td>
                        <td>${D.money(line.price * line.quantity)}</td>
                        <td><button type="button" class="btn secondary" data-remove="${index}" title="Remove">
                            <i class="fa-solid fa-trash"></i></button></td>
                    </tr>`).join("")}
                    <tr><td><strong>Items subtotal</strong></td><td><strong>${D.money(total)}</strong></td><td></td></tr>
                </tbody>
            </table>
            <div id="cartPricing"><p class="muted">Calculating fees...</p></div>
            <div class="inline-form" style="margin-top:12px">
                <input type="text" id="promoCode" value="${D.escape(appliedPromoCode)}"
                    placeholder="Promo code" ${appliedPromoCode ? "disabled" : ""}>
                <button type="button" class="btn secondary promo-action" id="applyPromo" ${appliedPromoCode ? "hidden" : ""}>
                    <i class="fa-solid fa-tag"></i> Apply promo
                </button>
                <button type="button" class="btn secondary promo-action" id="removePromo" ${appliedPromoCode ? "" : "hidden"}>
                    <i class="fa-solid fa-xmark"></i> Remove promo
                </button>
            </div>
            <div class="inline-form" style="margin-top:12px">
                <input type="text" id="deliveryAddress" placeholder="Delivery address (empty: your profile address)">
                <button type="button" class="btn" id="placeOrder" ${placeOrderDisabled ? "disabled" : ""}>
                    <i class="fa-solid fa-paper-plane"></i> Place order</button>
            </div>`;
        target.querySelectorAll("button[data-remove]").forEach(button =>
            button.addEventListener("click", () => {
                cart.splice(Number(button.dataset.remove), 1);
                renderCart();
            }));
        target.querySelector("#applyPromo").addEventListener("click", () => {
            const enteredCode = target.querySelector("#promoCode").value.trim();
            loadCartPricing(total, enteredCode);
        });
        target.querySelector("#removePromo").addEventListener("click", () => {
            appliedPromoCode = "";
            target.querySelector("#promoCode").value = "";
            target.querySelector("#promoCode").disabled = false;
            target.querySelector("#applyPromo").hidden = false;
            target.querySelector("#removePromo").hidden = true;
            loadCartPricing(total, "");
        });
        target.querySelector("#placeOrder").addEventListener("click", placeOrder);
        loadCartPricing(total);
    }

    async function loadCartPricing(subtotal, promoCode = appliedPromoCode) {
        const requestId = ++cartPricingRequest;
        const target = container.querySelector("#cartPricing");
        try {
            let url = "/api/orders/pricing?subtotal=" + encodeURIComponent(subtotal);
            if (promoCode) {
                url += "&promoCode=" + encodeURIComponent(promoCode);
            }
            const result = await D.api("GET", url);
            if (requestId !== cartPricingRequest || !target.isConnected) return;
            if (!result.ok) {
                // Only successfully validated codes are kept for order placement.
                appliedPromoCode = "";
                const applyButton = container.querySelector("#applyPromo");
                if (applyButton) applyButton.hidden = false;
                const removeButton = container.querySelector("#removePromo");
                if (removeButton) removeButton.hidden = true;
                const input = container.querySelector("#promoCode");
                if (input) input.disabled = false;
                const promoError = D.errorText(result);
                if (promoCode) {
                    try {
                        const baseResult = await D.api("GET",
                            "/api/orders/pricing?subtotal=" + encodeURIComponent(subtotal));
                        if (requestId !== cartPricingRequest || !target.isConnected) return;
                        if (baseResult.ok) {
                            renderCartPricing(target, baseResult.data, promoError);
                            return;
                        }
                        target.innerHTML = `<p class="empty">${D.escape(promoError)}
                            Fees unavailable: ${D.escape(D.errorText(baseResult))}</p>`;
                    } catch {
                        if (requestId === cartPricingRequest && target.isConnected) {
                            target.innerHTML = `<p class="empty">${D.escape(promoError)}
                                Fees could not be calculated. Please try again.</p>`;
                        }
                    }
                    return;
                }
                target.innerHTML = `<p class="empty">${D.escape(promoError)}</p>`;
                return;
            }

            const acceptedPromo = result.data.promoCode;
            if (acceptedPromo) {
                // Keep the canonical code from the configuration after a successful lookup.
                appliedPromoCode = acceptedPromo;
                const input = container.querySelector("#promoCode");
                if (input) {
                    input.value = acceptedPromo;
                    input.disabled = true;
                }
                const applyButton = container.querySelector("#applyPromo");
                if (applyButton) applyButton.hidden = true;
                const removeButton = container.querySelector("#removePromo");
                if (removeButton) removeButton.hidden = false;
            } else {
                appliedPromoCode = "";
                const input = container.querySelector("#promoCode");
                if (input) input.disabled = false;
                const applyButton = container.querySelector("#applyPromo");
                if (applyButton) applyButton.hidden = false;
                const removeButton = container.querySelector("#removePromo");
                if (removeButton) removeButton.hidden = true;
            }
            renderCartPricing(target, result.data);
        } catch (error) {
            if (requestId === cartPricingRequest && target.isConnected) {
                appliedPromoCode = "";
                const applyButton = container.querySelector("#applyPromo");
                if (applyButton) applyButton.hidden = false;
                const removeButton = container.querySelector("#removePromo");
                if (removeButton) removeButton.hidden = true;
                const input = container.querySelector("#promoCode");
                if (input) input.disabled = false;
                target.innerHTML = '<p class="empty">Unable to calculate order fees. Please try again.</p>';
            }
        }
    }

    function renderCartPricing(target, data, promoError = "") {
        const pricing = data.pricing;
        const acceptedPromo = data.promoCode;
        const estimatedTotal = data.discountedTotal ?? pricing.total;
        target.innerHTML = `
            <table aria-label="Estimated order charges">
                <tbody>
                    <tr><th scope="row">Delivery fee</th><td>${D.money(pricing.deliveryFee)}</td></tr>
                    <tr><th scope="row">Service fee</th><td>${D.money(pricing.serviceFee)}</td></tr>
                    ${pricing.smallOrderSurcharge > 0 ? `<tr><th scope="row">Small-order surcharge</th>
                        <td>${D.money(pricing.smallOrderSurcharge)}</td></tr>` : ""}
                    ${acceptedPromo ? `<tr><th scope="row">Promotion (${D.escape(acceptedPromo)})</th>
                        <td>-${D.money(data.discountAmount)}</td></tr>` : ""}
                    <tr><th scope="row">Estimated total</th><td><strong>${D.money(estimatedTotal)}</strong></td></tr>
                </tbody>
            </table>
            ${promoError ? `<p class="empty">Promo not applied: ${D.escape(promoError)}</p>` : ""}
            <p class="muted">Estimate only. Fees and the final total are recorded when the order is delivered.</p>`;
    }

    async function placeOrder() {
        const result = await D.command("POST", "/api/orders", {
            bakeryId: selectedBakery,
            items: cart.map(line => ({ menuItemId: line.menuItemId, quantity: line.quantity })),
            deliveryAddress: container.querySelector("#deliveryAddress").value,
            // Anthony's order integration will persist and revalidate this code at placement time.
            promoCode: appliedPromoCode || null
        }, data => `Order ${D.shortId(data.orderId)} placed (event ${D.shortId(data.eventId)}).`);

        if (result.ok) {
            cart = [];
            appliedPromoCode = "";
            renderCart();
        } else if (selectedBakery) {
            // The menu may have changed (sold-out item, paused bakery)
            await loadMenu(selectedBakery);
        }
    }

    return { render, refresh };
})());
