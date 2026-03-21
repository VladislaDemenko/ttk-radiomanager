// public/js/admin.js
let currentUser = null;
let currentEditUserId = null;
let currentRolesUserId = null;

document.addEventListener("DOMContentLoaded", async () => {
    const userJson = localStorage.getItem("currentUser");
    if (!userJson) {
        window.location.href = "/login";
        return;
    }

    currentUser = JSON.parse(userJson);

    // Проверяем наличие роли ADMIN (массив roles)
    if (!currentUser.roles || !currentUser.roles.includes("ADMIN")) {
        window.location.href = "/player";
        return;
    }

    await loadNavigation();
    await loadUsers();
});

async function loadNavigation() {
    const navLinks = document.getElementById("navLinks");
    const userInfo = document.getElementById("userInfo");

    const modules = [
        { name: "Плеер", icon: "", path: "/player", active: false },
        { name: "Раздел ведущего", icon: "", path: "/broadcaster", active: false },
        { name: "Администрирование", icon: "", path: "/admin", active: true }
    ];

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

async function loadUsers() {
    const login = document.getElementById("filterLogin")?.value || "";
    const fullName = document.getElementById("filterFullName")?.value || "";
    const role = document.getElementById("filterRole")?.value || "";
    const dateFrom = document.getElementById("filterDateFrom")?.value || "";
    const dateTo = document.getElementById("filterDateTo")?.value || "";

    const params = new URLSearchParams();
    if (login) params.append("login", login);
    if (fullName) params.append("fullName", fullName);
    if (role) params.append("role", role);
    if (dateFrom) {
        const date = new Date(dateFrom);
        params.append("dateFrom", date.toISOString());
    }
    if (dateTo) {
        const date = new Date(dateTo);
        date.setHours(23, 59, 59, 999);
        params.append("dateTo", date.toISOString());
    }

    try {
        const response = await fetch(`/api/admin/users?${params.toString()}`);
        if (response.ok) {
            const users = await response.json();
            renderUsersTable(users);
        } else {
            showToast("Ошибка загрузки пользователей", "error");
        }
    } catch (error) {
        console.error("Error loading users:", error);
        showToast("Ошибка соединения с сервером", "error");
    }
}

function renderUsersTable(users) {
    const tbody = document.getElementById("usersTableBody");

    if (!users || users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty-state">Пользователи не найдены</td></tr>';
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr>
            <td>${escapeHtml(user.login)}</td>
            <td>${escapeHtml(user.fullName)}</td>
            <td>
                ${user.roles && user.roles.map(role =>
                    `<span class="role-badge ${role.toLowerCase()}">${getRoleName(role)}</span>`
                ).join('')}
            </td>
            <td>${formatDate(user.registrationDate)}</td>
            <td class="action-buttons">
                <button class="action-btn edit" onclick="editUser(${user.id})">Редактировать</button>
                <button class="action-btn password" onclick="changePassword(${user.id})">Сменить пароль</button>
                <button class="action-btn roles" onclick="assignRoles(${user.id}, ${JSON.stringify(user.roles).replace(/"/g, '&quot;')})">Назначить роли</button>
                <button class="action-btn delete" onclick="deleteUser(${user.id})">Удалить</button>
            </td>
        </tr>
    `).join('');
}

function getRoleName(role) {
    const roles = {
        'USER': 'Пользователь',
        'BROADCASTER': 'Ведущий',
        'ADMIN': 'Администратор'
    };
    return roles[role] || role;
}

function formatDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('ru-RU', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function editUser(id) {
    currentEditUserId = id;
    fetch(`/api/admin/users/${id}`)
        .then(response => response.json())
        .then(user => {
            document.getElementById("editLogin").value = user.login;
            document.getElementById("editFullName").value = user.fullName;
            document.getElementById("editMessage").textContent = "";
            document.getElementById("editModal").classList.add("active");
        })
        .catch(error => {
            console.error("Error loading user:", error);
            showToast("Ошибка загрузки данных пользователя", "error");
        });
}

async function saveUserEdit() {
    const login = document.getElementById("editLogin").value.trim();
    const fullName = document.getElementById("editFullName").value.trim();

    if (!login || !fullName) {
        document.getElementById("editMessage").textContent = "Заполните все поля";
        document.getElementById("editMessage").className = "message error";
        return;
    }

    if (!/^[A-Za-z]+$/.test(login)) {
        document.getElementById("editMessage").textContent = "Логин должен содержать только латинские буквы";
        document.getElementById("editMessage").className = "message error";
        return;
    }

    if (!/^[А-Яа-яЁё\s-]+$/.test(fullName)) {
        document.getElementById("editMessage").textContent = "ФИО должно содержать только русские буквы, пробелы или дефис";
        document.getElementById("editMessage").className = "message error";
        return;
    }

    try {
        const response = await fetch(`/api/admin/users/${currentEditUserId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ login, fullName })
        });

        const result = await response.json();

        if (response.ok) {
            closeModal("editModal");
            showToast("Пользователь обновлен", "success");
            loadUsers();
        } else {
            document.getElementById("editMessage").textContent = result.message;
            document.getElementById("editMessage").className = "message error";
        }
    } catch (error) {
        console.error("Error updating user:", error);
        showToast("Ошибка при обновлении", "error");
    }
}

function changePassword(id) {
    currentEditUserId = id;
    document.getElementById("newPassword").value = "";
    document.getElementById("confirmPassword").value = "";
    document.getElementById("passwordMessage").textContent = "";
    document.getElementById("passwordModal").classList.add("active");
}

async function savePassword() {
    const newPassword = document.getElementById("newPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;

    if (!newPassword || !confirmPassword) {
        document.getElementById("passwordMessage").textContent = "Заполните все поля";
        document.getElementById("passwordMessage").className = "message error";
        return;
    }

    if (newPassword !== confirmPassword) {
        document.getElementById("passwordMessage").textContent = "Пароли не совпадают";
        document.getElementById("passwordMessage").className = "message error";
        return;
    }

    if (!/^[A-Za-z0-9!@#$%^&*()_\-+=\[\]{};:'",.<>/?\\|`~]+$/.test(newPassword)) {
        document.getElementById("passwordMessage").textContent = "Пароль может содержать только латинские буквы, цифры и символы";
        document.getElementById("passwordMessage").className = "message error";
        return;
    }

    try {
        const response = await fetch(`/api/admin/users/${currentEditUserId}/change-password`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ newPassword, confirmPassword })
        });

        const result = await response.json();

        if (response.ok) {
            closeModal("passwordModal");
            showToast("Пароль изменен", "success");
        } else {
            document.getElementById("passwordMessage").textContent = result.message;
            document.getElementById("passwordMessage").className = "message error";
        }
    } catch (error) {
        console.error("Error changing password:", error);
        showToast("Ошибка при смене пароля", "error");
    }
}

function assignRoles(id, currentRoles) {
    currentRolesUserId = id;

    document.getElementById("roleUser").checked = currentRoles.includes("USER");
    document.getElementById("roleBroadcaster").checked = currentRoles.includes("BROADCASTER");
    document.getElementById("roleAdmin").checked = currentRoles.includes("ADMIN");
    document.getElementById("rolesMessage").textContent = "";
    document.getElementById("rolesModal").classList.add("active");
}

async function saveRoles() {
    const roles = [];
    if (document.getElementById("roleUser").checked) roles.push("USER");
    if (document.getElementById("roleBroadcaster").checked) roles.push("BROADCASTER");
    if (document.getElementById("roleAdmin").checked) roles.push("ADMIN");

    if (roles.length === 0) {
        document.getElementById("rolesMessage").textContent = "Выберите хотя бы одну роль";
        document.getElementById("rolesMessage").className = "message error";
        return;
    }

    try {
        const response = await fetch(`/api/admin/users/${currentRolesUserId}/assign-roles`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ roles })
        });

        const result = await response.json();

        if (response.ok) {
            closeModal("rolesModal");
            showToast("Роли назначены", "success");
            loadUsers();
        } else {
            document.getElementById("rolesMessage").textContent = result.message;
            document.getElementById("rolesMessage").className = "message error";
        }
    } catch (error) {
        console.error("Error assigning roles:", error);
        showToast("Ошибка при назначении ролей", "error");
    }
}

async function deleteUser(id) {
    if (!confirm("Вы уверены, что хотите удалить этого пользователя?")) return;

    try {
        const response = await fetch(`/api/admin/users/${id}`, {
            method: "DELETE"
        });

        const result = await response.json();

        if (response.ok) {
            showToast("Пользователь удален", "success");
            loadUsers();
        } else {
            showToast(result.message || "Ошибка при удалении", "error");
        }
    } catch (error) {
        console.error("Error deleting user:", error);
        showToast("Ошибка при удалении", "error");
    }
}

function applyFilters() {
    loadUsers();
}

function resetFilters() {
    document.getElementById("filterLogin").value = "";
    document.getElementById("filterFullName").value = "";
    document.getElementById("filterRole").value = "";
    document.getElementById("filterDateFrom").value = "";
    document.getElementById("filterDateTo").value = "";
    loadUsers();
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove("active");
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

document.querySelectorAll('.modal').forEach(modal => {
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
});