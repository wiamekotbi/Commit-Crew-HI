const params = new URLSearchParams(window.location.search);
const accountId = params.get("accountId");

const form = document.getElementById("profileForm");
const message = document.getElementById("message");
const logoutButton = document.getElementById("logoutButton");

async function loadProfile() {
    if (!accountId) {
        message.textContent = "No account selected.";
        return;
    }

    const response = await fetch("/api/profiles/" + accountId);
    const profile = await response.json();

    if (!response.ok) {
        message.textContent = profile.error;
        return;
    }

    document.getElementById("accountId").value = profile.accountId;
    document.getElementById("username").value = profile.username;
    document.getElementById("role").value = profile.role;
    document.getElementById("displayName").value = profile.displayName;
    document.getElementById("contactEmail").value = profile.contactEmail || "";
    document.getElementById("deliveryAddress").value = profile.deliveryAddress || "";
}

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const data = {
        displayName: document.getElementById("displayName").value,
        contactEmail: document.getElementById("contactEmail").value,
        deliveryAddress: document.getElementById("deliveryAddress").value
    };

    const response = await fetch("/api/profiles/" + accountId, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    });

    const result = await response.json();

    if (response.ok) {
        message.textContent = "Profile updated successfully!";
    } else {
        message.textContent = result.error;
    }
});

logoutButton.addEventListener("click", async function () {
    await fetch("/api/logout", {
        method: "POST",
        credentials: "same-origin"
    });
    window.location.href = "/login.html";
});

loadProfile();
