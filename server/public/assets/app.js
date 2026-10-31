/* Reps dashboard. Reads the latest snapshot pushed by the phone and renders progress. No build step. */
(() => {
  const $ = (id) => document.getElementById(id);
  const DAY = 86400000;
  const state = { me: null, snap: null, snapshots: [], derived: null, metric: 'top', workoutsShown: 8, exerciseId: null };

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const unit = () => (state.snap?.settings?.unit === 'LB' ? 'lb' : 'kg');
  const toUnit = (kg) => (unit() === 'lb' ? kg * 2.2046226218 : kg);
  const num = (v) => { const r = Math.round(v * 100) / 100; return Number.isInteger(r) ? String(r) : String(r).replace(/0+$/, ''); };
  const fmtW = (kg) => `${num(toUnit(kg))} ${unit()}`;
  const fmtVol = (kg) => { const v = toUnit(kg); return v >= 10000 ? `${(v / 1000).toFixed(1)}k ${unit()}` : `${Math.round(v)} ${unit()}`; };
  const fmtDate = (ms) => { const d = new Date(ms), t = new Date(); const same = d.toDateString() === t.toDateString(); const y = new Date(t - DAY).toDateString() === d.toDateString(); return same ? 'Today' : y ? 'Yesterday' : d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: d.getFullYear() === t.getFullYear() ? undefined : 'numeric' }); };
  const fmtTime = (ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const fmtDur = (ms) => { const m = Math.max(0, Math.round(ms / 60000)); return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m} min`; };
  const ago = (ms) => { const s = (Date.now() - ms) / 1000; if (s < 60) return 'just now'; if (s < 3600) return `${Math.floor(s / 60)} min ago`; if (s < 86400) return `${Math.floor(s / 3600)} h ago`; return fmtDate(ms).toLowerCase(); };
  const startOfWeek = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); const day = (x.getDay() + 6) % 7; x.setDate(x.getDate() - day); return x; };
  const epley = (w, r) => (r <= 0 ? 0 : r === 1 ? w : w * (1 + r / 30));

  async function api(path, opts) {
    const r = await fetch(path, { credentials: 'same-origin', ...opts });
    if (r.status === 401) { location.href = '/'; throw new Error('unauthorized'); }
    return r;
  }

  function toast(msg) { const t = $('toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 2200); }

  // ---------- derive everything from the snapshot ----------
  function derive(snap) {
    const exById = Object.fromEntries(snap.exercises.map((e) => [e.id, e]));
    const wById = Object.fromEntries(snap.workouts.map((w) => [w.id, w]));
    const weById = Object.fromEntries(snap.workoutExercises.map((we) => [we.id, we]));
    const completed = snap.workouts.filter((w) => w.endedAt).sort((a, b) => b.startedAt - a.startedAt);
    const sets = snap.sets.map((s) => {
      const we = weById[s.workoutExerciseId]; const w = we && wById[we.workoutId]; const ex = we && exById[we.exerciseId];
      return we && w && ex && w.endedAt ? { ...s, exercise: ex, workout: w } : null;
    }).filter(Boolean).sort((a, b) => a.completedAt - b.completedAt || a.position - b.position);
    const working = sets.filter((s) => !s.isWarmup);
    const setsByWorkout = {};
    for (const s of sets) (setsByWorkout[s.workout.id] ||= []).push(s);

    // weekly volume (12 weeks)
    const thisMonday = startOfWeek(new Date());
    const weeks = [];
    for (let i = 11; i >= 0; i--) {
      const start = new Date(thisMonday); start.setDate(start.getDate() - i * 7);
      const end = new Date(start); end.setDate(end.getDate() + 7);
      const inWeek = working.filter((s) => s.workout.startedAt >= start && s.workout.startedAt < end);
      weeks.push({ start, volume: inWeek.reduce((a, s) => a + s.weightKg * s.reps, 0), sets: inWeek.length, workouts: new Set(inWeek.map((s) => s.workout.id)).size });
    }
    // streak
    const weekKeys = new Set(completed.map((w) => startOfWeek(new Date(w.startedAt)).getTime()));
    let streak = 0; let cur = thisMonday.getTime(); if (!weekKeys.has(cur)) cur -= 7 * DAY;
    while (weekKeys.has(cur)) { streak++; cur -= 7 * DAY; }
    // PRs
    const prEvents = []; const best = {};
    const byEx = {};
    for (const s of working) (byEx[s.exercise.id] ||= []).push(s);
    for (const [exId, list] of Object.entries(byEx)) {
      let top = 0;
      for (const s of list) {
        if (s.weightKg > top + 1e-4 && s.reps > 0) { top = s.weightKg; prEvents.push({ exercise: s.exercise, weightKg: s.weightKg, reps: s.reps, at: s.completedAt, workoutId: s.workout.id, setId: s.id }); }
      }
      const bestSet = list.reduce((a, s) => (s.weightKg > a.weightKg || (s.weightKg === a.weightKg && s.reps > a.reps) ? s : a), list[0]);
      best[exId] = { exercise: bestSet.exercise, weightKg: bestSet.weightKg, reps: bestSet.reps, at: bestSet.completedAt, orm: Math.max(...list.map((s) => epley(s.weightKg, s.reps))), sessions: new Set(list.map((s) => s.workout.id)).size, last: Math.max(...list.map((s) => s.workout.startedAt)) };
    }
    const prSetIds = new Set(prEvents.map((p) => p.setId));
    const thisWeek = weeks[weeks.length - 1];
    const trainedDays = new Set(completed.map((w) => new Date(w.startedAt).toDateString()));
    return { exById, completed, sets, working, setsByWorkout, weeks, streak, prEvents: prEvents.sort((a, b) => b.at - a.at), best, prSetIds, thisWeek, trainedDays,
      totalVolume: working.reduce((a, s) => a + s.weightKg * s.reps, 0) };
  }

  // ---------- charts (inline SVG) ----------
  function barChart(values, labels, { height = 150, highlightLast = true } = {}) {
    const w = 600, pad = 4, gap = 6, n = values.length, max = Math.max(1, ...values);
    const bw = (w - gap * (n - 1)) / n;
    let bars = '';
    values.forEach((v, i) => {
      const h = v <= 0 ? 3 : Math.max(3, (v / max) * (height - pad));
      const x = i * (bw + gap), y = height - h;
      const fill = v <= 0 ? 'var(--surface3)' : highlightLast && i === n - 1 ? 'var(--accent)' : 'color-mix(in srgb, var(--accent) 55%, transparent)';
      bars += `<rect x="${x}" y="${y}" width="${bw}" height="${h}" rx="6" fill="${fill}"><title>${labels[i]}: ${esc(values[i].toFixed ? fmtVol(values[i]) : values[i])}</title></rect>`;
    });
    return `<svg class="chart" viewBox="0 0 ${w} ${height}" preserveAspectRatio="none">${bars}</svg><div class="chart-labels">${labels.map((l) => `<span>${esc(l)}</span>`).join('')}</div>`;
  }

  function lineChart(points, { height = 190, highlight = new Set(), format = (v) => v } = {}) {
    const w = 600, padX = 10, padY = 14;
    const vals = points.map((p) => p.v); const min = Math.min(...vals), max = Math.max(...vals); const range = max - min || 1;
    const stepX = points.length > 1 ? (w - padX * 2) / (points.length - 1) : 0;
    const pts = points.map((p, i) => ({ x: padX + i * stepX, y: padY + (height - padY * 2) * (1 - (p.v - min) / range), p }));
    let d = '', area = '';
    pts.forEach((p, i) => {
      if (i === 0) { d = `M${p.x},${p.y}`; area = `M${p.x},${height} L${p.x},${p.y}`; } else {
        const prev = pts[i - 1], cx = (prev.x + p.x) / 2;
        d += ` C${cx},${prev.y} ${cx},${p.y} ${p.x},${p.y}`; area += ` C${cx},${prev.y} ${cx},${p.y} ${p.x},${p.y}`;
      }
    });
    area += ` L${pts[pts.length - 1].x},${height} Z`;
    const grid = [0, 0.5, 1].map((g) => `<line x1="0" x2="${w}" y1="${padY + (height - padY * 2) * g}" y2="${padY + (height - padY * 2) * g}" stroke="var(--line)" stroke-width="1"/>`).join('');
    const dots = pts.map((p, i) => `<circle cx="${p.x}" cy="${p.y}" r="${highlight.has(i) ? 6 : 4}" fill="${highlight.has(i) ? 'var(--gold)' : 'var(--accent)'}"><title>${esc(p.p.label)}: ${esc(format(p.p.v))}</title></circle>`).join('');
    return `<svg class="chart" viewBox="0 0 ${w} ${height}"><defs><linearGradient id="g" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity=".35"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></linearGradient></defs>${grid}<path d="${area}" fill="url(#g)"/><path d="${d}" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>${dots}</svg>` +
      `<div class="chart-labels"><span>${esc(points[0].label)}</span><span>peak ${esc(format(max))}</span><span>${esc(points[points.length - 1].label)}</span></div>`;
  }

  // ---------- render ----------
  function render() {
    const d = state.derived; const snap = state.snap;
    const hour = new Date().getHours();
    $('greeting').textContent = `${hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'}, ${state.me.displayName}`;
    $('userName').textContent = state.me.displayName;
    if (!snap) { $('empty').style.display = ''; $('content').style.display = 'none'; $('subtitle').textContent = 'Nothing backed up yet.'; $('syncBadge').textContent = 'no backup'; return; }
    $('empty').style.display = 'none'; $('content').style.display = '';
    $('syncBadge').textContent = `synced ${ago(snap.exportedAt)}`; $('syncBadge').className = 'badge ok';
    const last = d.completed[0];
    $('subtitle').textContent = last ? `Last workout ${fmtDate(last.startedAt).toLowerCase()} · ${last.name}` : 'No finished workouts yet.';

    const tw = d.thisWeek;
    $('tiles').innerHTML = [
      tile('This week', `${tw.workouts}`, `${tw.sets} sets · ${fmtVol(tw.volume)}`, 'accent'),
      tile('Streak', `${d.streak} wk`, d.streak > 1 ? 'consecutive weeks' : 'keep it going'),
      tile('Workouts', `${d.completed.length}`, 'all time'),
      tile('Lifted', fmtVol(d.totalVolume), 'all time'),
      tile('Records', `${d.prEvents.length}`, d.prEvents[0] ? `latest ${fmtDate(d.prEvents[0].at).toLowerCase()}` : '', 'gold'),
    ].join('');

    $('weekVolume').textContent = fmtVol(tw.volume);
    $('volumeChart').innerHTML = barChart(d.weeks.map((w) => w.volume), d.weeks.map((w) => w.start.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })));

    // heat map: last 16 weeks, Monday-first columns
    const start = startOfWeek(new Date()); start.setDate(start.getDate() - 15 * 7);
    let cells = ''; const today = new Date().toDateString(); let count = 0;
    for (let i = 0; i < 16 * 7; i++) {
      const day = new Date(start); day.setDate(day.getDate() + i);
      const on = d.trainedDays.has(day.toDateString()); if (on) count++;
      const future = day > new Date();
      cells += `<div class="${on ? 'on' : ''}${day.toDateString() === today ? ' today' : ''}" style="${future ? 'opacity:.25' : ''}" title="${day.toLocaleDateString()}"></div>`;
    }
    $('heat').innerHTML = cells; $('heatSummary').textContent = `${count} days`; $('heatStart').textContent = start.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });

    renderWorkouts();
    renderPrs();
    renderExerciseSelect();
    renderBackups();
  }

  const tile = (label, n, sub, cls = '') => `<div class="tile ${cls}"><div class="label">${esc(label)}</div><div class="num">${esc(n)}</div><div class="sub">${esc(sub)}</div></div>`;

  function renderWorkouts() {
    const d = state.derived;
    const list = d.completed.slice(0, state.workoutsShown);
    $('workoutCount').textContent = `${d.completed.length}`;
    $('moreWorkouts').style.display = d.completed.length > state.workoutsShown ? '' : 'none';
    $('workouts').innerHTML = list.map((w) => {
      const sets = (d.setsByWorkout[w.id] || []);
      const working = sets.filter((s) => !s.isWarmup);
      const vol = working.reduce((a, s) => a + s.weightKg * s.reps, 0);
      const prs = sets.filter((s) => d.prSetIds.has(s.id)).length;
      const exercises = [...new Set(sets.map((s) => s.exercise.id))].map((id) => sets.find((s) => s.exercise.id === id).exercise);
      const rows = exercises.map((ex) => {
        const es = sets.filter((s) => s.exercise.id === ex.id);
        return `<tr><td><b>${esc(ex.name)}</b><div class="muted small">${esc(ex.equipment)}</div></td><td>${es.map((s) => `<span style="white-space:nowrap">${s.isWarmup ? '<span class="muted">w</span>' : ''}${num(toUnit(s.weightKg))}×${s.reps}${d.prSetIds.has(s.id) ? ' <span class="pr">★</span>' : ''}</span>`).join(' &nbsp; ')}</td></tr>`;
      }).join('');
      return `<div><div class="row" data-toggle="w-${w.id}"><div class="grow"><div class="title">${esc(w.name)} ${prs ? `<span class="pr">★ ${prs}</span>` : ''}</div><div class="meta">${fmtDate(w.startedAt)} · ${fmtTime(w.startedAt)} · ${fmtDur(w.endedAt - w.startedAt)} · ${exercises.length} exercises · ${working.length} sets</div></div><div class="right">${fmtVol(vol)}</div></div>
        <div class="details" id="w-${w.id}"><table><thead><tr><th>Exercise</th><th>Sets (${unit()} × reps)</th></tr></thead><tbody>${rows || '<tr><td colspan="2" class="muted">No sets</td></tr>'}</tbody></table></div></div>`;
    }).join('');
  }

  function renderPrs() {
    const d = state.derived;
    const rows = Object.values(d.best).sort((a, b) => b.last - a.last).slice(0, 12);
    $('prs').innerHTML = rows.length ? `<table class="plain"><thead><tr><th>Exercise</th><th style="text-align:right">Best</th><th style="text-align:right">Est. 1RM</th></tr></thead><tbody>${rows.map((b) => `<tr class="clickable" data-ex="${b.exercise.id}"><td><b>${esc(b.exercise.name)}</b><div class="muted small">${fmtDate(b.at)}</div></td><td class="num">${fmtW(b.weightKg)} <span class="muted">× ${b.reps}</span></td><td class="num">${fmtW(b.orm)}</td></tr>`).join('')}</tbody></table>` : '<p class="muted">No working sets yet.</p>';
  }

  function renderExerciseSelect() {
    const d = state.derived;
    const list = Object.values(d.best).sort((a, b) => b.last - a.last);
    const sel = $('exerciseSelect');
    sel.innerHTML = list.map((b) => `<option value="${b.exercise.id}">${esc(b.exercise.name)}</option>`).join('');
    if (!state.exerciseId || !d.best[state.exerciseId]) state.exerciseId = list[0]?.exercise.id || null;
    if (state.exerciseId) sel.value = state.exerciseId;
    renderExercise();
  }

  function renderExercise() {
    const d = state.derived; const exId = state.exerciseId;
    if (!exId) { $('exerciseChart').innerHTML = '<p class="muted">Nothing to chart yet.</p>'; $('exerciseSessions').innerHTML = ''; return; }
    const sets = d.sets.filter((s) => s.exercise.id === exId);
    const byWorkout = {}; for (const s of sets) (byWorkout[s.workout.id] ||= []).push(s);
    let best = 0;
    const sessions = Object.values(byWorkout).sort((a, b) => a[0].workout.startedAt - b[0].workout.startedAt).map((list) => {
      const w = list.filter((s) => !s.isWarmup); const use = w.length ? w : list;
      const top = Math.max(...use.map((s) => s.weightKg)); const pr = top > best + 1e-4; if (pr) best = top;
      return { at: list[0].workout.startedAt, workoutId: list[0].workout.id, sets: list, top, orm: Math.max(...use.map((s) => epley(s.weightKg, s.reps))), vol: w.reduce((a, s) => a + s.weightKg * s.reps, 0), pr };
    });
    const b = d.best[exId];
    $('exerciseMeta').textContent = `${b.exercise.equipment} · ${b.exercise.muscleGroup} · best ${fmtW(b.weightKg)} × ${b.reps} · ${sessions.length} sessions`;
    const pick = (s) => (state.metric === 'top' ? s.top : state.metric === 'orm' ? s.orm : s.vol);
    const fmt = state.metric === 'vol' ? fmtVol : fmtW;
    if (sessions.length < 2) $('exerciseChart').innerHTML = `<p class="muted">${sessions.length ? 'One session so far. Do it again to see a trend.' : 'No sessions.'}</p>`;
    else $('exerciseChart').innerHTML = lineChart(sessions.map((s) => ({ v: toUnit(pick(s)), label: fmtDate(s.at) })), { highlight: new Set(state.metric === 'top' ? sessions.map((s, i) => (s.pr ? i : -1)).filter((i) => i >= 0) : []), format: (v) => (state.metric === 'vol' ? `${Math.round(v)} ${unit()}` : `${num(v)} ${unit()}`) });
    $('exerciseSessions').innerHTML = `<table class="plain"><thead><tr><th>Date</th><th>Sets (${unit()} × reps)</th><th style="text-align:right">Top</th><th style="text-align:right">Volume</th></tr></thead><tbody>${sessions.slice().reverse().slice(0, 15).map((s) => `<tr><td>${fmtDate(s.at)}${s.pr ? ' <span class="pr">★</span>' : ''}</td><td>${s.sets.map((x) => `${x.isWarmup ? '<span class="muted">w</span>' : ''}${num(toUnit(x.weightKg))}×${x.reps}`).join(' &nbsp; ')}</td><td class="num">${fmtW(s.top)}</td><td class="num">${fmtVol(s.vol)}</td></tr>`).join('')}</tbody></table>`;
  }

  function renderBackups() {
    const list = state.snapshots;
    $('backupCount').textContent = `${list.length}`;
    $('backups').innerHTML = list.length ? `<table class="plain"><thead><tr><th>Saved</th><th style="text-align:right">Size</th><th></th></tr></thead><tbody>${list.slice(0, 20).map((s, i) => `<tr><td>${new Date(s.at).toLocaleString()} ${i === 0 ? '<span class="badge ok">latest</span>' : ''}</td><td class="num">${(s.bytes / 1024).toFixed(0)} KB</td><td style="text-align:right"><a class="btn btn-sm" href="/api/snapshot/${encodeURIComponent(s.id)}?download=1">Download</a></td></tr>`).join('')}</tbody></table>${list.length > 20 ? `<p class="muted small" style="margin-top:8px">${list.length - 20} older backups not shown.</p>` : ''}` : '<p class="muted">No backups yet.</p>';
  }

  // ---------- connect dialog ----------
  async function openConnect() {
    const r = await api('/api/connect'); const c = await r.json();
    $('connectUrl').textContent = c.url; $('connectToken').textContent = c.token; $('connectLink').href = c.link;
    $('qr').innerHTML = `<img src="/api/connect/qr.svg?t=${Date.now()}" alt="QR code">`;
    $('connectDialog').showModal();
  }
  window.openConnect = openConnect;

  // ---------- events ----------
  document.addEventListener('click', async (e) => {
    const t = e.target.closest('[data-toggle]'); if (t) { $(t.dataset.toggle).classList.toggle('open'); return; }
    const ex = e.target.closest('[data-ex]'); if (ex) { state.exerciseId = ex.dataset.ex; $('exerciseSelect').value = state.exerciseId; renderExercise(); document.querySelector('#exerciseChart').scrollIntoView({ behavior: 'smooth', block: 'center' }); return; }
    const copy = e.target.closest('[data-copy]'); if (copy) { try { await navigator.clipboard.writeText($(copy.dataset.copy).textContent); toast('Copied'); } catch { toast('Copy failed'); } return; }
    const m = e.target.closest('#metricSeg button'); if (m) { state.metric = m.dataset.m; document.querySelectorAll('#metricSeg button').forEach((b) => b.classList.toggle('on', b === m)); renderExercise(); }
  });
  $('exerciseSelect').addEventListener('change', (e) => { state.exerciseId = e.target.value; renderExercise(); });
  $('moreWorkouts').addEventListener('click', () => { state.workoutsShown += 10; renderWorkouts(); });
  $('connectBtn').addEventListener('click', openConnect);
  $('rotateToken').addEventListener('click', async () => {
    if (!confirm('Create a new token? The phone will need to be connected again with the new one.')) return;
    await api('/api/tokens/rotate', { method: 'POST' }); toast('New token created'); openConnect();
  });

  async function load() {
    const me = await (await api('/api/me')).json(); state.me = me;
    const [latest, snaps] = await Promise.all([api('/api/snapshot/latest'), api('/api/snapshots')]);
    state.snapshots = (await snaps.json()).snapshots || [];
    state.snap = latest.status === 200 ? await latest.json() : null;
    state.derived = state.snap ? derive(state.snap) : null;
    render();
  }
  load().catch((e) => { console.error(e); $('subtitle').textContent = 'Could not load: ' + e.message; });
  // Live-ish: refresh when the tab regains focus or every 2 minutes.
  document.addEventListener('visibilitychange', () => { if (!document.hidden) load().catch(() => {}); });
  setInterval(() => load().catch(() => {}), 120000);
})();
