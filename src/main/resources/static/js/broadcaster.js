let currentUser = null;
let currentFilter = 'unreplied';
let currentReplyMessageId = null;
let refreshInterval = null;
let broadcastStartTime = null;
let timeInterval = null;

document.addEventListener("DOMContentLoaded", async () => {
    const userJson = localStorage.getItem("currentUser");
    if (!userJson) {
        window.location.href = "/login";
        return;
    }

    currentUser = JSON.parse(userJson);
    console.log('Broadcaster user:', currentUser);

    if (!currentUser.roles || (!currentUser.roles.includes("BROADCASTER") && !currentUser.roles.includes("ADMIN"))) {
        window.location.href = "/player";
        return;
    }

    await loadNavigation();
    await loadBroadcastInfo();
    await loadMessages();

    refreshInterval = setInterval(() => {
        loadBroadcastInfo();
        if (currentFilter === 'unreplied') {
            loadMessages();
        }
    }, 5000);
});

async function loadNavigation() {
    const navLinks = document.getElementById("navLinks");
    const userInfo = document.getElementById("userInfo");

    const modules = [
        { name: "Плеер", path: "/player", active: false },
        { name: "Раздел ведущего", path: "/broadcaster", active: true }
    ];

    if (currentUser.roles && currentUser.roles.includes("ADMIN")) {
        modules.push({ name: "Администрирование", path: "/admin", active: false });
    }

    navLinks.innerHTML = modules.map(module => `
        <a href="${module.path}" class="nav-item ${module.active ? 'active' : ''}">
            <span>${module.name === 'Плеер' ? '🎵' : module.name === 'Раздел ведущего' ? '🎙️' : '⚙️'}</span>
            <span>${module.name}</span>
        </a>
    `).join('');

    userInfo.innerHTML = `
        <span class="user-name">${escapeHtml(currentUser.fullName)}</span>
        <button class="logout-btn" onclick="logout()">Выйти</button>
    `;
}

async function loadBroadcastInfo() {
    try {
        const response = await fetch('/api/broadcaster/info');
        if (response.ok) {
            const info = await response.json();
            updateBroadcastUI(info);
        }
    } catch (error) {
        console.error('Error loading broadcast info:', error);
    }
}

function updateBroadcastUI(info) {
    const statusBadge = document.getElementById('broadcastStatusBadge');
    const startBtn = document.getElementById('startBtn');
    const stopBtn = document.getElementById('stopBtn');
    const trackTitle = document.getElementById('currentTrackTitle');
    const trackArtist = document.getElementById('currentTrackArtist');
    const listenerCount = document.getElementById('listenerCount');

    if (info.live) {
        statusBadge.innerHTML = `
            <span class="status-indicator online"></span>
            <span>Эфир активен</span>
        `;
        if (startBtn) startBtn.disabled = true;
        if (stopBtn) stopBtn.disabled = false;
        
        if (!broadcastStartTime && info.startedAt) {
            broadcastStartTime = new Date(info.startedAt);
            startTimeCounter();
        }
    } else {
        statusBadge.innerHTML = `
            <span class="status-indicator offline"></span>
            <span>Эфир остановлен</span>
        `;
        if (startBtn) startBtn.disabled = false;
        if (stopBtn) stopBtn.disabled = true;
        
        if (timeInterval) {
            clearInterval(timeInterval);
            timeInterval = null;
        }
        broadcastStartTime = null;
        const broadcastTimeElem = document.getElementById('broadcastTime');
        if (broadcastTimeElem) broadcastTimeElem.textContent = '00:00:00';
    }

    if (trackTitle) trackTitle.textContent = info.currentTrack || '—';
    if (trackArtist) trackArtist.textContent = info.currentArtist || '—';
    if (listenerCount) listenerCount.textContent = info.listenersCount || 0;
}

function startTimeCounter() {
    if (timeInterval) clearInterval(timeInterval);
    
    timeInterval = setInterval(() => {
        if (broadcastStartTime) {
            const now = new Date();
            const diff = now - broadcastStartTime;
            const hours = Math.floor(diff / 3600000);
            const minutes = Math.floor((diff % 3600000) / 60000);
            const seconds = Math.floor((diff % 60000) / 1000);
            const broadcastTimeElem = document.getElementById('broadcastTime');
            if (broadcastTimeElem) {
                broadcastTimeElem.textContent = 
                    `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
            }
        }
    }, 1000);
}

async function startBroadcast() {
    try {
        const response = await fetch('/api/broadcaster/start', { method: 'POST' });
        if (response.ok) {
            showToast('Эфир запущен', 'success');
            await loadBroadcastInfo();
        } else {
            showToast('Ошибка запуска эфира', 'error');
        }
    } catch (error) {
        showToast('Ошибка соединения с сервером', 'error');
    }
}

async function stopBroadcast() {
    try {
        const response = await fetch('/api/broadcaster/stop', { method: 'POST' });
        if (response.ok) {
            showToast('Эфир остановлен', 'success');
            await loadBroadcastInfo();
        } else {
            showToast('Ошибка остановки эфира', 'error');
        }
    } catch (error) {
        showToast('Ошибка соединения с сервером', 'error');
    }
}

async function updateTrackInfo() {
    const artist = document.getElementById('trackArtistInput')?.value.trim() || '';
    const track = document.getElementById('trackNameInput')?.value.trim() || '';

    if (!artist && !track) {
        showToast('Заполните хотя бы одно поле', 'error');
        return;
    }

    try {
        const response = await fetch('/api/broadcaster/track', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ artist, track })
        });
        
        if (response.ok) {
            showToast('Информация о треке обновлена', 'success');
            if (document.getElementById('trackArtistInput')) document.getElementById('trackArtistInput').value = '';
            if (document.getElementById('trackNameInput')) document.getElementById('trackNameInput').value = '';
            await loadBroadcastInfo();
        } else {
            showToast('Ошибка обновления трека', 'error');
        }
    } catch (error) {
        showToast('Ошибка соединения с сервером', 'error');
    }
}

function uploadAudio() {
    console.log('uploadAudio function called');

    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = '.mp3,.wav,.ogg';
    fileInput.style.position = 'absolute';
    fileInput.style.top = '-100px';
    fileInput.style.left = '-100px';
    fileInput.style.opacity = '0';
    
    fileInput.onchange = async (e) => {
        const file = e.target.files[0];
        if (!file) {
            document.body.removeChild(fileInput);
            return;
        }
        
        console.log('Selected file:', file.name, 'size:', file.size, 'type:', file.type);

        if (file.size > 50 * 1024 * 1024) {
            showToast('Файл превышает 50 МБ', 'error');
            document.body.removeChild(fileInput);
            return;
        }

        const fileExtension = file.name.split('.').pop().toLowerCase();
        if (!['mp3', 'wav', 'ogg'].includes(fileExtension)) {
            showToast('Неподдерживаемый формат. Используйте MP3, WAV или OGG', 'error');
            document.body.removeChild(fileInput);
            return;
        }

        let trackName = file.name.replace(/\.[^/.]+$/, "");
        const customName = prompt('Введите название трека:', trackName);
        if (customName !== null && customName.trim() !== '') {
            trackName = customName.trim();
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('trackName', trackName);
        
        showToast('Загрузка файла...', 'info');
        
        try {
            const response = await fetch('/api/broadcaster/upload-audio', {
                method: 'POST',
                body: formData
            });
            
            const result = await response.json();
            console.log('Upload response:', result);
            
            if (response.ok) {
                showToast(result.message || 'Трек успешно загружен!', 'success');

                setTimeout(() => {
                    loadBroadcastInfo();
                }, 500);
            } else {
                showToast(result.message || 'Ошибка при загрузке файла', 'error');
            }
        } catch (error) {
            console.error('Error uploading audio:', error);
            showToast('Ошибка соединения с сервером', 'error');
        }

        document.body.removeChild(fileInput);
    };
    
    document.body.appendChild(fileInput);
    fileInput.click();
}

async function loadMessages() {
    let url = '/api/broadcaster/messages/';
    
    if (currentFilter === 'unreplied') {
        url += 'unreplied';
    } else if (currentFilter === 'replied') {
        url += 'replied';
    } else {
        url += 'all';
    }
    
    try {
        const response = await fetch(url);
        if (response.ok) {
            const messages = await response.json();
            renderMessages(messages);
        }
    } catch (error) {
        console.error('Error loading messages:', error);
        const container = document.getElementById('messagesContainer');
        if (container) {
            container.innerHTML = '<div class="empty-state">Ошибка загрузки сообщений</div>';
        }
    }
}

function renderMessages(messages) {
    const container = document.getElementById('messagesContainer');
    if (!container) return;
    
    if (!messages || messages.length === 0) {
        container.innerHTML = '<div class="empty-state">📭 Нет сообщений</div>';
        return;
    }
    
    container.innerHTML = messages.map(message => `
        <div class="message-item ${!message.read ? 'unread' : ''}">
            <div class="message-header">
                <div class="message-sender-info">
                    <div class="sender-avatar">${getInitials(message.userFullName)}</div>
                    <div class="sender-name">${escapeHtml(message.userFullName)}</div>
                </div>
                <div class="message-time">${formatDateTime(message.sentAt)}</div>
            </div>
            <div class="message-text">${escapeHtml(message.content)}</div>
            <div class="message-actions">
                ${!message.read ? `
                    <button class="message-btn read" onclick="markAsRead(${message.id})">
                        ✓ Прочитано
                    </button>
                ` : ''}
                ${!message.reply ? `
                    <button class="message-btn reply" onclick="openReplyModal(${message.id}, '${escapeHtml(message.userFullName)}', '${escapeHtml(message.content).replace(/'/g, "\\'")}', '${message.sentAt}')">
                        ✎ Ответить
                    </button>
                ` : ''}
            </div>
            ${message.reply ? `
                <div class="message-reply">
                    <div class="reply-label">📨 Ответ ведущего:</div>
                    <div class="reply-text">${escapeHtml(message.reply)}</div>
                    <div class="reply-meta">
                        ${message.repliedAt ? formatDateTime(message.repliedAt) : ''}
                        ${message.repliedBy ? ` • ${escapeHtml(message.repliedBy)}` : ''}
                    </div>
                </div>
            ` : ''}
        </div>
    `).join('');
}

function getInitials(fullName) {
    if (!fullName) return '?';
    const parts = fullName.trim().split(/\s+/);
    if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return fullName[0].toUpperCase();
}

function formatDateTime(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;
    
    if (diff < 60000) return 'Только что';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} мин назад`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} ч назад`;
    
    return date.toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

async function markAsRead(messageId) {
    try {
        const response = await fetch(`/api/broadcaster/messages/${messageId}/read`, { method: 'POST' });
        if (response.ok) {
            showToast('Сообщение отмечено как прочитанное', 'success');
            await loadMessages();
        } else {
            showToast('Ошибка при отметке сообщения', 'error');
        }
    } catch (error) {
        showToast('Ошибка соединения с сервером', 'error');
    }
}

function openReplyModal(messageId, userName, messageContent, sentAt) {
    currentReplyMessageId = messageId;
    const senderNameElem = document.getElementById('replySenderName');
    const senderDateElem = document.getElementById('replySenderDate');
    const messageTextElem = document.getElementById('replyMessageText');
    const replyTextArea = document.getElementById('replyTextArea');
    const replyError = document.getElementById('replyError');
    
    if (senderNameElem) senderNameElem.textContent = userName;
    if (senderDateElem) senderDateElem.textContent = formatDateTime(sentAt);
    if (messageTextElem) messageTextElem.textContent = messageContent;
    if (replyTextArea) replyTextArea.value = '';
    if (replyError) replyError.textContent = '';
    
    const modal = document.getElementById('replyModal');
    if (modal) modal.classList.add('active');
}

async function sendReply() {
    const replyTextArea = document.getElementById('replyTextArea');
    const replyText = replyTextArea ? replyTextArea.value.trim() : '';
    
    if (!replyText) {
        const replyError = document.getElementById('replyError');
        if (replyError) replyError.textContent = 'Введите текст ответа';
        return;
    }
    
    try {
        const response = await fetch('/api/broadcaster/messages/reply', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': currentUser.id
            },
            body: JSON.stringify({
                messageId: currentReplyMessageId,
                reply: replyText
            })
        });
        
        if (response.ok) {
            closeReplyModal();
            showToast('Ответ отправлен', 'success');
            await loadMessages();
        } else {
            const result = await response.json();
            showToast(result.message || 'Ошибка при отправке ответа', 'error');
        }
    } catch (error) {
        showToast('Ошибка соединения с сервером', 'error');
    }
}

function filterMessages(filter, button) {
    currentFilter = filter;
    
    document.querySelectorAll('.filter-chip').forEach(btn => {
        btn.classList.remove('active');
    });
    if (button) button.classList.add('active');
    
    loadMessages();
}

function closeReplyModal() {
    const modal = document.getElementById('replyModal');
    if (modal) modal.classList.remove('active');
    currentReplyMessageId = null;
}

function closeUploadModal() {
    const modal = document.getElementById('uploadModal');
    if (modal) modal.classList.remove('active');
}

function showToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function logout() {
    if (refreshInterval) clearInterval(refreshInterval);
    if (timeInterval) clearInterval(timeInterval);
    localStorage.removeItem('currentUser');
    window.location.href = '/login';
}

document.querySelectorAll('.modal').forEach(modal => {
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
});