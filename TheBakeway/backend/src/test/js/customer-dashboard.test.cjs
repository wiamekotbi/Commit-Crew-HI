const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const vm = require("node:vm");

const orderHistoryScript = readFileSync(
    resolve(__dirname, "../../main/resources/static/js/dashboard-customer-orders.js"),
    "utf8"
);
const customerScript = readFileSync(
    resolve(__dirname, "../../main/resources/static/js/dashboard-customer.js"),
    "utf8"
).replace(
    "return { render, refresh };",
    `return {
        render,
        refresh,
        __test: {
            setState(state) {
                selectedBakery = state.selectedBakery;
                bakeries = state.bakeries;
                menu = state.menu;
                cart = state.cart;
                container = state.container;
                orderHistory = CustomerOrderHistory.create(
                    D, container.querySelector("#orders"), bakeryName);
            },
            renderCart,
            setPromoCode(code) {
                appliedPromoCode = code;
            },
            loadCartPricing,
            loadOrders: () => orderHistory.load(),
            rateOrder: orderId => orderHistory.rateOrder(orderId)
        }
    };`
);

function loadCustomerDashboard(Dashboard) {
    const context = vm.createContext({
        Dashboard,
        CSS: { escape: value => String(value) }
    });
    vm.runInContext(orderHistoryScript, context);
    vm.runInContext(customerScript, context);
}

class Element {
    constructor() {
        this.innerHTML = "";
        this.textContent = "";
        this.isConnected = true;
    }

    querySelector() {
        return new Element();
    }

    querySelectorAll() {
        return [];
    }

    addEventListener() {}
}

function renderCart({ bakeries, menu }) {
    let panel;
    const Dashboard = {
        register(role, value) {
            assert.equal(role, "CUSTOMER");
            panel = value;
        },
        async api() {
            return {
                ok: true,
                data: {
                    pricing: {
                        deliveryFee: 4,
                        serviceFee: 2,
                        smallOrderSurcharge: 0,
                        total: 16
                    }
                }
            };
        },
        escape: value => String(value ?? ""),
        money: value => `$${Number(value).toFixed(2)}`
    };
    loadCustomerDashboard(Dashboard);

    const cart = new Element();
    const cartBakery = new Element();
    const container = {
        querySelector(selector) {
            if (selector === "#cart") return cart;
            if (selector === "#cartBakery") return cartBakery;
            return new Element();
        }
    };
    panel.__test.setState({
        selectedBakery: "bakery-1",
        bakeries,
        menu,
        cart: [{ menuItemId: "item-1", name: "Bread", price: 10, quantity: 1 }],
        container
    });
    panel.__test.renderCart();
    return cart.innerHTML;
}

test("place order is enabled when the bakery and selected item are available", () => {
    const markup = renderCart({
        bakeries: [{ bakeryId: "bakery-1", bakeryName: "Bakery" }],
        menu: [{ id: "item-1", status: "available", discontinued: false }]
    });

    assert.match(markup, /id="placeOrder"/);
    assert.match(markup, /id="promoCode"/);
    assert.match(markup, /id="applyPromo"/);
    assert.doesNotMatch(markup, /id="applyPromo" hidden/);
    assert.match(markup, /id="removePromo" hidden/);
    assert.doesNotMatch(markup, /<button[^>]*id="placeOrder"[^>]*disabled/);
});

async function loadPromoPricing(apiResult, promoCode) {
    let panel;
    const calls = [];
    const Dashboard = {
        register(role, value) {
            assert.equal(role, "CUSTOMER");
            panel = value;
        },
        async api(method, url) {
            calls.push({ method, url });
            return apiResult;
        },
        escape: value => String(value ?? ""),
        errorText: result => result.data.error,
        money: value => `$${Number(value).toFixed(2)}`
    };
    loadCustomerDashboard(Dashboard);

    const pricing = new Element();
    const promoInput = new Element();
    const applyPromo = new Element();
    const removePromo = new Element();
    const container = {
        querySelector(selector) {
            if (selector === "#cartPricing") return pricing;
            if (selector === "#promoCode") return promoInput;
            if (selector === "#applyPromo") return applyPromo;
            if (selector === "#removePromo") return removePromo;
            if (selector === "#orders") return new Element();
            return new Element();
        }
    };
    panel.__test.setState({
        selectedBakery: "bakery-1",
        bakeries: [],
        menu: [],
        cart: [],
        container
    });
    panel.__test.setPromoCode(promoCode);
    await panel.__test.loadCartPricing(25);
    return { calls, markup: pricing.innerHTML, promoInput, applyPromo, removePromo };
}

test("checkout pricing displays an accepted promotion and discounted total", async () => {
    const view = await loadPromoPricing({
        ok: true,
        data: {
            pricing: { deliveryFee: 4, serviceFee: 2, smallOrderSurcharge: 0, total: 31 },
            promoCode: "SAVE5",
            discountAmount: 5,
            discountedTotal: 26
        }
    }, "save5");

    assert.deepEqual(view.calls, [
        { method: "GET", url: "/api/orders/pricing?subtotal=25&promoCode=save5" }
    ]);
    assert.match(view.markup, /Promotion \(SAVE5\)/);
    assert.match(view.markup, /-\$5\.00/);
    assert.match(view.markup, /\$26\.00/);
    assert.equal(view.promoInput.value, "SAVE5");
    assert.equal(view.promoInput.disabled, true);
    assert.equal(view.applyPromo.hidden, true);
    assert.equal(view.removePromo.hidden, false);
});

test("checkout pricing displays the backend promotion rejection", async () => {
    const view = await loadPromoPricing({
        ok: false,
        data: { error: "This promo code requires an order value of at least 20.00" }
    }, "SAVE5");

    assert.match(view.markup, /requires an order value of at least 20\.00/);
    assert.equal(view.promoInput.disabled, false);
    assert.equal(view.applyPromo.hidden, false);
    assert.equal(view.removePromo.hidden, true);
});

test("place order is disabled when the selected bakery is paused or closed", () => {
    const markup = renderCart({
        bakeries: [],
        menu: [{ id: "item-1", status: "available", discontinued: false }]
    });

    assert.match(markup, /<button[^>]*id="placeOrder"[^>]*disabled/);
});

test("place order is disabled when a selected item becomes unavailable", () => {
    const markup = renderCart({
        bakeries: [{ bakeryId: "bakery-1", bakeryName: "Bakery" }],
        menu: [{ id: "item-1", status: "sold_out", discontinued: false }]
    });

    assert.match(markup, /<button[^>]*id="placeOrder"[^>]*disabled/);
});

async function loadOrderHistory(orders, ratings = []) {
    let panel;
    const calls = [];
    const commands = [];
    const Dashboard = {
        register(role, value) {
            assert.equal(role, "CUSTOMER");
            panel = value;
        },
        async api(method, url) {
            calls.push({ method, url });
            if (url === "/api/orders/mine") return { ok: true, data: orders };
            if (url === "/api/order-ratings/mine") return { ok: true, data: ratings };
            throw new Error(`Unexpected API request: ${method} ${url}`);
        },
        escape: value => String(value ?? ""),
        time: value => value,
        itemsSummary: order => order.lines.map(line => `${line.quantity} x ${line.itemName}`).join(", "),
        money: value => `$${Number(value).toFixed(2)}`,
        badge: value => `[${value}]`,
        toggleDetails() {},
        command: async (...args) => {
            commands.push(args);
            return { ok: true };
        }
    };
    loadCustomerDashboard(Dashboard);

    const orderList = new Element();
    orderList.querySelector = () => ({ value: "4" });
    const container = {
        querySelector(selector) {
            if (selector === "#orders") return orderList;
            return new Element();
        }
    };
    panel.__test.setState({
        selectedBakery: null,
        bakeries: [{ bakeryId: "bakery-1", bakeryName: "Sweet Treats" }],
        menu: [],
        cart: [],
        container
    });
    await panel.__test.loadOrders();
    return {
        markup: orderList.innerHTML,
        calls,
        rateOrder: panel.__test.rateOrder,
        commands
    };
}

test("customer order history preserves status-specific summary and cancel actions", async () => {
    const { markup, calls } = await loadOrderHistory([
        {
            orderId: "placed-order",
            bakeryId: "bakery-1",
            createdAt: "2026-11-18T12:00:00Z",
            total: 7,
            status: "placed",
            rejectionReason: null,
            lines: [{ quantity: 2, itemName: "Croissant" }]
        },
        {
            orderId: "delivered-order",
            bakeryId: "bakery-1",
            createdAt: "2026-11-18T13:00:00Z",
            total: 16,
            status: "delivered",
            rejectionReason: null,
            lines: [{ quantity: 1, itemName: "Bread" }]
        },
        {
            orderId: "rejected-order",
            bakeryId: "bakery-1",
            createdAt: "2026-11-18T14:00:00Z",
            total: 5,
            status: "rejected",
            rejectionReason: "Out of stock",
            lines: [{ quantity: 1, itemName: "Cake" }]
        }
    ]);

    assert.deepEqual(calls, [
        { method: "GET", url: "/api/orders/mine" },
        { method: "GET", url: "/api/order-ratings/mine" }
    ]);
    assert.match(markup, /Sweet Treats/);
    assert.match(markup, /2 x Croissant/);
    assert.match(markup, /data-cancel="placed-order"/);
    assert.doesNotMatch(markup, /data-cancel="delivered-order"/);
    assert.match(markup, /order-summary\.html\?orderId=delivered-order/);
    assert.doesNotMatch(markup, /order-summary\.html\?orderId=placed-order/);
    assert.match(markup, /data-rate-order="delivered-order"/);
    assert.match(markup, /data-bakery-rating="delivered-order"/);
    assert.match(markup, /data-courier-rating="delivered-order"/);
    assert.doesNotMatch(markup, /data-rate-order="placed-order"/);
    assert.doesNotMatch(markup, /data-rate-order="rejected-order"/);
    assert.match(markup, /Out of stock/);
    assert.equal((markup.match(/data-details=/g) || []).length, 3);
});

test("customer order history preserves its empty state", async () => {
    const { markup } = await loadOrderHistory([]);

    assert.match(markup, /You have no orders yet/);
});

test("customer order history shows a rated order's two scores instead of the form", async () => {
    const { markup } = await loadOrderHistory([{
        orderId: "delivered-order",
        bakeryId: "bakery-1",
        createdAt: "2026-11-18T13:00:00Z",
        total: 16,
        status: "delivered",
        rejectionReason: null,
        lines: [{ quantity: 1, itemName: "Bread" }]
    }], [{
        orderId: "delivered-order",
        bakeryId: "bakery-1",
        bakeryScore: 3,
        courierId: "courier-1",
        courierScore: 4
    }]);

    assert.match(markup, /Rated: bakery 3\/5, courier 4\/5/);
    assert.doesNotMatch(markup, /data-rate-order=/);
});

test("customer order history submits the bakery and courier ratings together", async () => {
    const view = await loadOrderHistory([]);

    await view.rateOrder("order-1");

    assert.equal(view.commands.length, 1);
    assert.equal(view.commands[0][0], "POST");
    assert.equal(view.commands[0][1], "/api/orders/order-1/rating");
    assert.equal(view.commands[0][2].bakeryScore, 4);
    assert.equal(view.commands[0][2].courierScore, 4);
    assert.equal(view.commands[0][3], "Order rated: bakery 4/5, courier 4/5.");
});
