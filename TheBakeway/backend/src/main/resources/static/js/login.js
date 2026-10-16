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
        window.location.href = "/profile.html?accountId=" + encodeURIComponent(result.accountId);
    } else {
        message.textContent = result.error;
    }
});
