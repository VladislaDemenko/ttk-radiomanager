let currentUser = null;
let audioContext = null;
let gainNode = null;
let isPlaying = false;

document.addEventListener("DOMContentLoaded", async () => {

    const userJson = localStorage.getItem("currentUser");
    if (!userJson) {
        window.location.href = "/login";
        return;
    }

    currentUser = JSON.parse(userJson);

    await loadNavigation();

    initPlayer();

    initFeedbackForm();
});

async function loadNavigation() {
    const navLinks = document.getElementById("navLinks");
    const userInfo = document.getElementById("userInfo");

    const modules = [];

    modules.push({
        name: "Плеер",
        icon: "",
        path: "/player",
        active: true
    });

    if (currentUser.roles && (currentUser.roles.includes("ADMIN") || currentUser.roles.includes("BROADCASTER"))) {
        modules.push({
            name: "Раздел ведущего",
            icon: "",
            path: "/broadcaster",
            active: false
        });
    }

    if (currentUser.roles && currentUser.roles.includes("ADMIN")) {
        modules.push({
            name: "Администрирование",
            icon: "",
            path: "/admin",
            active: false
        });
    }

    navLinks.innerHTML = modules.map(module => `
        <a href="${module.path}" class="nav-item ${module.active ? 'active' : ''}">
            <span>${module.icon}</span>
            <span>${module.name}</span>
        </a>
    `).join('');

    userInfo.innerHTML = `
        <span class="user-name">${escapeHtml(currentUser.fullName)}</span>
        <button class="logout-btn" onclick="logout()">Выйти</button>
    `;
}

function initPlayer() {
    const audio = document.getElementById("audioPlayer");
    const playPauseBtn = document.getElementById("playPauseBtn");
    const volumeSlider = document.getElementById("volumeSlider");
    const volumeValue = document.getElementById("volumeValue");

    audioContext = new (window.AudioContext || window.webkitAudioContext)();
    gainNode = audioContext.createGain();
    const source = audioContext.createMediaElementSource(audio);
    source.connect(gainNode);
    gainNode.connect(audioContext.destination);

    gainNode.gain.value = parseFloat(volumeSlider.value);
    volumeValue.textContent = `${Math.round(gainNode.gain.value * 100)}%`;

    playPauseBtn.addEventListener("click", () => {
        if (isPlaying) {
            audio.pause();
            audioContext.suspend();
            playPauseBtn.innerHTML = `
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z"/>
                </svg>
            `;
            isPlaying = false;
        } else {
            audio.play();
            audioContext.resume();
            playPauseBtn.innerHTML = `
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
                </svg>
            `;
            isPlaying = true;
        }
    });

    volumeSlider.addEventListener("input", (e) => {
        const value = parseFloat(e.target.value);
        gainNode.gain.value = value;
        volumeValue.textContent = `${Math.round(value * 100)}%`;
    });

    audio.addEventListener("ended", () => {
        isPlaying = false;
        playPauseBtn.innerHTML = `
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z"/>
            </svg>
        `;
    });
}

function initFeedbackForm() {
    const form = document.getElementById("feedbackForm");
    const textarea = document.getElementById("messageContent");

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const content = textarea.value.trim();

        if (!content) {
            showToast("Введите сообщение", "error");
            return;
        }

        if (content.length > 500) {
            showToast("Сообщение не должно превышать 500 символов", "error");
            return;
        }

        try {
            const response = await fetch("/api/messages/send", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-User-Id": currentUser.id
                },
                body: JSON.stringify({ content })
            });

            const result = await response.json();

            if (response.ok) {
                showToast("Сообщение отправлено ведущему", "success");
                textarea.value = "";
            } else {
                showToast(result.message || "Ошибка при отправке", "error");
            }
        } catch (error) {
            console.error("Error sending message:", error);
            showToast("Ошибка соединения с сервером", "error");
        }
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(message, type) {
    const toast = document.createElement("div");
    toast.className = `message-toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 3000);
}

function logout() {
    localStorage.removeItem("currentUser");
    window.location.href = "/login";
}