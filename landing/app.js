(function () {
  const root = document.documentElement;
  const themeBtn = document.getElementById('themeToggle');
  const themeIcon = document.getElementById('themeIcon');
  const heroImg = document.getElementById('heroPhoneImg');
  const screenTabs = Array.from(document.querySelectorAll('.screen-tab'));

  let activeScreen = 'rules';

  const SUN_SVG =
    '<circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/>';
  const MOON_SVG = '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>';

  function isDark() {
    return root.getAttribute('data-theme') === 'dark';
  }

  function screenSrc(screen) {
    if (screen === 'rules') return isDark() ? '/screens/rules-dark.png' : '/screens/rules-light.png';
    if (screen === 'editor') return '/screens/editor-light.png';
    if (screen === 'settings') return '/screens/settings-light.png';
    return '/screens/splash-light.png';
  }

  function syncThemeUI() {
    themeIcon.innerHTML = isDark() ? SUN_SVG : MOON_SVG;
    if (activeScreen === 'rules') {
      heroImg.src = screenSrc('rules');
    }
  }

  themeBtn.addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark';
    if (next === 'dark') root.setAttribute('data-theme', 'dark');
    else root.removeAttribute('data-theme');
    localStorage.setItem('bg_theme', next);
    syncThemeUI();
  });

  screenTabs.forEach((btn) => {
    btn.addEventListener('click', () => {
      activeScreen = btn.dataset.screen || 'rules';
      screenTabs.forEach((b) => b.classList.toggle('active', b === btn));
      heroImg.style.opacity = '0.4';
      setTimeout(() => {
        heroImg.src = screenSrc(activeScreen);
        heroImg.alt = `BuzzGuard ${btn.textContent} screen`;
        heroImg.style.opacity = '1';
      }, 90);
    });
  });

  syncThemeUI();

  // Interactive Mad-Libs Rule Playground
  const APPS = ['WhatsApp', 'Telegram', 'Gmail', 'Any app'];
  const KEYWORDS = ['any message', '"urgent, server"', '"OTP, verification"', '"% off, promo"'];
  const CONTEXTS = ['any time', 'work hours (9–5)', 'screen off + Wi-Fi', 'silent mode'];
  const ACTIONS = [
    { label: 'cooldown for 60s', kind: 'cooldown' },
    { label: 'trigger alarm + remind', kind: 'alarm' },
    { label: 'hide content (Secret)', kind: 'secret' },
    { label: 'dismiss immediately', kind: 'dismiss' },
    { label: 'batch every 60m', kind: 'batch' },
  ];

  let appIdx = 0;
  let kwIdx = 0;
  let ctxIdx = 0;
  let actIdx = 0;

  const tokApp = document.getElementById('tokApp');
  const tokKeyword = document.getElementById('tokKeyword');
  const tokContext = document.getElementById('tokContext');
  const tokAction = document.getElementById('tokAction');
  const simBtn = document.getElementById('simBtn');
  const simFeed = document.getElementById('simFeed');
  const simStatus = document.getElementById('simStatus');

  function renderTokens() {
    tokApp.textContent = `${APPS[appIdx]} ▾`;
    tokKeyword.textContent = `${KEYWORDS[kwIdx]} ▾`;
    tokContext.textContent = `${CONTEXTS[ctxIdx]} ▾`;
    tokAction.textContent = `${ACTIONS[actIdx].label} ▾`;
  }

  function runSimulation() {
    const appName = APPS[appIdx] === 'Any app' ? 'Messages' : APPS[appIdx];
    const action = ACTIONS[actIdx];

    const samples = [
      { title: 'Team Chat', body: 'Hey are you around?', time: '9:41 PM' },
      { title: 'Team Chat', body: 'Sending the updated build now', time: '9:41 PM' },
      { title: 'Team Chat', body: 'URGENT: check staging before 10', time: '9:41 PM' },
    ];

    simFeed.innerHTML = '';
    simStatus.textContent = `Rule active · ${action.label}`;

    samples.forEach((s, i) => {
      let badge = 'Delivered';
      let badgeClass = 'ok';
      let muted = false;
      let displayBody = s.body;

      if (action.kind === 'cooldown') {
        if (i === 0) {
          badge = 'Allowed · Cooldown started';
        } else {
          badge = 'Silenced (cooldown)';
          badgeClass = 'act';
          muted = true;
        }
      } else if (action.kind === 'dismiss') {
        badge = 'Auto-dismissed';
        badgeClass = 'act';
        muted = true;
      } else if (action.kind === 'batch') {
        badge = 'Held for 60m batch';
        badgeClass = 'act';
        muted = true;
      } else if (action.kind === 'secret') {
        badge = 'Redacted on lock screen';
        badgeClass = 'ok';
        displayBody = 'Private message — unlock to view';
      } else if (action.kind === 'alarm') {
        badge = 'Alarm + SOS vibration';
        badgeClass = 'act';
      }

      const el = document.createElement('div');
      el.className = `sim-item${muted ? ' muted' : ''}`;
      el.style.animationDelay = `${i * 75}ms`;
      el.innerHTML = `
        <div class="sim-top">
          <span>${appName} · ${s.time}</span>
          <span class="sim-badge ${badgeClass}">${badge}</span>
        </div>
        <div class="sim-title">${s.title}</div>
        <div class="sim-body">${displayBody}</div>
      `;
      simFeed.appendChild(el);
    });
  }

  tokApp.addEventListener('click', () => { appIdx = (appIdx + 1) % APPS.length; renderTokens(); runSimulation(); });
  tokKeyword.addEventListener('click', () => { kwIdx = (kwIdx + 1) % KEYWORDS.length; renderTokens(); runSimulation(); });
  tokContext.addEventListener('click', () => { ctxIdx = (ctxIdx + 1) % CONTEXTS.length; renderTokens(); runSimulation(); });
  tokAction.addEventListener('click', () => { actIdx = (actIdx + 1) % ACTIONS.length; renderTokens(); runSimulation(); });
  simBtn.addEventListener('click', runSimulation);

  renderTokens();
  runSimulation();
})();
