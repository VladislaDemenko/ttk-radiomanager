document.addEventListener("DOMContentLoaded", () => {
  const loginForm = document.getElementById("loginForm");
  const registerForm = document.getElementById("registerForm");

  function showMessage(elementId, text, type) {
    const element = document.getElementById(elementId);
    if (!element) return;

    element.textContent = text;
    element.className = type ? `message ${type}` : "message";
  }

  function isLatinLogin(login) {
    return /^[A-Za-z]+$/.test(login);
  }

  function isRussianFio(fio) {
    return /^[А-Яа-яЁё\s-]+$/.test(fio.trim());
  }

  function isValidPassword(password) {
    return /^[A-Za-z0-9!@#$%^&*()_\-+=\[\]{};:'",.<>/?\\|`~]+$/.test(password);
  }

  if (registerForm) {
    registerForm.addEventListener("submit", async (event) => {
      event.preventDefault();

      const login = document.getElementById("regLogin").value.trim();
      const fio = document.getElementById("fio").value.trim();
      const password = document.getElementById("regPassword").value;
      const confirmPassword = document.getElementById("confirmPassword").value;

      showMessage("registerMessage", "", "");

      if (!login || !fio || !password || !confirmPassword) {
        showMessage("registerMessage", "Заполните все поля формы.", "error");
        return;
      }

      if (!isLatinLogin(login)) {
        showMessage("registerMessage", "Логин должен содержать только латинские буквы.", "error");
        return;
      }

      if (!isRussianFio(fio)) {
        showMessage("registerMessage", "ФИО должно содержать только русские буквы, пробелы или дефис.", "error");
        return;
      }

      if (!isValidPassword(password)) {
        showMessage("registerMessage", "Пароль может содержать только латинские буквы, цифры и символы.", "error");
        return;
      }

      if (password !== confirmPassword) {
        showMessage("registerMessage", "Пароли не совпадают.", "error");
        return;
      }

      // Отправка на бэкенд
      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            login: login,
            fullName: fio,
            password: password,
            confirmPassword: confirmPassword
          })
        });

        const result = await response.json();

        if (result.status === "success") {
          showMessage(
            "registerMessage",
            "Регистрация прошла успешно. Выполняется переход на страницу входа...",
            "success"
          );
          registerForm.reset();
          setTimeout(() => {
            window.location.href = "/login";
          }, 1400);
        } else {
          showMessage("registerMessage", result.message, "error");
        }
      } catch (error) {
        console.error('Error:', error);
        showMessage("registerMessage", "Ошибка соединения с сервером", "error");
      }
    });
  }

  if (loginForm) {
    loginForm.addEventListener("submit", async (event) => {
      event.preventDefault();

      const login = document.getElementById("login").value.trim();
      const password = document.getElementById("password").value;

      showMessage("loginMessage", "", "");

      if (!login || !password) {
        showMessage("loginMessage", "Введите логин и пароль.", "error");
        return;
      }

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ login, password })
        });

        if (response.ok) {
          const user = await response.json();
          localStorage.setItem("currentUser", JSON.stringify(user));
          showMessage(
            "loginMessage",
            `Вход выполнен успешно. Текущая роль: ${user.role}. Перенаправление...`,
            "success"
          );
          setTimeout(() => {
            if (user.role === "ADMIN") {
              window.location.href = "/admin";
            } else {
              window.location.href = "/player";
            }
          }, 1500);
        } else {
          const error = await response.json();
          showMessage("loginMessage", error.message || "Неверный логин или пароль", "error");
        }
      } catch (error) {
        console.error('Error:', error);
        showMessage("loginMessage", "Ошибка соединения с сервером", "error");
      }
    });
  }
});