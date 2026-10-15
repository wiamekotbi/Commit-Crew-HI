const form = document.getElementById("registrationForm");
const message = document.getElementById("message");

form.addEventListener("submit", async function (event) {
    event.preventDefault();

    const data = {
        name: document.getElementById("name").value,
        username: document.getElementById("username").value,
        password: document.getElementById("password").value,
        confirmPassword: document.getElementById("confirmPassword").value,
        deliveryAddress: document.getElementById("deliveryAddress").value
    };

    const response = await fetch("/api/register", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    });

    const result = await response.json();

    if (response.ok) {
        message.textContent = "Account created successfully. ID: " + result.accountId;
        form.reset();
    } else {
        message.textContent = result.error;
    }
});