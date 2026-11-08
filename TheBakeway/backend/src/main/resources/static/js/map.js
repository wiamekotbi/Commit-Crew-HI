
const BakewayMap = (() => {
    let map = null;
    let infoWindow = null;

    const bakeryMarkers = new Map();
    const bakeryStatuses = new Map();
    const courierMarkers = new Map();

    

    const COLORS = {
        unknown: "#808080",
        short: "#20A464",
        medium: "#F59E0B",
        long: "#DC3545",
        courier: "#2563EB"
    };

    const SHORT_QUEUE_MAX = 3;
    const MEDIUM_QUEUE_MAX = 7;

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

    function createCourierIcon() {
        return {
            path: google.maps.SymbolPath.CIRCLE,
            fillColor: COLORS.courier,
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

            if (status === "closed") {
                return;
            }
            if (status !== "open" && status !== "paused") {
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
                }
            } else {
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
                const name = document.createElement("strong");
                name.textContent = bakeryName;

                infoWindow.setContent(name);
                infoWindow.open({ anchor: marker, map });
            });
        });

        bakeryStatuses.forEach((status, id) => {
            if (!currentIds.has(id)) {
                bakeryStatuses.delete(id);
            }
        });
    }

    function updateBakeryQueue(bakeryId, queueSize) {
        const marker = bakeryMarkers.get(bakeryId);

        if (!marker) return;

        const status = bakeryStatuses.get(bakeryId);

        // Paused bakeries are always red.
        if (status === "paused") {
            marker.setIcon(createBakeryIcon(COLORS.long));
            return;
        }

        if (status !== "open") return;

        const size = Number(queueSize);

        if (!Number.isInteger(size) || size < 0) {
            console.warn("Invalid queue size:", bakeryId, queueSize);
            return;
        }

        marker.setIcon(
            createBakeryIcon(getQueueColor(size))
        );
    }

        function setCouriers(couriers) {
        if (!map) return;

        const currentIds = new Set();

        couriers.forEach(courier => {
            const { courierId, coordinates } = courier;

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
                // Update the position of an existing courier
                marker.setPosition(position);
            } else {
                // Create a marker for a new courier
                marker = new google.maps.Marker({
                    position,
                    map,
                    icon: createCourierIcon(),
                    clickable: false,
                    zIndex: 100
                });

                courierMarkers.set(courierId, marker);
            }
        });

        // Remove couriers no longer returned by the API
        courierMarkers.forEach((marker, id) => {
            if (!currentIds.has(id)) {
                marker.setMap(null);
                courierMarkers.delete(id);
            }
        });
    }

    function init(containerId = "map") {
        const element = document.getElementById(containerId);

        if (!element || !window.google?.maps?.Map) {
            console.error("Map container or Google Maps API unavailable");
            return;
        }

        bakeryMarkers.forEach(marker => marker.setMap(null));
        bakeryMarkers.clear();

        courierMarkers.forEach(marker => marker.setMap(null));
        courierMarkers.clear();

        bakeryStatuses.clear();

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

        const MONTREAL_BOUNDS = {
            north: 45.72,
            south: 45.40,
            west: -73.99,
            east: -73.47
        };

        map.setOptions({
            restriction: {
                latLngBounds: MONTREAL_BOUNDS,
                strictBounds: true
            },
            minZoom: 5,
            maxZoom: 19
        });

        console.log("Restriction applied:", map.get("restriction"));

        infoWindow = new google.maps.InfoWindow();
    }

    return {
        init,
        setBakeries,
        updateBakeryQueue,
        setCouriers
    };
})();
