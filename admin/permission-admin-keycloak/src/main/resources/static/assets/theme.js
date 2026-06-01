(function () {
  var storageKey = "permission-admin-theme";
  var fallbackTheme = "corporate";

  function readTheme() {
    try {
      return localStorage.getItem(storageKey) || fallbackTheme;
    } catch (error) {
      return fallbackTheme;
    }
  }

  function saveTheme(theme) {
    try {
      localStorage.setItem(storageKey, theme);
    } catch (error) {
      // Ignore storage errors so the selector still changes the current page.
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme || fallbackTheme);
  }

  applyTheme(readTheme());

  document.addEventListener("DOMContentLoaded", function () {
    var theme = readTheme();
    applyTheme(theme);
    document.querySelectorAll("[data-theme-select]").forEach(function (select) {
      select.value = theme;
      select.addEventListener("change", function () {
        applyTheme(select.value);
        saveTheme(select.value);
        document.querySelectorAll("[data-theme-select]").forEach(function (other) {
          other.value = select.value;
        });
      });
    });
  });
})();
