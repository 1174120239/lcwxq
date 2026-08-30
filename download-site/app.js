(function () {
  'use strict';
  var API = { admin: 'https://admin.lcxqy.cn/Api/api.php', replacement: 'https://api.lcxqy.cn', web: 'https://prev.lcxqy.cn/' };
  var CACHE_KEY = 'lcxqy-download-site-data-v1';
  var $ = function (selector) { return document.querySelector(selector); };
  var text = function (value, fallback) { return value === null || value === undefined || String(value).trim() === '' ? (fallback || '') : String(value); };
  var formatNumber = function (value) { if (value === null || value === undefined || value === '') return '--'; var n = Number(value); return Number.isFinite(n) ? n.toLocaleString('zh-CN') : text(value, '--'); };
  function setLink(selector, url) { var node = $(selector); if (node && url) node.href = url; }
  function renderVersions(current, history) {
    var list = $('#version-list'); var entries = [];
    if (current && current.version) entries.push({ version: current.version, code: current.versionCode, date: '最新发布', intro: current.versionIntro, force: current.qzgx || current.force });
    (history || []).forEach(function (item) { if (!entries.some(function (entry) { return String(entry.code) === String(item.versionCode || item.code); })) entries.push({ version: item.version, code: item.versionCode || item.code, date: item.date, intro: item.versionIntro, changes: item.changes, name: item.name, force: item.force }); });
    if (!entries.length) { list.innerHTML = '<div class="loading-line">暂时没有可展示的版本记录。</div>'; return; }
    list.innerHTML = entries.slice(0, 12).map(function (entry) {
      var changes = entry.changes || (entry.intro ? [entry.intro] : ['本次版本已发布，欢迎下载体验。']);
      return '<article class="version-entry"><div class="version-meta"><strong>' + text(entry.version, '未知版本') + '</strong><span>' + text(entry.date, '日期未知') + '</span>' + (entry.force ? '<span class="badge">重要更新</span>' : '') + '</div><div><h3>' + (entry.name ? text(entry.name).trim() : '聊城一中论坛更新') + '</h3><ul class="changes">' + changes.map(function (change) { return '<li>' + escapeHtml(change) + '</li>'; }).join('') + '</ul></div></article>';
    }).join('');
  }
  function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]; }); }
  function dayKey() {
    var now = new Date(Date.now() + 8 * 60 * 60 * 1000);
    return now.toISOString().slice(0, 10);
  }
  function readCache() {
    try {
      var value = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
      return value && value.day && value.payload ? value : null;
    } catch (error) { return null; }
  }
  function writeCache(payload) {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify({ day: dayKey(), payload: payload })); } catch (error) { /* Storage can be unavailable in private/file contexts. */ }
  }
  function payloadFromResults(results) {
    return {
      users: results[0].status === 'fulfilled' ? results[0].value : null,
      spaces: results[1].status === 'fulfilled' ? results[1].value : null,
      adminVersion: results[2].status === 'fulfilled' ? results[2].value : null,
      versionList: results[3].status === 'fulfilled' ? results[3].value : null,
      download: results[4].status === 'fulfilled' ? results[4].value : null,
      config: results[5].status === 'fulfilled' ? results[5].value : null,
      successCount: results.filter(function (result) { return result.status === 'fulfilled'; }).length
    };
  }
  function applyPayload(payload, statusText) {
    payload = payload || {};
    var users = payload.users;
    var spaces = payload.spaces;
    var adminVersion = payload.adminVersion;
    var versionList = payload.versionList;
    var download = payload.download;
    var config = payload.config && payload.config.data ? payload.config.data : {};
    if (config.heroKicker) $('#hero-kicker-text').textContent = config.heroKicker;
    if (config.heroTitle) $('#hero-title').textContent = config.heroTitle;
    if (config.heroIntro) $('#hero-intro').textContent = config.heroIntro;
    var webUrl = config.webUrl || API.web;
    $('#user-count').textContent = text(users && users.usercount, '--');
    $('#space-count').textContent = formatNumber(spaces && spaces.total);
    var current = adminVersion && adminVersion.version ? adminVersion : null;
    var data = versionList && versionList.data ? versionList.data : {};
    var listedCurrent = data.currentVersion || null;
    var latest = listedCurrent || current;
    $('#version-count').textContent = text(latest && latest.version, '--');
    renderVersions(latest, data.versions || []);
    var android = download && download.data && download.data.androidDownloadUrl;
    if (!android && current) android = current.versionUrl;
    setLink('#android-link', android); setLink('#web-link', webUrl); setLink('#hero-web-link', webUrl);
    $('#download-note').textContent = android ? '最新版本：' + text(latest && latest.version, '当前版本') : 'Android 地址暂未配置，请先使用网页版。';
    var ok = Number(payload.successCount) || 0;
    $('#sync-state').textContent = statusText || (ok >= 3 ? '已同步' : '部分同步');
    $('#data-note').textContent = statusText ? statusText : (ok >= 3 ? '数据来自现有公开接口，页面加载时自动更新。' : '部分数据暂时无法读取，页面其余内容仍可正常浏览。');
  }
  async function loadData() {
    var cached = readCache();
    if (cached && cached.day === dayKey()) {
      applyPayload(cached.payload, '今日已同步');
      return;
    }
    var results = await Promise.allSettled([
      fetch(API.admin + '?act=usercount').then(function (response) { return response.json(); }),
      fetch(API.replacement + '/SFreeSpace/spaceList?page=1&limit=1').then(function (response) { return response.json(); }),
      fetch(API.admin + '?update=1').then(function (response) { return response.json(); }),
      fetch(API.admin + '?act=versionList').then(function (response) { return response.json(); }),
      fetch(API.replacement + '/SFreeInvitation/config').then(function (response) { return response.json(); }),
      fetch(API.admin + '?act=downloadSiteConfig').then(function (response) { return response.json(); })
    ]);
    var payload = payloadFromResults(results);
    applyPayload(payload);
    if (payload.successCount >= 3) writeCache(payload);
  }
  var toggle = $('.menu-toggle'); var mobile = $('.mobile-nav');
  toggle.addEventListener('click', function () { var open = mobile.classList.toggle('open'); toggle.setAttribute('aria-expanded', String(open)); mobile.setAttribute('aria-hidden', String(!open)); });
  mobile.querySelectorAll('a').forEach(function (link) { link.addEventListener('click', function () { mobile.classList.remove('open'); toggle.setAttribute('aria-expanded', 'false'); }); });
  $('#year').textContent = new Date().getFullYear();
  var initialCache = readCache();
  if (initialCache && initialCache.day !== dayKey()) applyPayload(initialCache.payload, '显示昨日数据，正在更新今日数据…');
  loadData().catch(function () {
    if (!initialCache) $('#data-note').textContent = '数据暂时无法读取，请检查站点域名的 CORS 配置。';
    $('#sync-state').textContent = '读取失败';
  });
}());
