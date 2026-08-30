(function () {
  'use strict';
  var API = { admin: 'https://admin.lcxqy.cn/Api/api.php', replacement: 'https://api.lcxqy.cn', web: 'https://prev.lcxqy.cn/' };
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
  async function loadData() {
    var results = await Promise.allSettled([
      fetch(API.admin + '?act=usercount').then(function (response) { return response.json(); }),
      fetch(API.replacement + '/SFreeSpace/spaceList?page=1&limit=1').then(function (response) { return response.json(); }),
      fetch(API.admin + '?update=1').then(function (response) { return response.json(); }),
      fetch(API.admin + '?act=versionList').then(function (response) { return response.json(); }),
      fetch(API.replacement + '/SFreeInvitation/config').then(function (response) { return response.json(); })
    ]);
    var users = results[0].status === 'fulfilled' ? results[0].value : null;
    var spaces = results[1].status === 'fulfilled' ? results[1].value : null;
    var adminVersion = results[2].status === 'fulfilled' ? results[2].value : null;
    var versionList = results[3].status === 'fulfilled' ? results[3].value : null;
    var download = results[4].status === 'fulfilled' ? results[4].value : null;
    $('#user-count').textContent = text(users && users.usercount, '--');
    $('#space-count').textContent = formatNumber(spaces && spaces.total);
    var current = adminVersion && adminVersion.version ? adminVersion : null;
    var data = versionList && versionList.data ? versionList.data : {};
    var listedCurrent = data.currentVersion || null;
    var latest = listedCurrent || current;
    $('#version-count').textContent = text(latest && latest.version, '--');
    renderVersions(latest, data.versions || []);
    var android = download && download.data && download.data.androidDownloadUrl;
    var web = API.web;
    if (!android && current) android = current.versionUrl;
    setLink('#android-link', android); setLink('#web-link', web);
    $('#download-note').textContent = android ? '最新版本：' + text(latest && latest.version, '当前版本') : 'Android 地址暂未配置，请先使用网页版。';
    var ok = results.filter(function (result) { return result.status === 'fulfilled'; }).length;
    $('#sync-state').textContent = ok >= 3 ? '已同步' : '部分同步';
    $('#data-note').textContent = ok >= 3 ? '数据来自现有公开接口，页面加载时自动更新。' : '部分数据暂时无法读取，页面其余内容仍可正常浏览。';
  }
  var toggle = $('.menu-toggle'); var mobile = $('.mobile-nav');
  toggle.addEventListener('click', function () { var open = mobile.classList.toggle('open'); toggle.setAttribute('aria-expanded', String(open)); mobile.setAttribute('aria-hidden', String(!open)); });
  mobile.querySelectorAll('a').forEach(function (link) { link.addEventListener('click', function () { mobile.classList.remove('open'); toggle.setAttribute('aria-expanded', 'false'); }); });
  $('#year').textContent = new Date().getFullYear(); loadData();
}());
