const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const vm = require("node:vm");

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
            },
            renderCart
        }
    };`
);

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
    vm.runInNewContext(customerScript, { Dashboard });

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
    assert.doesNotMatch(markup, /<button[^>]*id="placeOrder"[^>]*disabled/);
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
