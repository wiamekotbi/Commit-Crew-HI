
const BakewayMap = (() => {
    let map = null;
    let infoWindow = null;

    const bakeryMarkers = new Map();

    const COLORS = {
        unknown: "#808080",
        short: "#20A464",
        medium: "#F59E0B",
        long: "#DC3545",
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

        };

    function setBakeries(bakeries) {
        if (!map) return;

        const currentIds = new Set();

        bakeries.forEach(bakery => {
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
                marker.setIcon(createBakeryIcon(COLORS.unknown));
            } else {
                marker = new google.maps.Marker({
                    position,
                    map,
                    icon: createBakeryIcon(COLORS.unknown)
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

        // Remove markers for bakeries no longer returned.
        bakeryMarkers.forEach((marker, id) => {
            if (!currentIds.has(id)) {
                marker.setMap(null);
                bakeryMarkers.delete(id);
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

        const color = getQueueColor(size);

        marker.setIcon(createBakeryIcon(color));
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
        updateBakeryQueue
    };
})();
