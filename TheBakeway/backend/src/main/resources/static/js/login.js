const form = document.getElementById("loginForm");
const message = document.getElementById("message");

form.addEventListener("submit", async function (event) {
    event.preventDefault();
    message.textContent = "";

    const response = await fetch("/api/login", {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            username: document.getElementById("username").value,
            password: document.getElementById("password").value
        })
    });

    const result = await response.json();

    if (response.ok) {
        // Every role lands on the dashboard, which shows what that role can do (UC-REG-02)
        window.location.href = "/dashboard.html";
    } else {
        message.textContent = result.error;
    }
});
