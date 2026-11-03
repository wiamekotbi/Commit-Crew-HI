const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const vm = require("node:vm");

const staticRoot = resolve(__dirname, "../../main/resources/static");
const scripts = ["dashboard.js", "order-summary.js"]
    .map(name => readFileSync(resolve(staticRoot, "js", name), "utf8"));

function page(replies, search = "?orderId=order-1") {
    const elements = Object.fromEntries(["orderSummary", "retrySummary", "logoutButton"].map(id => [id, {
        innerHTML: "",
        disabled: false,
        attributes: {},
        listeners: {},
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(name, action) { this.listeners[name] = action; }
    }]));
    const requests = [];
    const window = { location: { search, href: "" } };
    const context = vm.createContext({
        document: { getElementById: id => elements[id] },
        window,
        URLSearchParams,
        fetch: async (url, options) => {
            requests.push({ url, options });
            const reply = replies.shift();
            if (reply instanceof Error) throw reply;
            if (typeof reply === "function") return reply();
            if (!reply) throw new Error("Unexpected request: " + url);
            return response(reply.status || 200, reply.data);
        }
    });
    for (const script of scripts) vm.runInContext(script, context);
    return { elements, requests, window };
}

function response(status, data) {
    return { ok: status < 400, status, text: async () => JSON.stringify(data) };
}

function summary(surcharge = 2) {
    return {
        orderId: "order-1",
        status: "delivered",
        deliveredAt: "2026-11-02T15:00:00Z",
        items: [{ itemName: "Croissant", quantity: 2, unitPrice: 4, subtotal: 8 }],
        pricing: { subtotal: 8, deliveryFee: 3, serviceFee: 1, smallOrderSurcharge: surcharge, total: 12 + surcharge },
        refunds: []
    };
}

const customer = () => ({ data: { role: "CUSTOMER" } });
const settled = () => new Promise(resolve => setImmediate(resolve));

test("shows loading until the real API response arrives, then renders all charges", async () => {
    let finish;
    const pending = new Promise(resolve => { finish = resolve; });
    const view = page([customer(), () => pending]);
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /Loading order summary/);
    assert.equal(view.elements.orderSummary.attributes["aria-busy"], "true");
    assert.equal(view.elements.retrySummary.disabled, true);
    finish(response(200, summary()));
    await settled();

    const html = view.elements.orderSummary.innerHTML;
    for (const label of ["order-1", "Croissant", "Quantity", "Unit price", "Subtotal", "Delivery fee",
        "Service fee", "Small-order surcharge", "Final total", "Delivered:", "$14.00"]) {
        assert.ok(html.includes(label), label);
    }
    for (const [label, amount] of [["Subtotal", "8.00"], ["Delivery fee", "3.00"],
        ["Service fee", "1.00"], ["Small-order surcharge", "2.00"]]) {
        assert.ok(html.includes(`${label}</th><td>$${amount}</td>`)
            || new RegExp(`${label}</th>\\s*<td>\\$${amount.replace(".", "\\.")}</td>`).test(html), label);
    }
    assert.equal(view.elements.orderSummary.attributes["aria-busy"], "false");
    assert.equal(view.elements.retrySummary.disabled, false);
    assert.equal(view.requests[1].url, "/api/orders/order-1/summary");
    assert.equal(view.requests[1].options.credentials, "same-origin");
});

test("does not display a surcharge when none was charged", async () => {
    const view = page([customer(), { data: summary(0) }]);
    await settled();
    assert.doesNotMatch(view.elements.orderSummary.innerHTML, /Small-order surcharge/);
    assert.match(view.elements.orderSummary.innerHTML, /\$12\.00/);
});

test("shows refunds separately and keeps the original final total", async () => {
    const data = summary();
    data.status = "refunded";
    data.refunds = [{ refundId: "refund-1", amount: 14, refundedAt: "2026-11-02T16:00:00Z", reason: "Refunded" }];
    const view = page([customer(), { data }]);
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /Final total/);
    assert.match(view.elements.orderSummary.innerHTML, /original charge above is unchanged/);
    assert.match(view.elements.orderSummary.innerHTML, /refund-1/);
    assert.match(view.elements.orderSummary.innerHTML, /Croissant/);
    assert.match(view.elements.orderSummary.innerHTML, /<td>2<\/td>/);
    assert.match(view.elements.orderSummary.innerHTML, /<td>\$4\.00<\/td>/);
    assert.equal((view.elements.orderSummary.innerHTML.match(/\$14\.00/g) || []).length, 2);
});

test("explains when a refunded order has no refund details yet", async () => {
    const data = summary();
    data.status = "refunded";
    const view = page([customer(), { data }]);
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /Refund details are not available yet/);
});

for (const [status, error] of [
    [404, "Order not found"],
    [403, "Access denied"],
    [409, "Order summary is available only after delivery"],
    [409, "Order summary is not available: no recorded charge"],
    [409, "Order summary is unavailable until billing integration is connected"]
]) {
    test(`displays the backend error: ${error}`, async () => {
        const view = page([customer(), { status, data: { error } }]);
        await settled();
        assert.ok(view.elements.orderSummary.innerHTML.includes(error));
        assert.doesNotMatch(view.elements.orderSummary.innerHTML, /Final total/);
        assert.equal(view.elements.retrySummary.disabled, false);
    });
}

test("redirects an expired session to the existing login page", async () => {
    const view = page([{ status: 401, data: { error: "Authentication required" } }]);
    await settled();
    assert.equal(view.window.location.href, "/login.html");
    assert.equal(view.requests.length, 1);
});

test("blocks noncustomer navigation before requesting a summary", async () => {
    const view = page([{ data: { role: "OPERATOR" } }]);
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /Only customers/);
    assert.equal(view.requests.length, 1);
});

test("handles a missing order ID", async () => {
    const view = page([customer()], "");
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /No order selected/);
    assert.equal(view.requests.length, 1);
});

test("can retry after a network error", async () => {
    const view = page([customer(), new Error("Offline"), customer(), { data: summary() }]);
    await settled();
    assert.match(view.elements.orderSummary.innerHTML, /Unable to load/);
    await view.elements.retrySummary.listeners.click();
    assert.match(view.elements.orderSummary.innerHTML, /Final total/);
});

test("escapes item names and backend errors before inserting HTML", async () => {
    const data = summary();
    data.items[0].itemName = '<img src=x onerror="alert(1)">';
    const view = page([customer(), { data }, customer(), { status: 404, data: { error: "<script>" } }]);
    await settled();
    assert.doesNotMatch(view.elements.orderSummary.innerHTML, /<img/);
    assert.match(view.elements.orderSummary.innerHTML, /&lt;img/);
    await view.elements.retrySummary.listeners.click();
    assert.match(view.elements.orderSummary.innerHTML, /&lt;script&gt;/);
});

test("customer history links eligible orders to the connected summary page", async () => {
    const orders = { innerHTML: "", querySelectorAll: () => [] };
    let panel;
    const context = vm.createContext({
        Dashboard: {
            register: (role, value) => { panel = value; },
            api: async () => ({ ok: true, data: [
                { orderId: "delivered-1", status: "delivered" },
                { orderId: "refunded-1", status: "refunded" },
                { orderId: "placed-1", status: "placed" }
            ] }),
            escape: value => String(value),
            time: () => "time",
            money: () => "$0.00",
            badge: value => value,
            itemsSummary: () => "items"
        }
    });
    vm.runInContext(readFileSync(resolve(staticRoot, "js/dashboard-customer.js"), "utf8"), context);
    const container = { innerHTML: "", querySelector: id => id === "#orders" ? orders : {
        innerHTML: "", textContent: "", addEventListener: () => {}, querySelectorAll: () => []
    } };
    await panel.render(container);
    assert.match(orders.innerHTML, /order-summary\.html\?orderId=delivered-1/);
    assert.match(orders.innerHTML, /order-summary\.html\?orderId=refunded-1/);
    assert.doesNotMatch(orders.innerHTML, /order-summary\.html\?orderId=placed-1/);
});
