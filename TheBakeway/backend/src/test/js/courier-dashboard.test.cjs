const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const vm = require("node:vm");

const courierScript = readFileSync(
    resolve(__dirname, "../../main/resources/static/js/dashboard-courier.js"),
    "utf8"
);

class Element {
    constructor() {
        this.innerHTML = "";
        this.isConnected = true;
    }

    querySelector() {
        return new Element();
    }

    addEventListener() {}
}

function dashboard(deliveries, courier = { status: "OFFLINE" }) {
    const calls = [];
    let panel;
    const Dashboard = {
        register(role, value) {
            assert.equal(role, "COURIER");
            panel = value;
        },
        async api(method, url) {
            calls.push({ method, url });
            if (url === "/api/couriers/me") {
                return { ok: true, data: courier };
            }
            if (url === "/api/couriers/me/past-deliveries") {
                return { ok: true, data: deliveries };
            }
            throw new Error(`Unexpected API request: ${method} ${url}`);
        },
        escape: value => String(value ?? ""),
        shortId: value => String(value).slice(0, 8),
        time: value => value,
        money: value => `$${Number(value).toFixed(2)}`,
        badge: value => value,
        command: async () => ({ ok: true })
    };
    vm.runInNewContext(courierScript, { Dashboard });
    const elements = {};
    const container = {
        set innerHTML(markup) {
            for (const [, id] of markup.matchAll(/id="([^"]+)"/g)) {
                elements[id] = new Element();
            }
        },
        querySelector(selector) {
            return elements[selector.slice(1)];
        }
    };
    return { panel, container, calls, elements };
}

test("courier dashboard renders past deliveries without charges", async () => {
    const view = dashboard([{
        orderId: "order-12345678",
        bakeryId: "bakery-1",
        deliveryAddress: "123 Main Street",
        orderStatus: "delivered",
        deliveredAt: "2026-11-07T12:00:00Z"
    }]);

    await view.panel.render(view.container);

    assert.deepEqual(view.calls.map(call => call.url), [
        "/api/couriers/me",
        "/api/couriers/me/past-deliveries"
    ]);
    const markup = view.elements.courierPastDeliveries.innerHTML;
    assert.match(markup, /order-12/);
    assert.match(markup, /123 Main Street/);
    assert.doesNotMatch(markup, /\$/);
});

test("courier dashboard replaces loading message when history is empty", async () => {
    const view = dashboard([]);

    await view.panel.render(view.container);

    assert.match(view.elements.courierPastDeliveries.innerHTML, /no past deliveries yet/i);
});

test("courier dashboard keeps both delivery controls disabled without an assigned order", async () => {
    const view = dashboard([], { status: "IDLE" });

    await view.panel.render(view.container);

    const markup = view.elements.courierDelivery.innerHTML;
    assert.match(markup, /<button[^>]*id="confirmPickup"[^>]*disabled/);
    assert.match(markup, /<button[^>]*id="confirmDelivery"[^>]*disabled/);
});

test("courier dashboard enables only pickup for an assigned order", async () => {
    const view = dashboard([], { status: "ASSIGNED", currentOrderId: "order-1" });

    await view.panel.render(view.container);

    const markup = view.elements.courierDelivery.innerHTML;
    assert.match(markup, /id="confirmPickup"/);
    assert.doesNotMatch(markup, /<button[^>]*id="confirmPickup"[^>]*disabled/);
    assert.match(markup, /<button[^>]*id="confirmDelivery"[^>]*disabled/);
});

test("courier dashboard enables only delivery while carrying", async () => {
    const view = dashboard([], { status: "CARRYING", currentOrderId: "order-1" });

    await view.panel.render(view.container);

    const markup = view.elements.courierDelivery.innerHTML;
    assert.match(markup, /<button[^>]*id="confirmPickup"[^>]*disabled/);
    assert.match(markup, /id="confirmDelivery"/);
    assert.doesNotMatch(markup, /<button[^>]*id="confirmDelivery"[^>]*disabled/);
});
