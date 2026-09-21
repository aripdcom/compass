/*
  Sitenin çevirilerini sayfalarla karşılaştırır.

      node tools/check-site.js

  İki sayfa var ve her birinin kendi çeviri tablosu: ana sayfa ile gizlilik
  sayfası. Ayrı tutulmalarının sebebi ağırlık (bkz. privacy-i18n.js'in başı),
  ama ayrılan şey er geç ayrışır — bu betik de onu engellemek için var.

  Her sayfa için üç şeye bakılıyor:

    * sayfadaki her `data-i18n` anahtarının yirmi yedi dilde de karşılığı var
      mı (eksik anahtar sessizce İngilizce kalır, yani sayfanın ortasında dil
      değişir);
    * bir dilde sayfanın kullanmadığı anahtar kalmış mı (metin silindiğinde
      çevirisi geride kalır ve kimse fark etmez);
    * <link rel="alternate" hreflang> listesi ile dil tablosu aynı kümeyi mi
      gösteriyor (arama motorlarına var olmayan bir dil söylenmesin).

  Sonra da iki tablo birbirine karşı: aynı diller, aynı `name` değerleri. Dil
  seçici iki sayfada aynı listeyi göstermeli, yoksa dil değiştiren okur
  sayfalar arasında geçerken listeyi değişmiş bulur.

  Sorun varsa hepsini listeler ve çıkış kodu 1 olur.
*/

"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.join(__dirname, "..");

const PAGES = [
  { label: "index.html", html: "docs/index.html", table: "docs/assets/i18n.js" },
  {
    label: "privacy/index.html",
    html: "docs/privacy/index.html",
    table: "docs/assets/privacy-i18n.js"
  }
];

const problems = [];

// Çeviri dosyaları birer tarayıcı dosyası: `window` yokken çalışmazlar, o
// yüzden sahte bir pencere veriliyor. İçlerinde başka hiçbir şey yok,
// çalıştırmak güvenli.
function loadTable(relative) {
  const sandbox = { window: {} };
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(path.join(root, relative), "utf8"), sandbox, {
    filename: path.basename(relative)
  });
  return sandbox.window.COMPASS_I18N;
}

function keysIn(html, attribute) {
  const found = new Set();
  const pattern = new RegExp(attribute + '="([^"]+)"', "g");
  let match;
  while ((match = pattern.exec(html)) !== null) found.add(match[1]);
  return found;
}

function checkPage(page) {
  const html = fs.readFileSync(path.join(root, page.html), "utf8");
  const table = loadTable(page.table);
  const where = page.label;

  // Sayfanın beklediği anahtarlar. _title ile _desc HTML'de öznitelik olarak
  // geçmiyor, site.js onları başlık ve açıklama etiketinden alıyor.
  const needed = new Set([
    ...keysIn(html, "data-i18n"),
    ...keysIn(html, "data-i18n-alt"),
    "_title",
    "_desc"
  ]);

  const codes = Object.keys(table);
  if (!codes.includes("en")) problems.push(`${where}: tabloda \`en\` yok`);

  for (const code of codes) {
    const entry = table[code];
    if (!entry.name) problems.push(`${where} ${code}: `+"`name`"+` eksik`);

    // İngilizce sayfanın kendisinde; tabloda yalnızca adı bulunur.
    if (code === "en") {
      const extra = Object.keys(entry).filter((key) => key !== "name");
      if (extra.length) {
        problems.push(
          `${where} en: tabloda olmaması gereken anahtarlar — ${extra.join(", ")}`
        );
      }
      continue;
    }

    for (const key of needed) {
      if (typeof entry[key] !== "string" || entry[key].trim() === "") {
        problems.push(`${where} ${code}: eksik ya da boş \`${key}\``);
      }
    }
    for (const key of Object.keys(entry)) {
      if (key !== "name" && !needed.has(key)) {
        problems.push(`${where} ${code}: sayfanın kullanmadığı anahtar \`${key}\``);
      }
    }
  }

  // hreflang listesi ile tablo aynı dilleri göstermeli.
  const listed = new Set();
  const hreflang = /hreflang="([\w-]+)"/g;
  let match;
  while ((match = hreflang.exec(html)) !== null) {
    if (match[1] !== "x-default") listed.add(match[1]);
  }
  for (const code of codes) {
    if (!listed.has(code)) problems.push(`${where} hreflang: \`${code}\` sayfada duyurulmuyor`);
  }
  for (const code of listed) {
    if (!codes.includes(code)) problems.push(`${where} hreflang: \`${code}\` duyuruluyor ama tabloda yok`);
  }

  return { table, needed };
}

const checked = PAGES.map((page) => ({ page, ...checkPage(page) }));

// İki tablo aynı dil listesini ve aynı dil adlarını taşımalı.
const [first, ...rest] = checked;
for (const other of rest) {
  const a = Object.keys(first.table);
  const b = Object.keys(other.table);
  for (const code of a) {
    if (!b.includes(code)) {
      problems.push(`${other.page.label}: \`${code}\` eksik (${first.page.label} içinde var)`);
    } else if (first.table[code].name !== other.table[code].name) {
      problems.push(
        `${code}: dil adı iki tabloda ayrı — ` +
          `${first.page.label} "${first.table[code].name}", ` +
          `${other.page.label} "${other.table[code].name}"`
      );
    }
  }
  for (const code of b) {
    if (!a.includes(code)) {
      problems.push(`${first.page.label}: \`${code}\` eksik (${other.page.label} içinde var)`);
    }
  }
}

if (problems.length) {
  console.error(`${problems.length} sorun:`);
  for (const problem of problems) console.error(`  ${problem}`);
  process.exit(1);
}

const summary = checked
  .map((item) => `${item.page.label}: ${item.needed.size} metin`)
  .join(", ");
console.log(
  `${Object.keys(first.table).length} dil — ${summary} — site çevirileri tutuyor.`
);
