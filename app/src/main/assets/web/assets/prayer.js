(function () {
  'use strict';

  if (window.__prayerUiInstalled) return;
  window.__prayerUiInstalled = true;

  var pending = new Map();
  var hooked = new WeakSet();
  var seq = 0;
  var els = {};
  var current = null;

  function notify(raw) {
    var msg;
    try {
      msg = JSON.parse(raw);
    } catch (_) {
      return;
    }
    if (!msg || msg.type !== 'result') return;
    var entry = pending.get(msg.id);
    if (!entry) return;
    pending.delete(msg.id);
    if (msg.ok) entry.resolve(msg.data);
    else entry.reject(new Error(msg.error || 'Perintah gagal'));
  }

  function hookBridge() {
    var bridge = window.RRBridge;
    if (!bridge || typeof bridge.onMessage !== 'function' || hooked.has(bridge)) return;
    var original = bridge.onMessage;
    bridge.onMessage = function (raw) {
      try {
        original.call(bridge, raw);
      } catch (e) {
        console.error('RRBridge original error', e);
      }
      notify(raw);
    };
    hooked.add(bridge);
  }

  setInterval(hookBridge, 300);

  function send(name, payload) {
    hookBridge();
    return new Promise(function (resolve, reject) {
      if (!window.BillingNative) {
        reject(new Error('Native bridge tidak tersedia'));
        return;
      }
      var id = 'prayer-' + Date.now() + '-' + (++seq);
      var timer = setTimeout(function () {
        if (!pending.has(id)) return;
        pending.delete(id);
        reject(new Error('Perintah timeout'));
      }, 30000);
      pending.set(id, {
        resolve: function (data) {
          clearTimeout(timer);
          resolve(data);
        },
        reject: function (error) {
          clearTimeout(timer);
          reject(error);
        },
      });
      try {
        window.BillingNative.postMessage(JSON.stringify({
          v: 1,
          type: 'command',
          id: id,
          name: name,
          payload: payload || {},
        }));
      } catch (e) {
        clearTimeout(timer);
        pending.delete(id);
        reject(e);
      }
    });
  }

  function ensureModal() {
    if (els.wrap) return;
    var wrap = document.createElement('div');
    wrap.id = 'prayer-modal';
    wrap.style.cssText = 'position:fixed;inset:0;z-index:9999;display:none;align-items:center;justify-content:center;padding:16px;background:rgba(2,6,23,.78);backdrop-filter:blur(4px);';
    wrap.innerHTML = ''
      + '<div id="prayer-card" role="dialog" aria-modal="true" style="width:100%;max-width:430px;max-height:88vh;overflow:auto;box-sizing:border-box;border:1px solid #1e293b;border-radius:18px;background:#0f172a;color:#e2e8f0;padding:18px;box-shadow:0 24px 80px rgba(0,0,0,.55);font-family:inherit;">'
      + '  <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">'
      + '    <div>'
      + '      <div style="font-size:16px;font-weight:800;color:#f8fafc;">Popup Waktu Sholat</div>'
      + '      <div style="font-size:11px;color:#94a3b8;margin-top:3px;">Dikirim otomatis ke semua TV</div>'
      + '    </div>'
      + '    <button type="button" id="prayer-close" style="border:1px solid #334155;background:#1e293b;color:#cbd5e1;border-radius:9px;padding:7px 10px;font-size:12px;font-weight:700;cursor:pointer;">Tutup</button>'
      + '  </div>'
      + '  <div style="margin-top:14px;display:flex;align-items:center;gap:10px;padding:10px 12px;border:1px solid #1e293b;border-radius:12px;background:#020617;">'
      + '    <input type="checkbox" id="prayer-enabled" style="width:18px;height:18px;accent-color:#10b981;cursor:pointer;">'
      + '    <label for="prayer-enabled" style="font-size:13px;font-weight:600;color:#e2e8f0;cursor:pointer;">Aktifkan popup otomatis</label>'
      + '  </div>'
      + '  <label for="prayer-message" style="display:block;margin-top:12px;font-size:11px;font-weight:700;color:#94a3b8;">Pesan popup</label>'
      + '  <textarea id="prayer-message" rows="3" maxlength="300" placeholder="Waktu sholat {sholat} telah masuk. Mohon jeda sejenak." style="width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #334155;border-radius:10px;background:#020617;color:#e2e8f0;padding:10px 12px;font-size:13px;line-height:1.45;resize:vertical;"></textarea>'
      + '  <div style="font-size:10px;color:#64748b;margin-top:4px;">Teks <b>{sholat}</b> diganti nama sholat otomatis.</div>'
      + '  <div style="display:flex;gap:10px;margin-top:12px;align-items:flex-end;">'
      + '    <label style="flex:1;font-size:11px;font-weight:700;color:#94a3b8;">Durasi (detik)<input id="prayer-duration" type="number" min="3" max="300" step="1" style="width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #334155;border-radius:10px;background:#020617;color:#e2e8f0;padding:10px 12px;font-size:13px;"></label>'
      + '    <button type="button" id="prayer-detect" style="flex:1;border:1px solid #0ea5e9;background:rgba(14,165,233,.12);color:#7dd3fc;border-radius:10px;padding:10px 12px;font-size:12px;font-weight:800;cursor:pointer;">Deteksi Lokasi</button>'
      + '  </div>'
      + '  <div style="display:flex;gap:8px;margin-top:10px;">'
      + '    <label style="flex:1;font-size:11px;font-weight:700;color:#94a3b8;">Latitude<input id="prayer-lat" type="number" step="any" placeholder="-6.2000" style="width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #334155;border-radius:10px;background:#020617;color:#e2e8f0;padding:9px 11px;font-size:13px;"></label>'
      + '    <label style="flex:1;font-size:11px;font-weight:700;color:#94a3b8;">Longitude<input id="prayer-lon" type="number" step="any" placeholder="106.8167" style="width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #334155;border-radius:10px;background:#020617;color:#e2e8f0;padding:9px 11px;font-size:13px;"></label>'
      + '  </div>'
      + '  <div id="prayer-loc-status" style="font-size:11px;color:#fbbf24;margin-top:7px;line-height:1.4;">Lokasi belum diatur.</div>'
      + '  <div style="margin-top:14px;padding:12px;border:1px solid #1e293b;border-radius:12px;background:#020617;">'
      + '    <div style="font-size:10px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#64748b;">Jadwal hari ini</div>'
      + '    <div id="prayer-schedule" style="margin-top:8px;font-size:12px;color:#cbd5e1;line-height:1.9;"></div>'
      + '    <div id="prayer-next" style="margin-top:8px;font-size:11px;font-weight:700;color:#34d399;"></div>'
      + '  </div>'
      + '  <div id="prayer-status" style="font-size:11px;line-height:1.4;margin-top:10px;min-height:16px;"></div>'
      + '  <div style="display:flex;gap:8px;margin-top:12px;">'
      + '    <button type="button" id="prayer-test" style="flex:1;border:1px solid #10b981;background:rgba(16,185,129,.12);color:#6ee7b7;border-radius:10px;padding:11px 12px;font-size:12px;font-weight:800;cursor:pointer;">Tes Popup ke Semua TV</button>'
      + '    <button type="button" id="prayer-save" style="flex:1;border:0;border-radius:10px;padding:11px 12px;font-size:12px;font-weight:900;color:#022c22;background:linear-gradient(135deg,#34d399,#22d3ee);cursor:pointer;">Simpan</button>'
      + '  </div>'
      + '  <div style="font-size:10px;line-height:1.5;color:#64748b;margin-top:10px;">Jadwal dihitung otomatis dari lokasi (metode Kemenag, ihtiyati 2 menit). Alarm tetap berjalan saat aplikasi ditutup; Android 12+ perlu izin alarm tepat.</div>'
      + '</div>';
    document.body.appendChild(wrap);

    els.wrap = wrap;
    els.enabled = document.getElementById('prayer-enabled');
    els.message = document.getElementById('prayer-message');
    els.duration = document.getElementById('prayer-duration');
    els.lat = document.getElementById('prayer-lat');
    els.lon = document.getElementById('prayer-lon');
    els.locStatus = document.getElementById('prayer-loc-status');
    els.schedule = document.getElementById('prayer-schedule');
    els.next = document.getElementById('prayer-next');
    els.status = document.getElementById('prayer-status');
    els.save = document.getElementById('prayer-save');
    els.test = document.getElementById('prayer-test');
    els.detect = document.getElementById('prayer-detect');
    els.close = document.getElementById('prayer-close');

    els.close.addEventListener('click', closeModal);
    els.wrap.addEventListener('click', function (event) {
      if (event.target === els.wrap) closeModal();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && els.wrap && els.wrap.style.display !== 'none') closeModal();
    });
    els.save.addEventListener('click', saveConfig);
    els.detect.addEventListener('click', detectLocation);
    els.test.addEventListener('click', testPopup);
  }

  function setStatus(text, color) {
    if (!els.status) return;
    els.status.textContent = text || '';
    els.status.style.color = color || '#94a3b8';
  }

  function setBusy(busy) {
    [els.save, els.test, els.detect].forEach(function (button) {
      if (!button) return;
      button.disabled = !!busy;
      button.style.opacity = busy ? '.55' : '1';
      button.style.cursor = busy ? 'not-allowed' : 'pointer';
    });
  }

  function renderSchedule(schedule, next) {
    if (!els.schedule) return;
    els.schedule.innerHTML = '';
    if (!Array.isArray(schedule) || schedule.length === 0) {
      els.schedule.textContent = 'Atur lokasi untuk melihat jadwal hari ini.';
      els.schedule.style.color = '#94a3b8';
    } else {
      schedule.forEach(function (prayer) {
        var row = document.createElement('div');
        row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:12px;';
        var name = document.createElement('span');
        name.textContent = prayer.name || '';
        var time = document.createElement('b');
        time.textContent = prayer.label || '';
        time.style.color = '#e2e8f0';
        row.appendChild(name);
        row.appendChild(time);
        els.schedule.appendChild(row);
      });
    }
    if (next && next.name) {
      els.next.textContent = 'Berikutnya: ' + next.name + ' ' + (next.label || '');
      els.next.style.display = '';
    } else {
      els.next.textContent = '';
      els.next.style.display = 'none';
    }
  }

  function populate(data) {
    current = data || {};
    var config = current.config || {};
    els.enabled.checked = !!config.enabled;
    els.message.value = config.message || '';
    els.duration.value = config.durationSec || 15;
    if (config.locationSet) {
      els.lat.value = config.latitude != null ? config.latitude : '';
      els.lon.value = config.longitude != null ? config.longitude : '';
      els.locStatus.textContent = current.locationStatus || ('Lokasi: ' + (config.locationLabel || ''));
      els.locStatus.style.color = '#34d399';
    } else {
      els.lat.value = '';
      els.lon.value = '';
      els.locStatus.textContent = current.locationStatus || 'Lokasi belum diatur.';
      els.locStatus.style.color = '#fbbf24';
    }
    renderSchedule(current.schedule, current.next);
  }

  function readForm() {
    var latText = (els.lat.value || '').trim();
    var lonText = (els.lon.value || '').trim();
    var lat = parseFloat(latText);
    var lon = parseFloat(lonText);
    var hasLocation = latText !== '' && lonText !== '' &&
      isFinite(lat) && isFinite(lon) &&
      lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    return {
      enabled: !!els.enabled.checked,
      message: (els.message.value || '').slice(0, 300),
      durationSec: parseInt(els.duration.value, 10) || 15,
      latitude: hasLocation ? lat : 0,
      longitude: hasLocation ? lon : 0,
      locationSet: hasLocation,
      locationLabel: hasLocation ? lat.toFixed(4) + ', ' + lon.toFixed(4) : '',
    };
  }

  function loadConfig() {
    setStatus('Memuat pengaturan...', '#38bdf8');
    send('prayer.get', {}).then(function (data) {
      populate(data);
      setStatus('', '#94a3b8');
    }).catch(function (error) {
      setStatus(error.message || 'Gagal memuat pengaturan', '#f87171');
    });
  }

  function openModal() {
    ensureModal();
    els.wrap.style.display = 'flex';
    loadConfig();
  }

  function closeModal() {
    if (els.wrap) els.wrap.style.display = 'none';
  }

  function saveConfig() {
    setBusy(true);
    setStatus('Menyimpan...', '#38bdf8');
    send('prayer.save', readForm()).then(function (data) {
      populate(data);
      setStatus('Pengaturan sholat disimpan.', '#34d399');
    }).catch(function (error) {
      setStatus(error.message || 'Gagal menyimpan', '#f87171');
    }).then(function () {
      setBusy(false);
    });
  }

  function detectLocation() {
    setBusy(true);
    setStatus('Mendeteksi lokasi...', '#38bdf8');
    send('prayer.detect', {}).then(function (data) {
      populate(data);
      setStatus(data.message || 'Lokasi terdeteksi.', '#34d399');
    }).catch(function (error) {
      setStatus(error.message || 'Lokasi tidak terdeteksi', '#f87171');
    }).then(function () {
      setBusy(false);
    });
  }

  function testPopup() {
    var form = readForm();
    setBusy(true);
    setStatus('Mengirim popup ke semua TV...', '#38bdf8');
    send('prayer.test', {
      message: form.message,
      durationSec: form.durationSec,
    }).then(function (data) {
      var count = data && data.count ? data.count : 0;
      setStatus('Popup terkirim ke ' + count + ' TV.', '#34d399');
    }).catch(function (error) {
      setStatus(error.message || 'Gagal mengirim popup', '#f87171');
    }).then(function () {
      setBusy(false);
    });
  }

  window.PrayerUI = { open: openModal };
})();
