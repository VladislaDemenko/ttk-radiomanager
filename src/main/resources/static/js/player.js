let currentUser = null;
let stompClient = null;
let audioContext = null;
let gainNode = null;
let isPlaying = false;
let audioBufferQueue = [];

document.addEventListener("DOMContentLoaded", async () => {
    const userJson = localStorage.getItem("currentUser");
    if (!userJson) {
        window.location.href = "/login";
        return;
    }

    currentUser = JSON.parse(userJson);
    console.log('User loaded:', currentUser);

    await loadNavigation();
    initPlayer();
    initFeedbackForm();
    connectWebSocket();
});

async function loadNavigation() {
    const navLinks = document.getElementById("navLinks");
    const userInfo = document.getElementById("userInfo");

    const modules = [
        { name: "Плеер", icon: "🎵", path: "/player", active: true }
    ];

    if (currentUser.roles && (currentUser.roles.includes("ADMIN") || currentUser.roles.includes("BROADCASTER"))) {
        modules.push({ name: "Раздел ведущего", icon: "🎙️", path: "/broadcaster", active: false });
    }

    if (currentUser.roles && currentUser.roles.includes("ADMIN")) {
        modules.push({ name: "Администрирование", icon: "⚙️", path: "/admin", active: false });
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
    const playPauseBtn = document.getElementById("playPauseBtn");
    const volumeSlider = document.getElementById("volumeSlider");
    const volumeValue = document.getElementById("volumeValue");

    audioContext = new (window.AudioContext || window.webkitAudioContext)();
    gainNode = audioContext.createGain();
    gainNode.connect(audioContext.destination);
    gainNode.gain.value = parseFloat(volumeSlider.value);
    volumeValue.textContent = `${Math.round(gainNode.gain.value * 100)}%`;

    playPauseBtn.addEventListener("click", async () => {
        if (audioContext.state === 'suspended') {
            await audioContext.resume();
        }

        if (isPlaying) {
            gainNode.disconnect();
            playPauseBtn.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>';
            isPlaying = false;
            updateConnectionStatus('paused', 'Пауза');
        } else {
            gainNode.connect(audioContext.destination);
            playPauseBtn.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';
            isPlaying = true;
            updateConnectionStatus('connected', 'Воспроизведение');

            // Если есть очередь, начинаем воспроизведение
            if (audioBufferQueue.length > 0) {
                processQueue();
            }
        }
    });

    volumeSlider.addEventListener("input", (e) => {
        const value = parseFloat(e.target.value);
        gainNode.gain.value = value;
        volumeValue.textContent = `${Math.round(value * 100)}%`;
    });
}

function connectWebSocket() {
    console.log('Connecting to WebSocket...');
    updateConnectionStatus('connecting', 'Подключение...');

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = (str) => {
        console.log('STOMP:', str);
    };

    stompClient.connect({},
        // Success callback
        (frame) => {
            console.log('WebSocket connected!', frame);
            updateConnectionStatus('connected', 'Подключено к эфиру');

            // Подписываемся на аудиострим
            stompClient.subscribe('/topic/stream', (message) => {
                console.log('Received audio chunk');
                const chunk = JSON.parse(message.body);
                handleAudioChunk(chunk);
            });

            // Подписываемся на информацию о треке
            stompClient.subscribe('/topic/track-info', (message) => {
                const info = JSON.parse(message.body);
                console.log('Track info:', info);
                document.getElementById('currentTrackName').textContent = info.track;
                document.getElementById('currentTrackArtist').textContent = info.artist || 'Сейчас в эфире';
            });

            // Регистрируем слушателя
            stompClient.send("/app/stream/listener", {}, JSON.stringify({
                action: "add",
                userId: currentUser.id
            }));

            // Проверяем статус стрима
            checkStreamStatus();
        },
        // Error callback
        (error) => {
            console.error('WebSocket connection error:', error);
            updateConnectionStatus('error', 'Ошибка подключения');
            setTimeout(connectWebSocket, 5000);
        }
    );
}

function checkStreamStatus() {
    fetch('/api/broadcaster/stream-status')
        .then(response => response.json())
        .then(status => {
            console.log('Stream status:', status);
            if (status.isStreaming) {
                document.getElementById('currentTrackName').textContent = status.currentTrack;
                updateConnectionStatus('connected', 'Стрим активен');
            } else {
                updateConnectionStatus('waiting', 'Ожидание начала трансляции');
            }
        })
        .catch(error => {
            console.error('Error checking stream status:', error);
        });
}

function handleAudioChunk(chunk) {
    console.log('Processing chunk:', chunk.chunkNumber, 'last:', chunk.last);

    if (chunk.last) {
        console.log('Track finished');
        return;
    }

    if (!chunk.data || chunk.data === '') {
        return;
    }

    try {
        // Декодируем base64
        const binaryString = atob(chunk.data);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }

        audioContext.decodeAudioData(bytes.buffer, (buffer) => {
            console.log('Audio decoded successfully, duration:', buffer.duration);
            audioBufferQueue.push(buffer);

            if (isPlaying && audioBufferQueue.length === 1) {
                processQueue();
            }
        }, (error) => {
            console.error('Error decoding audio:', error);
        });
    } catch (error) {
        console.error('Error processing chunk:', error);
    }
}

let isProcessing = false;
let currentSource = null;

function processQueue() {
    if (!isPlaying || audioBufferQueue.length === 0 || isProcessing) {
        return;
    }

    isProcessing = true;
    const buffer = audioBufferQueue.shift();

    currentSource = audioContext.createBufferSource();
    currentSource.buffer = buffer;
    currentSource.connect(gainNode);
    currentSource.onended = () => {
        console.log('Buffer finished playing');
        currentSource = null;
        isProcessing = false;

        if (audioBufferQueue.length > 0 && isPlaying) {
            processQueue();
        }
    };

    currentSource.start();
    console.log('Started playing buffer');
}

function updateConnectionStatus(status, message) {
    const statusElement = document.getElementById('connectionStatus');
    if (statusElement) {
        let dotClass = '';
        if (status === 'connected') dotClass = 'connected';
        else if (status === 'error') dotClass = 'error';
        else if (status === 'connecting') dotClass = 'connecting';
        else dotClass = 'waiting';

        statusElement.innerHTML = `<span class="status-dot ${dotClass}"></span><span>${message}</span>`;
    }
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

        try {
            const response = await fetch("/api/messages/send", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-User-Id": currentUser.id
                },
                body: JSON.stringify({ content })
            });

            if (response.ok) {
                showToast("Сообщение отправлено", "success");
                textarea.value = "";
            } else {
                showToast("Ошибка при отправке", "error");
            }
        } catch (error) {
            console.error('Error sending message:', error);
            showToast("Ошибка соединения", "error");
        }
    });
}

function showToast(message, type) {
    const toast = document.createElement("div");
    toast.className = `message-toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function logout() {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/stream/listener", {}, JSON.stringify({
            action: "remove",
            userId: currentUser.id
        }));
        stompClient.disconnect();
    }
    localStorage.removeItem("currentUser");
    window.location.href = "/login";
}