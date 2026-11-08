// Courier panel: duty lifecycle and delivery-offer responses.
Dashboard.register("COURIER", (() => {
    const D = Dashboard;
    let container = null;

    async function render(target) {
        container = target;
        container.innerHTML = `
            <div class="grid-2">
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-person-biking"></i> Courier status</h2>
                    </div>
                    <div id="courierStatus"></div>
                </section>
                <section class="card">
                    <div class="card-title">
                        <h2><i class="fa-solid fa-bell"></i> Delivery offer</h2>
                    </div>
                    <div id="courierOffer"></div>
                </section>
            </div>
            <section class="card">
                <div class="card-title">
                    <h2><i class="fa-solid fa-box"></i> Current delivery</h2>
                </div>
                <div id="courierDelivery"></div>
            </section>`;
        await refresh();
    }

    async function refresh() {
        const result = await D.api("GET", "/api/couriers/me");
        if (!result.ok) {
            container.querySelector("#courierStatus").innerHTML =
                `<p class="empty">${D.escape(D.errorText(result))}</p>`;
            return;
        }
        renderCourier(result.data);
    }

    function renderCourier(courier) {
        const status = courier.status;
        const statusTarget = container.querySelector("#courierStatus");
        const offerTarget = container.querySelector("#courierOffer");
        const deliveryTarget = container.querySelector("#courierDelivery");

        let dutyButton = "";
        if (status === "OFFLINE") {
            dutyButton = `<button type="button" class="btn" id="goOnDuty">
                <i class="fa-solid fa-toggle-on"></i> Go on duty</button>`;
        } else if (status === "CARRYING") {
            dutyButton = `<button type="button" class="btn secondary" disabled>
                <i class="fa-solid fa-toggle-off"></i> Cannot go offline while carrying</button>`;
        } else {
            dutyButton = `<button type="button" class="btn secondary" id="goOffDuty">
                <i class="fa-solid fa-toggle-off"></i> Go off duty</button>`;
        }

        statusTarget.innerHTML = `
            <p>${D.badge(status.toLowerCase())}</p>
            ${courier.coordinates
                ? `<p class="muted"><i class="fa-solid fa-location-dot"></i>
                   ${D.escape(courier.coordinates.latitude)},
                   ${D.escape(courier.coordinates.longitude)}</p>`
                : ""}
            <div class="actions">${dutyButton}</div>`;

        statusTarget.querySelector("#goOnDuty")?.addEventListener(
            "click", () => D.command(
                "PATCH", "/api/couriers/me/on-duty", undefined,
                "You are now on duty and available for offers."));
        statusTarget.querySelector("#goOffDuty")?.addEventListener(
            "click", () => D.command(
                "PATCH", "/api/couriers/me/off-duty", undefined,
                "You are now off duty."));

        if (courier.activeOffer) {
            const offer = courier.activeOffer;
            offerTarget.innerHTML = `
                <p><strong>Order ${D.escape(D.shortId(offer.orderId))}</strong></p>
                <p class="muted">Offered ${D.escape(D.time(offer.offeredAt))}</p>
                <div class="actions">
                    <button type="button" class="btn" id="acceptOffer">
                        <i class="fa-solid fa-check"></i> Accept</button>
                    <button type="button" class="btn danger" id="declineOffer">
                        <i class="fa-solid fa-xmark"></i> Decline</button>
                </div>`;
            offerTarget.querySelector("#acceptOffer").addEventListener(
                "click", () => respondToOffer(offer.offerId, "accept"));
            offerTarget.querySelector("#declineOffer").addEventListener(
                "click", () => respondToOffer(offer.offerId, "decline"));
        } else {
            offerTarget.innerHTML = `<p class="empty">${
                status === "IDLE"
                    ? "Waiting for a delivery offer."
                    : "Go on duty and remain idle to receive offers."
            }</p>`;
        }

        if (courier.currentOrderId) {
            // The backend owns the delivery lifecycle rules. The dashboard only
            // exposes the command allowed by the courier's current persisted
            // state: ASSIGNED couriers pick up, while CARRYING couriers deliver.
            let deliveryAction = "";
            if (status === "ASSIGNED") {
                deliveryAction = `<button type="button" class="btn" id="confirmPickup">
                    <i class="fa-solid fa-box-open"></i> Confirm pickup</button>`;
            } else if (status === "CARRYING") {
                deliveryAction = `<button type="button" class="btn" id="confirmDelivery">
                    <i class="fa-solid fa-house-circle-check"></i> Mark delivered</button>`;
            }

            deliveryTarget.innerHTML = `
                <p><strong>Order ${D.escape(D.shortId(courier.currentOrderId))}</strong></p>
                <p class="muted">${
                    status === "CARRYING"
                        ? "Order picked up and in transit."
                        : "Assigned and waiting for pickup."
                }</p>
                <div class="actions">${deliveryAction}</div>`;

            // Reuse Dashboard.command so authentication cookies, error messages,
            // success banners, and the post-command dashboard refresh all remain
            // consistent with the existing courier and restaurant controls.
            deliveryTarget.querySelector("#confirmPickup")?.addEventListener(
                "click", () => updateDelivery(
                    courier.currentOrderId,
                    "pickup",
                    "Order picked up. Delivery is now in progress."));
            deliveryTarget.querySelector("#confirmDelivery")?.addEventListener(
                "click", () => updateDelivery(
                    courier.currentOrderId,
                    "deliver",
                    "Order marked as delivered."));
        } else {
            deliveryTarget.innerHTML =
                `<p class="empty">No delivery is currently assigned.</p>`;
        }
    }

    function respondToOffer(offerId, response) {
        const pastTense = response === "accept" ? "accepted" : "declined";
        return D.command(
            "PATCH",
            `/api/couriers/offers/${encodeURIComponent(offerId)}/${response}`,
            undefined,
            `Delivery offer ${pastTense}.`);
    }

    function updateDelivery(orderId, action, successMessage) {
        return D.command(
            "PATCH",
            `/api/orders/${encodeURIComponent(orderId)}/${action}`,
            undefined,
            successMessage);
    }

    return { render, refresh };
})());
