(function () {
  var overlay = null;

  function rupiah(value) {
    var n = Number(value) || 0;
    return "Rp " + n.toLocaleString("id-ID");
  }

  function make(tag, style, text) {
    var node = document.createElement(tag);
    if (style) node.setAttribute("style", style);
    if (text != null) node.textContent = text;
    return node;
  }

  function close() {
    if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
    overlay = null;
  }

  function open(run, toast, clearGoogle) {
    if (overlay) return;
    if (typeof run !== "function") {
      window.alert("Native bridge tidak tersedia.");
      return;
    }
    run("kasirshift.state", {}).then(function (state) {
      show(run, toast, state || {}, !!clearGoogle);
    }).catch(function (error) {
      var message = (error && error.message) || "Gagal membaca data shift.";
      if (typeof toast === "function") toast(message, "error");
      else window.alert(message);
    });
  }

  function show(run, toast, state, clearGoogle) {
    var isAdmin = String(state.currentRole || "").toLowerCase() === "admin";
    var mine = state.mine || null;
    var active = mine || (isAdmin ? state.globalActive : null);
    var activeMetrics = Array.isArray(state.activeMetrics) ? state.activeMetrics : [];
    var expected = 0;
    if (active) {
      var found = activeMetrics.filter(function (item) {
        return String(item.shiftId || "") === String(active.id || "");
      })[0];
      if (found) expected = Number(found.kasSeharusnya) || 0;
      else if (mine && state.metrics && String(state.metrics.shiftId || "") === String(active.id || "")) {
        expected = Number(state.metrics.kasSeharusnya) || 0;
      }
    }

    overlay = make("div", "position:fixed;inset:0;z-index:99999;display:flex;align-items:center;justify-content:center;background:rgba(2,6,23,.78);padding:18px;backdrop-filter:blur(4px)");
    var card = make("div", "width:100%;max-width:390px;border:1px solid #1e293b;border-radius:18px;background:#0f172a;color:#e2e8f0;box-shadow:0 24px 70px rgba(0,0,0,.55);padding:18px;font-family:system-ui,-apple-system,Segoe UI,sans-serif");
    overlay.appendChild(card);

    var title = make("p", "margin:0;font-size:15px;font-weight:800;color:" + (active ? "#fbbf24" : "#f87171"), active ? "Tutup Kasir & Keluar" : "Keluar Aplikasi");
    card.appendChild(title);

    var subtitle = make("p", "margin:6px 0 0;font-size:11px;line-height:1.5;color:#94a3b8", active ? "Shift kasir harus ditutup dan form shift diisi sebelum keluar." : "Tidak ada shift aktif. Lanjutkan keluar dari aplikasi?");
    card.appendChild(subtitle);

    var error = make("p", "margin:10px 0 0;font-size:11px;font-weight:700;color:#fda4af;display:none");
    var cashInput = null;
    var noteInput = null;

    if (active) {
      var info = make("div", "margin-top:12px;border:1px solid #1e293b;border-radius:12px;background:#020617;padding:10px;font-size:11px;line-height:1.7;color:#cbd5e1");
      info.appendChild(make("p", "margin:0;font-weight:800;color:#f8fafc", "Shift " + (active.jenisShift || "-") + " · " + (active.username || "-")));
      info.appendChild(make("p", "margin:2px 0 0", "Kas awal: " + rupiah(active.kasAwal)));
      info.appendChild(make("p", "margin:0;color:#67e8f9;font-weight:700", "Kas seharusnya: " + rupiah(expected)));
      card.appendChild(info);

      var cashLabel = make("label", "display:block;margin-top:12px;font-size:10px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#94a3b8", "Uang fisik di kasir");
      card.appendChild(cashLabel);
      cashInput = make("input", "box-sizing:border-box;width:100%;margin-top:5px;border:1px solid #334155;border-radius:10px;background:#020617;color:#f8fafc;padding:10px 12px;font-size:14px;outline:none");
      cashInput.type = "number";
      cashInput.inputMode = "numeric";
      cashInput.min = "0";
      cashInput.placeholder = "0";
      cashInput.value = String(expected);
      card.appendChild(cashInput);

      var noteLabel = make("label", "display:block;margin-top:10px;font-size:10px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#94a3b8", "Catatan shift");
      card.appendChild(noteLabel);
      noteInput = make("textarea", "box-sizing:border-box;width:100%;min-height:64px;resize:vertical;margin-top:5px;border:1px solid #334155;border-radius:10px;background:#020617;color:#f8fafc;padding:10px 12px;font-size:12px;outline:none");
      noteInput.placeholder = "Opsional";
      noteInput.maxLength = 300;
      card.appendChild(noteInput);
    }

    card.appendChild(error);

    var actions = make("div", "display:flex;gap:8px;margin-top:14px");
    var cancel = make("button", "flex:1;border:1px solid #334155;border-radius:10px;background:transparent;color:#cbd5e1;padding:10px;font-size:12px;font-weight:700", "Batal");
    cancel.type = "button";
    cancel.onclick = close;
    actions.appendChild(cancel);

    var confirm = make("button", "flex:1;border:0;border-radius:10px;background:" + (active ? "#f59e0b" : "#e11d48") + ";color:" + (active ? "#111827" : "#fff") + ";padding:10px;font-size:12px;font-weight:800", active ? "Tutup Shift & Keluar" : "Keluar");
    confirm.type = "button";
    actions.appendChild(confirm);
    card.appendChild(actions);

    var busy = false;
    var shiftClosed = false;

    function setBusy(value) {
      busy = value;
      confirm.disabled = value;
      cancel.disabled = value;
      confirm.style.opacity = value ? ".6" : "1";
    }

    function fail(message) {
      error.textContent = message;
      error.style.display = "block";
      setBusy(false);
    }

    confirm.onclick = function () {
      if (busy) return;
      setBusy(true);
      error.style.display = "none";
      var logout = function () {
        return run("auth.logout", { clearGoogleSignIn: clearGoogle }, "Keluar.");
      };
      if (!active || shiftClosed) {
        logout().then(close).catch(function (e) {
          fail((e && e.message) || "Gagal keluar.");
        });
        return;
      }
      var cash = cashInput.value === "" ? NaN : Number(cashInput.value);
      if (!isFinite(cash) || cash < 0) {
        fail("Isi uang fisik dengan angka 0 atau lebih.");
        return;
      }
      run("kasirshift.close", {
        uangFisik: cash,
        catatan: noteInput.value || "",
        shiftId: active.id,
        paksa: isAdmin
      }, "Shift ditutup.").then(function () {
        shiftClosed = true;
        return logout();
      }).then(close).catch(function (e) {
        fail((e && e.message) || "Gagal menutup shift atau keluar.");
      });
    };

    document.body.appendChild(overlay);
    if (cashInput) {
      setTimeout(function () {
        cashInput.focus();
        cashInput.select();
      }, 80);
    }
  }

  window.RRShiftLogout = { open: open, close: close };
})();
