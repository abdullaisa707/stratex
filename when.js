// Understands spoken/typed Arabic (Gulf + MSA) dates and times, e.g.
// "الخميس القادم عندي كويز الساعة 4 العصر" → {date:'2026-11-26', time:'16:00', rest:'كويز'}
(function (root) {
  const A = '[اأإآ]';          // any alef
  const H = '[ةه]';            // taa marbuta / haa
  const Y = '[يى]';
  const re = s => s.replace(/A/g, A).replace(/H/g, H).replace(/Y/g, Y);

  const NUM_WORDS = [
    [re('(?:وAحد|وحدH|وحدة)'), 1], [re('(?:Aثنين|ثنين|ثنتين|Aثنتين)'), 2], [re('ثلاثH?'), 3], [re('Aربع(?:H)?'), 4],
    [re('خمس(?:H)?'), 5], [re('ست(?:H)?'), 6], [re('سبع(?:H)?'), 7], [re('ثمAن(?:يH)?|ثمنيH'), 8],
    [re('تسع(?:H)?'), 9], [re('عشر(?:H)?'), 10], [re('(?:Aحدعش|حدعش|حدAعش|Aحد(?:Y)? عشر(?:H)?)'), 11],
    [re('(?:Aثنعش|ثنعش|Aثنا عشر)'), 12]
  ];
  const NUM_ALT = NUM_WORDS.map(w => w[0]).join('|');
  function wordNum(s) {
    if (/^\d+$/.test(s)) return +s;
    for (const [w, n] of NUM_WORDS) if (new RegExp('^(?:' + w + ')$').test(s)) return n;
    return null;
  }

  const MONTHS = [
    [re('ينAير|جنوري|يناير|كAنون Aلثاني'), 1], [re('فبرAير|فبراير|شبAط'), 2], [re('مAرس|Aذار|آذار'), 3],
    [re('Aبريل|نيسAن'), 4], [re('مAيو|Aيار'), 5], [re('يونيو|حزيرAن'), 6], [re('يوليو|تموز'), 7],
    [re('Aغسطس|Aب(?=\\s|$)'), 8], [re('سبتمبر|Aيلول'), 9], [re('Aكتوبر|تشرين Aلاول|تشرين Aلأول'), 10],
    [re('نوفمبر|تشرين Aلثاني'), 11], [re('ديسمبر|كAنون Aلاول|كانون الأول'), 12]
  ];
  const MONTH_ALT = MONTHS.map(m => m[0]).join('|');
  function monthNum(s) { for (const [w, n] of MONTHS) if (new RegExp('^(?:' + w + ')$').test(s)) return n; return null; }

  const DAYS = [[re('(?:Aل)?Aحد'), 0], [re('(?:Aل)?(?:Aثنين|ثنين)'), 1], [re('(?:Aل)?ثلاثAء'), 2], [re('(?:Aل)?Aربعاء|(?:Aل)?Aربعا'), 3],
    [re('(?:Aل)?خميس'), 4], [re('(?:Aل)?جمعH'), 5], [re('(?:Aل)?سبت'), 6]];

  const pad = n => String(n).padStart(2, '0');
  const iso = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  const addDays = (d, k) => { const x = new Date(d); x.setDate(x.getDate() + k); return x; };

  function parseWhen(input, now) {
    now = now ? new Date(now) : new Date();
    const today = new Date(now); today.setHours(0, 0, 0, 0);
    let text = ' ' + String(input).replace(/[٠-٩]/g, c => '٠١٢٣٤٥٦٧٨٩'.indexOf(c)).replace(/[ً-ْ]/g, '') + ' ';
    let date = null, time = null;
    const cut = (m) => { text = text.replace(m, ' '); };
    const B = '(?<=^|\\s|[،,.])';  // word start
    const E = '(?=$|\\s|[،,.؟?!])'; // word end
    const NEXT = re('(?:\\s+(?:Aل)?(?:قAدم|جAي|يAي|جAيH|قAدمH|Aلي جAي|اللي جاي|الياي|اللي بعده))');

    // --- explicit dates: 15/12/2026, 15-12
    let m = text.match(/(\d{1,2})\s*[\/\-.]\s*(\d{1,2})(?:\s*[\/\-.]\s*(\d{2,4}))?/);
    if (m) {
      let y = m[3] ? +m[3] : null; if (y && y < 100) y += 2000;
      date = mk(+m[1], +m[2], y); cut(m[0]);
    }
    // --- "15 ديسمبر 2026", "يوم 15 من شهر 12", "15 من الشهر 12"
    if (!date) {
      m = text.match(new RegExp(B + re('(?:يوم\\s+)?(\\d{1,2})\\s+(?:من\\s+)?(?:(?:Aل)?شهر\\s+(\\d{1,2})|(' + MONTH_ALT + '))(?:\\s+(?:سنH\\s+|عAم\\s+)?(\\d{4}))?') + E));
      if (m) { date = mk(+m[1], m[2] ? +m[2] : monthNum(m[3]), m[4] ? +m[4] : null); cut(m[0]); }
    }
    // --- relative words
    if (!date) {
      const rel = [
        [re('بعد\\s+(?:بكرH|باجر|باكر|غدA)'), 2], [re('(?:بكرH|باجر|باكر|غدAً?|بكرا)'), 1],
        [re('(?:Aليوم|هAليوم|هذA Aليوم)'), 0], [re('(?:Aلاسبوع|Aلأسبوع)\\s+(?:Aلقادم|Aلجاي|Aلياي)'), 7], [re('بعد\\s+(?:Aسبوع|أسبوع)'), 7],
        [re('بعد\\s+يومين'), 2]
      ];
      for (const [r, k] of rel) { m = text.match(new RegExp(B + r + E)); if (m) { date = addDays(today, k); cut(m[0]); break; } }
    }
    if (!date) {
      m = text.match(new RegExp(B + 'بعد\\s+(\\d+|' + NUM_ALT + ')\\s+' + re('(?:Aيام|يوم|أيام)') + E));
      if (m) { date = addDays(today, wordNum(m[1])); cut(m[0]); }
    }
    // --- weekday names, optionally "القادم/الجاي"
    if (!date) {
      for (const [d, wd] of DAYS) {
        m = text.match(new RegExp(B + '(?:يوم\\s+)?(?:' + d + ')(' + NEXT + ')?' + E));
        if (m) {
          let k = (wd - today.getDay() + 7) % 7;
          if (k === 0 && m[1]) k = 7;
          date = addDays(today, k); cut(m[0]); break;
        }
      }
    }
    // --- "يوم 15" (this month, or next month if it already passed)
    if (!date) {
      m = text.match(new RegExp(B + 'يوم\\s+(\\d{1,2})' + E));
      if (m) { date = mk(+m[1], null, null); cut(m[0]); }
    }

    // --- time: "الساعة 4", "4:30 العصر", "الساعة اربع ونص الصبح"
    const PERIOD = re('(Aلصبح|صبAحAً?|Aلصباح|الفجر|Aلظهر|ظهرAً?|Aلعصر|عصرAً?|بعد Aلظهر|Aلمغرب|مسAءً?|Aلمسا|Aلمساء|بالليل|Aلليل|في Aلليل|ص|م)');
    const FRAC = re('(?:\\s*(?:و\\s*)?(نص|نصف|ربع|ثلث|Aلا\\s*ربع|إلا\\s*ربع))?');
    const T = '(\\d{1,2}(?::\\d{2})?|' + NUM_ALT + ')' + FRAC;
    m = text.match(new RegExp(B + re('(?:Aلساع[ةه]|ساع[ةه])') + '\\s+' + T + '(?:\\s+' + PERIOD + E + ')?'))
      || text.match(new RegExp(B + T + '\\s+' + PERIOD + E));
    if (m) {
      let h, min = 0;
      if (m[1].includes(':')) { [h, min] = m[1].split(':').map(Number); } else h = wordNum(m[1]);
      const f = m[2] || '';
      if (/نص/.test(f)) min = 30; else if (/(ا|إ)لا/.test(f)) { min = 45; h -= 1; } else if (/ربع/.test(f)) min = 15; else if (/ثلث/.test(f)) min = 20;
      const p = m[3] || '';
      if (h != null && h >= 0 && h <= 24) {
        if (/صب|صباح|فجر|^ص$/.test(p)) { if (h === 12) h = 0; }
        else if (/ظهر/.test(p) && !/بعد/.test(p)) { if (h < 6) h += 12; }
        else if (p) { if (h < 12) h += 12; if (/ليل/.test(p) && h === 24) h = 0; }
        else if (h >= 1 && h <= 7) h += 12;   // no period: 1–7 most likely afternoon
        if (h === 24) h = 0;
        if (h === 0 && h - 1 < 0 && /(ا|إ)لا/.test(f)) h = 23;
        time = pad(h) + ':' + pad(min); cut(m[0]);
      }
    }

    if (time && !date) {
      const [hh, mm] = time.split(':').map(Number);
      const t = new Date(today); t.setHours(hh, mm);
      date = t > now ? today : addDays(today, 1);
    }

    // --- tidy the remaining words into a title
    let rest = text.replace(new RegExp(B + re('(?:ذكرني|فكرني|سجل|سجلي|Aكتب|حط)') + E, 'g'), ' ')
      .replace(/\s+/g, ' ').trim();
    const FILL = re('(?:عندي|عندنا|في|فيه|يوم|Aلساع[ةه]|و|على|ب|من|بتاريخ|تاريخ|موعد|ان|إن|Aنه)');
    for (let i = 0; i < 3; i++) rest = rest.replace(new RegExp('^' + FILL + '\\s+|\\s+' + FILL + '$', 'g'), '').trim();
    rest = rest.replace(/^[،,.\s]+|[،,.\s]+$/g, '');
    return { date: date ? iso(date) : null, time, rest };

    function mk(d, mo, y) {
      if (!d || d > 31) return null;
      let year = y || today.getFullYear(), month = mo ? mo - 1 : today.getMonth();
      let x = new Date(year, month, d);
      if (!y && x < today) x = mo ? new Date(year + 1, month, d) : new Date(year, month + 1, d);
      return x;
    }
  }

  // Guess a category from words like "كويز" / "واجب" / "محاضرة".
  function guessCat(text) {
    if (new RegExp(re('كويز|Aمتحان|Aختبار|ميد|فAينل|كوز|تست')).test(text)) return 'امتحانات';
    if (new RegExp(re('وAجب|تسليم|مشروع|تقرير|بريزنتيشن|عرض تقديمي|Aسايمنت')).test(text)) return 'واجبات';
    if (new RegExp(re('محAضر[ةه]|كلاس|لكتشر|درس')).test(text)) return 'محاضرات';
    return null;
  }

  root.parseWhen = parseWhen; root.guessCat = guessCat;
  if (typeof module !== 'undefined') module.exports = { parseWhen, guessCat };
})(typeof window !== 'undefined' ? window : globalThis);
