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

function dashboard(deliveries) {
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
                return { ok: true, data: { status: "OFFLINE" } };
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

test("courier dashboard loads and renders past delivery ledger entries", async () => {
    const view = dashboard([{
        orderId: "order-12345678",
        deliveredAt: "2026-11-07T12:00:00Z",
        ledgerEntries: [{
            entryType: "CHARGE",
            occurredAt: "2026-11-07T12:00:00Z",
            amount: 17,
            currency: "CAD",
            pricing: {
                subtotal: 8,
                deliveryFee: 4,
                serviceFee: 2,
                smallOrderSurcharge: 3
            }
        }]
    }]);

    await view.panel.render(view.container);

    assert.deepEqual(view.calls.map(call => call.url), [
        "/api/couriers/me",
        "/api/couriers/me/past-deliveries"
    ]);
    assert.match(view.elements.courierPastDeliveries.innerHTML, /order-12/);
    assert.match(view.elements.courierPastDeliveries.innerHTML, /CHARGE/);
    assert.match(view.elements.courierPastDeliveries.innerHTML, /\$17\.00 CAD/);
    assert.match(view.elements.courierPastDeliveries.innerHTML, /Delivery: \$4\.00/);
});

test("courier dashboard replaces loading message when history is empty", async () => {
    const view = dashboard([]);

    await view.panel.render(view.container);

    assert.match(view.elements.courierPastDeliveries.innerHTML, /no past deliveries yet/i);
});
