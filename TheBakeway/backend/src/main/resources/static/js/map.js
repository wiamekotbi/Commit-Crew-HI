const BakewayMap = (() => {
    let map = null;

    async function init(containerId = "map") {
        const element = document.getElementById(containerId);

        if (!element) {
            console.error("Map container not found:", containerId);
            return;
        }

        // Wait for Google Maps API
        while (!window.google?.maps?.Map) {
            await new Promise(resolve => setTimeout(resolve, 100));
        }

        const location = {
            lat: 45.5017,
            lng: -73.5673
        };

        map = new google.maps.Map(element, {
            center: location,
            zoom: 12,
            mapTypeControl: true,
            streetViewControl: false,
            fullscreenControl: true
        });

        new google.maps.Marker({
            position: location,
            map: map,
            title: "The Bakeway"
        });
    }

    return { init };
