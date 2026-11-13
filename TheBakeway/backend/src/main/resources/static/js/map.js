
const BakewayMap = (() => {
    let map = null;
    let infoWindow = null;

    const bakeryMarkers = new Map();
    const bakeryStatuses = new Map();
    const queueSizes = new Map();
    const courierMarkers = new Map();
    let fittedMarkerIds = null;

    

    const COLORS = {
        unknown: "#808080",
        short: "#20A464",
        medium: "#F59E0B",
        long: "#DC3545",
        courier: "#2563EB"
    };

    const COURIER_COLORS = {
        IDLE: "#2563EB",
        ASSIGNED: "#7C3AED",
        CARRYING: "#0F172A"
    };

    // R-DASH-03: short (green) up to 2 queued orders, building (amber) 3 to 5,
    // long (red) at 6 or more, or when the bakery is paused.
    const SHORT_QUEUE_MAX = 2;
    const MEDIUM_QUEUE_MAX = 5;

    function getQueueColor(queueSize) {
        if (queueSize <= SHORT_QUEUE_MAX) {
            return COLORS.short;
        }

        if (queueSize <= MEDIUM_QUEUE_MAX) {
            return COLORS.medium;
        }

        return COLORS.long;
    }

    function createBakeryIcon(color) {
        return {
            path: google.maps.SymbolPath.BACKWARD_CLOSED_ARROW,
            fillColor: color,
            fillOpacity: 1,
            strokeColor: "#FFFFFF",
            strokeWeight: 2,
            scale: 7
        };
    }

    // Courier markers: the colour shows the courier state (DASH core functionality)
    function createCourierIcon(status) {
        return {
            path: google.maps.SymbolPath.CIRCLE,
            fillColor: COURIER_COLORS[status] || COLORS.courier,
            fillOpacity: 1,
            strokeColor: "#FFFFFF",
            strokeWeight: 2,
            scale: 9
        };
    }

    function setBakeries(bakeries) {
        if (!map) return;

        const currentIds = new Set();

        bakeries.forEach(bakery => {
            const status = bakery.bakeryStatus?.toLowerCase();

            if (status !== "open" && status !== "paused" && status !== "closed") {
                console.warn("Unknown bakery status:", bakery.bakeryId, status);
                return;
            }

            bakeryStatuses.set(bakery.bakeryId, status);
            const { bakeryId, bakeryName, coordinates } = bakery;

            const lat = coordinates?.latitude;
            const lng = coordinates?.longitude;

            if (lat == null || lng == null ||
                !Number.isFinite(Number(lat)) ||
                !Number.isFinite(Number(lng))) {
                return;
            }

            currentIds.add(bakeryId);

            const position = {
                lat: Number(lat),
                lng: Number(lng)
            };

            let marker = bakeryMarkers.get(bakeryId);
            

            if (marker) {
                marker.setPosition(position);
                if (status === "paused") {
                    marker.setIcon(createBakeryIcon(COLORS.long));
                } else if (status === "closed") {
                    marker.setIcon(createBakeryIcon(COLORS.unknown));
                }
            } else {
                // Closed bakeries stay grey; open ones get their colour from the queue size
                marker = new google.maps.Marker({
                    position,
                    map,
                    icon: createBakeryIcon(status === "paused" ? COLORS.long : COLORS.unknown)
                });

                bakeryMarkers.set(bakeryId, marker);
            }

            //debug message
            console.log(
                "Bakery marker:",
                bakeryName,
                position
            );

            google.maps.event.clearListeners(marker, "click");

            marker.addListener("click", () => {
                // Label "Name · N queued" (DASH labels)
                const label = document.createElement("strong");
                label.textContent = `${bakeryName} · ${queueSizes.get(bakeryId) ?? 0} queued (${status})`;

                infoWindow.setContent(label);
                infoWindow.open({ anchor: marker, map });
            });
        });

        bakeryStatuses.forEach((status, id) => {
            if (!currentIds.has(id)) {
                bakeryStatuses.delete(id);
            }
        });

        // Remove bakeries no longer returned by the API
        bakeryMarkers.forEach((marker, id) => {
            if (!currentIds.has(id)) {
                marker.setMap(null);
                bakeryMarkers.delete(id);
                queueSizes.delete(id);
            }
        });
    }

    function updateBakeryQueue(bakeryId, queueSize) {
        const marker = bakeryMarkers.get(bakeryId);

        if (!marker) return;

        const size = Number(queueSize);

        if (!Number.isInteger(size) || size < 0) {
            console.warn("Invalid queue size:", bakeryId, queueSize);
            return;
        }

        queueSizes.set(bakeryId, size);

        const status = bakeryStatuses.get(bakeryId);

        // Paused bakeries are always red.
        if (status === "paused") {
            marker.setIcon(createBakeryIcon(COLORS.long));
            return;
        }

        if (status !== "open") return;

        marker.setIcon(
            createBakeryIcon(getQueueColor(size))
        );
    }

    function setCouriers(couriers) {
        if (!map) return;

        const currentIds = new Set();

        couriers.forEach(courier => {
            const { courierId, coordinates, status } = courier;

            const lat = coordinates?.latitude;
            const lng = coordinates?.longitude;

            if (
                lat == null || lng == null ||
                !Number.isFinite(Number(lat)) ||
                !Number.isFinite(Number(lng))
            ) {
                return;
            }

            currentIds.add(courierId);

            const position = {
                lat: Number(lat),
                lng: Number(lng)
            };

            let marker = courierMarkers.get(courierId);

            if (marker) {
                // Update the position and state colour of an existing courier
                marker.setPosition(position);
                marker.setIcon(createCourierIcon(status));
            } else {
                // Create a marker for a new courier
                marker = new google.maps.Marker({
                    position,
                    map,
                    icon: createCourierIcon(status),
                    zIndex: 100
                });

                courierMarkers.set(courierId, marker);
            }
            marker.setTitle(`${courierId} (${String(status || "").toLowerCase()})`);
        });

        // Remove couriers no longer returned by the API
        courierMarkers.forEach((marker, id) => {
            if (!currentIds.has(id)) {
                marker.setMap(null);
                courierMarkers.delete(id);
            }
        });
    }

    // R-DASH-02: scale the map to the bounds of the current dataset. The view is
    // fitted again only when markers are added or removed, so polling does not
    // reset the user's zoom. R-DASH-06: an empty dataset shows a message.
    function fitToData() {
        if (!map) return;

        const markers = [...bakeryMarkers.values(), ...courierMarkers.values()];
        if (markers.length === 0) {
            showMessage("No restaurants or on-duty couriers to show on the map.");
            fittedMarkerIds = null;
            return;
        }
        showMessage(null);

        const ids = [...bakeryMarkers.keys(), ...courierMarkers.keys()].sort().join("|");
        if (ids === fittedMarkerIds) return;
        fittedMarkerIds = ids;

        const bounds = new google.maps.LatLngBounds();
        markers.forEach(marker => bounds.extend(marker.getPosition()));
        if (markers.length === 1) {
            map.setCenter(bounds.getCenter());
            map.setZoom(15);
        } else {
            map.fitBounds(bounds, 40);
        }
    }

    function showMessage(text) {
        const message = document.getElementById("mapMessage");
        if (!message) return;
        message.textContent = text || "";
        message.hidden = !text;
    }

    // The Google Maps script loads asynchronously, so wait for it before giving up
    async function waitForGoogleMaps(timeoutMs) {
        const deadline = Date.now() + timeoutMs;
        while (!window.google?.maps?.Map && Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, 200));
        }
        return Boolean(window.google?.maps?.Map);
    }

    async function init(containerId = "map") {
        const element = document.getElementById(containerId);

        if (!element || !(await waitForGoogleMaps(10000))) {
            console.error("Map container or Google Maps API unavailable");
            if (element) element.hidden = true;
            showMessage("The map could not be loaded. Check the internet connection and reload the page.");
            return;
        }

        bakeryMarkers.forEach(marker => marker.setMap(null));
        bakeryMarkers.clear();

        courierMarkers.forEach(marker => marker.setMap(null));
        courierMarkers.clear();

        bakeryStatuses.clear();
        queueSizes.clear();
        fittedMarkerIds = null;

        map = new google.maps.Map(element, {
            center: { lat: 45.5017, lng: -73.5673 },
            zoom: 12,
            streetViewControl: false,
            mapTypeControl: true,

            styles: [
                {
                    featureType: "poi",
                    elementType: "labels",
                    stylers: [{ visibility: "off" }]
                },
                {
                    featureType: "transit.station",
                    elementType: "labels",
                    stylers: [{ visibility: "off" }]
                }
            ]
        });

        infoWindow = new google.maps.InfoWindow();
    }

    return {
        init,
        setBakeries,
        updateBakeryQueue,
        setCouriers,
        fitToData
    };
})();
