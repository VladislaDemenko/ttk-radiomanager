let currentUser = null;
let currentFilter = 'unreplied';
let currentReplyMessageId = null;
let refreshInterval = null;
let broadcastStartTime = null;
let timeInterval = null;
let currentPlaylistId = null;
let currentPlaylistItems = [];
let playbackStompClient = null;

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
    await loadMediaLibrary();
    await loadPlaylists();
    await loadPlaybackStatus();
    initPlaybackWebSocket();

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

async function loadPlaybackStatus() {
    try {
        const response = await fetch('/api/broadcaster/playback/status');
        if (response.ok) {
            const status = await response.json();
            updatePlaybackUI(status);
        }
    } catch (error) {
        console.error('Error loading playback status:', error);
    }
}

function initPlaybackWebSocket() {
    if (playbackStompClient && playbackStompClient.connected) {
        return;
    }

    console.log('Initializing playback WebSocket...');
    const socket = new SockJS('/ws');
    playbackStompClient = Stomp.over(socket);
    playbackStompClient.debug = () => {};

    playbackStompClient.connect({},
        () => {
            console.log('Playback WebSocket connected');

            playbackStompClient.subscribe('/topic/playback-status', (message) => {
                const status = JSON.parse(message.body);
                updatePlaybackUI(status);
            });
        },
        (error) => {
            console.error('Playback WebSocket error:', error);
            setTimeout(initPlaybackWebSocket, 5000);
        }
    );
}

function updatePlaybackUI(status) {
    const statusText = document.getElementById('playbackStatus');
    if (statusText) {
        statusText.textContent = status.statusText;
    }
}

async function nextTrack() {
    try {
        const response = await fetch('/api/broadcaster/playback/next', {
            method: 'POST',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            showToast('Следующий трек', 'success');
            await loadPlaybackStatus();
        }
    } catch (error) {
        console.error('Error next track:', error);
        showToast('Ошибка при переключении', 'error');
    }
}

async function previousTrack() {
    try {
        const response = await fetch('/api/broadcaster/playback/prev', {
            method: 'POST',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            showToast('Предыдущий трек', 'success');
            await loadPlaybackStatus();
        }
    } catch (error) {
        console.error('Error previous track:', error);
        showToast('Ошибка при переключении', 'error');
    }
}

async function loadMediaLibrary() {
    try {
        const response = await fetch('/api/broadcaster/media', {
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const files = await response.json();
            renderMediaLibrary(files);
        } else {
            console.error('Failed to load media library');
        }
    } catch (error) {
        console.error('Error loading media library:', error);
    }
}

function renderMediaLibrary(files) {
    const container = document.getElementById('mediaLibrary');
    if (!container) return;

    if (!files || files.length === 0) {
        container.innerHTML = '<div class="empty-state">📁 Нет загруженных файлов<br><small>Нажмите + чтобы загрузить</small></div>';
        return;
    }

    container.innerHTML = files.map(file => `
        <div class="media-item" data-id="${file.id}">
            <div class="media-item-info">
                <div class="media-item-name">${escapeHtml(file.fileName)}</div>
                <div class="media-item-meta">${formatFileSize(file.fileSize)}</div>
            </div>
            <div class="media-item-actions">
                <button class="btn-icon" onclick="addToCurrentPlaylist(${file.id}, '${escapeHtml(file.fileName)}')" title="Добавить в плейлист">➕</button>
                <button class="btn-icon" onclick="deleteAudioFile(${file.id})" title="Удалить">🗑️</button>
            </div>
        </div>
    `).join('');
}

function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

async function deleteAudioFile(fileId) {
    if (!confirm('Удалить этот файл?')) return;

    try {
        const response = await fetch(`/api/broadcaster/media/${fileId}`, {
            method: 'DELETE',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            showToast('Файл удален', 'success');
            await loadMediaLibrary();
        } else {
            showToast('Ошибка при удалении', 'error');
        }
    } catch (error) {
        console.error('Error deleting file:', error);
        showToast('Ошибка соединения', 'error');
    }
}

async function loadPlaylists() {
    try {
        const response = await fetch('/api/broadcaster/playlists', {
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const playlists = await response.json();
            renderPlaylists(playlists);
        } else {
            console.error('Failed to load playlists');
        }
    } catch (error) {
        console.error('Error loading playlists:', error);
    }
}

function renderPlaylists(playlists) {
    const container = document.getElementById('playlistsContainer');
    if (!container) return;

    if (!playlists || playlists.length === 0) {
        container.innerHTML = '<div class="empty-state">📋 Нет плейлистов<br><small>Нажмите + чтобы создать</small></div>';
        return;
    }

    container.innerHTML = playlists.map(playlist => `
        <div class="playlist-card ${playlist.active ? 'active' : ''}" onclick="openPlaylist(${playlist.id})">
            <div class="playlist-card-header">
                <div class="playlist-card-name">${escapeHtml(playlist.name)}</div>
                ${playlist.active ? '<span class="badge active">Активен</span>' : ''}
            </div>
            <div class="playlist-card-info">
                <span>${playlist.items?.length || 0} треков</span>
                ${playlist.looping ? '<span>🔁</span>' : ''}
                ${playlist.shuffling ? '<span>🔀</span>' : ''}
            </div>
        </div>
    `).join('');
}

function createNewPlaylist() {
    document.getElementById('playlistName').value = '';
    document.getElementById('createPlaylistModal').classList.add('active');
}

async function saveNewPlaylist() {
    const name = document.getElementById('playlistName').value.trim();
    if (!name) {
        showToast('Введите название плейлиста', 'error');
        return;
    }

    try {
        const response = await fetch('/api/broadcaster/playlists', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': currentUser.id
            },
            body: JSON.stringify({ name })
        });

        if (response.ok) {
            closeCreatePlaylistModal();
            showToast('Плейлист создан', 'success');
            await loadPlaylists();
        } else {
            showToast('Ошибка при создании', 'error');
        }
    } catch (error) {
        console.error('Error creating playlist:', error);
        showToast('Ошибка соединения', 'error');
    }
}

async function openPlaylist(playlistId) {
    currentPlaylistId = playlistId;

    try {
        const response = await fetch(`/api/broadcaster/playlists`, {
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const playlists = await response.json();
            const playlist = playlists.find(p => p.id === playlistId);

            if (playlist) {
                currentPlaylistItems = playlist.items || [];
                document.getElementById('playlistModalTitle').textContent = playlist.name;

                const loopBtn = document.getElementById('playlistLoopBtn');
                const shuffleBtn = document.getElementById('playlistShuffleBtn');

                if (playlist.looping) {
                    loopBtn.classList.add('active');
                } else {
                    loopBtn.classList.remove('active');
                }

                if (playlist.shuffling) {
                    shuffleBtn.classList.add('active');
                } else {
                    shuffleBtn.classList.remove('active');
                }

                renderPlaylistItems();
                await loadTracksForSelect();
                document.getElementById('playlistModal').classList.add('active');
            }
        }
    } catch (error) {
        console.error('Error opening playlist:', error);
    }
}

function renderPlaylistItems() {
    const container = document.getElementById('playlistItemsContainer');
    if (!container) return;

    if (!currentPlaylistItems || currentPlaylistItems.length === 0) {
        container.innerHTML = '<div class="empty-state"> Плейлист пуст<br><small>Добавьте треки из медиатеки</small></div>';
        return;
    }

    container.innerHTML = currentPlaylistItems.map((item, index) => `
        <div class="playlist-item" draggable="true" data-item-id="${item.id}" data-index="${index}">
            <div class="playlist-item-info">
                <div class="playlist-item-title">${index + 1}. ${escapeHtml(item.fileName)}</div>
            </div>
            <div class="playlist-item-controls">
                <button class="btn-icon" onclick="removeFromPlaylist(${item.id})" title="Удалить">🗑️</button>
            </div>
        </div>
    `).join('');
}

async function loadTracksForSelect() {
    try {
        const response = await fetch('/api/broadcaster/media', {
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const files = await response.json();
            const select = document.getElementById('addTrackSelect');

            select.innerHTML = '<option value="">Выберите трек...</option>' +
                files.map(file => `<option value="${file.id}">${escapeHtml(file.fileName)}</option>`).join('');
        }
    } catch (error) {
        console.error('Error loading tracks:', error);
    }
}

async function addTrackToPlaylist() {
    const select = document.getElementById('addTrackSelect');
    const audioFileId = select.value;

    if (!audioFileId) {
        showToast('Выберите трек', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/items`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': currentUser.id
            },
            body: JSON.stringify({ audioFileId: parseInt(audioFileId) })
        });

        if (response.ok) {
            const updatedPlaylist = await response.json();
            currentPlaylistItems = updatedPlaylist.items || [];
            renderPlaylistItems();
            select.value = '';
            showToast('Трек добавлен', 'success');
            await loadPlaylists();
        } else {
            showToast('Ошибка при добавлении', 'error');
        }
    } catch (error) {
        console.error('Error adding track:', error);
        showToast('Ошибка соединения', 'error');
    }
}

async function addToCurrentPlaylist(audioFileId, fileName) {
    if (!currentPlaylistId) {
        showToast('Сначала откройте или создайте плейлист', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/items`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': currentUser.id
            },
            body: JSON.stringify({ audioFileId: audioFileId })
        });

        if (response.ok) {
            const updatedPlaylist = await response.json();
            currentPlaylistItems = updatedPlaylist.items || [];
            renderPlaylistItems();
            showToast(`Трек "${fileName}" добавлен в плейлист`, 'success');
            await loadPlaylists();
        } else {
            showToast('Ошибка при добавлении трека', 'error');
        }
    } catch (error) {
        console.error('Error adding to playlist:', error);
        showToast('Ошибка соединения', 'error');
    }
}

async function removeFromPlaylist(itemId) {
    if (!confirm('Удалить трек из плейлиста?')) return;

    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/items/${itemId}`, {
            method: 'DELETE',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            currentPlaylistItems = currentPlaylistItems.filter(item => item.id !== itemId);
            renderPlaylistItems();
            showToast('Трек удален', 'success');
            await loadPlaylists();
        } else {
            showToast('Ошибка при удалении', 'error');
        }
    } catch (error) {
        console.error('Error removing track:', error);
        showToast('Ошибка соединения', 'error');
    }
}

async function togglePlaylistLoop() {
    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/toggle-loop`, {
            method: 'POST',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const btn = document.getElementById('playlistLoopBtn');
            btn.classList.toggle('active');
            showToast('Режим повтора ' + (btn.classList.contains('active') ? 'включен' : 'выключен'), 'success');
        }
    } catch (error) {
        console.error('Error toggling loop:', error);
    }
}

async function togglePlaylistShuffle() {
    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/toggle-shuffle`, {
            method: 'POST',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            const btn = document.getElementById('playlistShuffleBtn');
            btn.classList.toggle('active');
            showToast('Перемешивание ' + (btn.classList.contains('active') ? 'включено' : 'выключено'), 'success');
        }
    } catch (error) {
        console.error('Error toggling shuffle:', error);
    }
}

async function activateCurrentPlaylist() {
    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/activate`, {
            method: 'POST',
            headers: { 'X-User-Id': currentUser.id }
        });

        if (response.ok) {
            showToast('Плейлист активирован и начал воспроизведение', 'success');
            closePlaylistModal();
            await loadPlaylists();
            await loadPlaybackStatus();
        } else {
            showToast('Ошибка при активации', 'error');
        }
    } catch (error) {
        console.error('Error activating playlist:', error);
        showToast('Ошибка соединения', 'error');
    }
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

function uploadAudio() {
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

        if (file.size > 50 * 1024 * 1024) {
            showToast('Файл превышает 50 МБ', 'error');
            document.body.removeChild(fileInput);
            return;
        }

        const fileExtension = file.name.split('.').pop().toLowerCase();
        if (!['mp3', 'wav', 'ogg'].includes(fileExtension)) {
            showToast('Неподдерживаемый формат', 'error');
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

            if (response.ok) {
                showToast(result.message || 'Трек успешно загружен!', 'success');
                setTimeout(() => {
                    loadBroadcastInfo();
                    loadMediaLibrary();
                    loadPlaybackStatus();
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

function closeCreatePlaylistModal() {
    document.getElementById('createPlaylistModal').classList.remove('active');
}

function closePlaylistModal() {
    document.getElementById('playlistModal').classList.remove('active');
    currentPlaylistId = null;
    currentPlaylistItems = [];
}

function closeReplyModal() {
    const modal = document.getElementById('replyModal');
    if (modal) modal.classList.remove('active');
    currentReplyMessageId = null;
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
    if (playbackStompClient && playbackStompClient.connected) {
        playbackStompClient.disconnect();
    }
    localStorage.removeItem('currentUser');
    window.location.href = '/login';
}

// Функция для добавления трека из файловой системы
async function addTrackFromFileSystem() {
    const filePath = document.getElementById('fsFilePath').value.trim();
    const trackName = document.getElementById('fsTrackName').value.trim();

    if (!filePath) {
        showToast('Укажите путь к файлу', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/broadcaster/playlists/${currentPlaylistId}/add-from-fs`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': currentUser.id
            },
            body: JSON.stringify({
                filePath: filePath,
                trackName: trackName
            })
        });

        const result = await response.json();

        if (response.ok) {
            // Обновляем отображение плейлиста
            if (result.playlist) {
                currentPlaylistItems = result.playlist.items || [];
                renderPlaylistItems();
            }

            // Очищаем поля
            document.getElementById('fsFilePath').value = '';
            document.getElementById('fsTrackName').value = '';

            showToast(result.message || 'Трек добавлен в плейлист', 'success');

            // Обновляем список плейлистов
            await loadPlaylists();
        } else {
            showToast(result.error || 'Ошибка при добавлении трека', 'error');
        }
    } catch (error) {
        console.error('Error adding track from file system:', error);
        showToast('Ошибка соединения с сервером', 'error');
    }
}

// Функция для обзора аудиофайлов
function browseAudioFiles() {
    const browser = document.getElementById('fsFileBrowser');
    if (browser.style.display === 'none') {
        browser.style.display = 'block';
        loadAudioFilesFromDirectory();
    } else {
        browser.style.display = 'none';
    }
}

// Функция для загрузки списка аудиофайлов из директории
async function loadAudioFilesFromDirectory() {
    const directory = document.getElementById('browserDirectory').value.trim();

    if (!directory) {
        showToast('Укажите путь к директории', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/broadcaster/fs-audio-files?directory=${encodeURIComponent(directory)}`);
        const result = await response.json();

        if (response.ok) {
            renderAudioFilesList(result.files);
        } else {
            showToast(result.error || 'Ошибка при загрузке списка файлов', 'error');
        }
    } catch (error) {
        console.error('Error loading audio files:', error);
        showToast('Ошибка соединения с сервером', 'error');
    }
}

// Функция для отображения списка аудиофайлов
function renderAudioFilesList(files) {
    const container = document.getElementById('audioFilesList');

    if (!files || files.length === 0) {
        container.innerHTML = '<div class="empty-state">Нет аудиофайлов в этой директории</div>';
        return;
    }

    container.innerHTML = files.map(file => `
        <div class="audio-file-item" style="
            padding: 10px;
            margin: 5px 0;
            background: #f5f5f5;
            border-radius: 5px;
            cursor: pointer;
            display: flex;
            justify-content: space-between;
            align-items: center;
        ">
            <div>
                <div><strong>${escapeHtml(file.name)}</strong></div>
                <div style="font-size: 12px; color: #666;">${file.size}</div>
            </div>
            <button class="btn btn-sm" onclick="selectFileFromBrowser('${escapeHtml(file.path)}', '${escapeHtml(file.name)}')">
                Выбрать
            </button>
        </div>
    `).join('');
}

function selectFileFromBrowser(filePath, fileName) {
    document.getElementById('fsFilePath').value = filePath;
    document.getElementById('fsTrackName').value = fileName.replace(/\.[^/.]+$/, ""); // Убираем расширение
    document.getElementById('fsFileBrowser').style.display = 'none';
    showToast(`Выбран файл: ${fileName}`, 'success');
}

const style = document.createElement('style');
style.textContent = `
    .btn-sm {
        padding: 5px 10px;
        font-size: 12px;
    }
    .audio-file-item:hover {
        background: #e0e0e0 !important;
    }
`;
document.head.appendChild(style);

document.querySelectorAll('.modal').forEach(modal => {
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
});