/*
  Sitenin çevirilerini sayfayla karşılaştırır.

      node tools/check-site.js

  Üç şeye bakar:

    * index.html'deki her `data-i18n` anahtarının yirmi yedi dilde de
      karşılığı var mı (eksik anahtar sessizce İngilizce kalır, yani sayfanın
      ortasında dil değişir);
    * bir dilde sayfanın kullanmadığı anahtar kalmış mı (metin silindiğinde
      çevirisi geride kalır ve kimse fark etmez);
    * <link rel="alternate" hreflang> listesi ile dil tablosu aynı kümeyi mi
      gösteriyor (arama motorlarına var olmayan bir dil söylenmesin).

  Sorun varsa hepsini listeler ve çıkış kodu 1 olur.
*/

"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.join(__dirname, "..");
const html = fs.readFileSync(path.join(root, "docs", "index.html"), "utf8");
const source = fs.readFileSync(path.join(root, "docs", "assets", "i18n.js"), "utf8");

// i18n.js bir tarayıcı dosyası: `window` yokken çalışmaz, o yüzden sahte bir
// pencere veriliyor. Dosyada başka hiçbir şey yok, çalıştırmak güvenli.
const sandbox = { window: {} };
vm.createContext(sandbox);
vm.runInContext(source, sandbox, { filename: "i18n.js" });
const table = sandbox.window.COMPASS_I18N;

const problems = [];

function keysIn(attribute) {
  const found = new Set();
  const pattern = new RegExp(attribute + '="([^"]+)"', "g");
  let match;
  while ((match = pattern.exec(html)) !== null) found.add(match[1]);
  return found;
}

// Sayfanın beklediği anahtarlar. _title ile _desc HTML'de öznitelik olarak
// geçmiyor, site.js onları başlık ve açıklama etiketinden alıyor.
const needed = new Set([
  ...keysIn("data-i18n"),
  ...keysIn("data-i18n-alt"),
  "_title",
  "_desc"
]);

const codes = Object.keys(table);
if (!codes.includes("en")) problems.push("tabloda `en` yok");

for (const code of codes) {
  const entry = table[code];
  if (!entry.name) problems.push(`${code}: `+"`name`"+` eksik`);

  // İngilizce index.html'in kendisinde; tabloda yalnızca adı bulunur.
  if (code === "en") {
    const extra = Object.keys(entry).filter((key) => key !== "name");
    if (extra.length) {
      problems.push(`en: tabloda olmaması gereken anahtarlar — ${extra.join(", ")}`);
    }
    continue;
  }

  for (const key of needed) {
    if (typeof entry[key] !== "string" || entry[key].trim() === "") {
      problems.push(`${code}: eksik ya da boş \`${key}\``);
    }
  }
  for (const key of Object.keys(entry)) {
    if (key !== "name" && !needed.has(key)) {
      problems.push(`${code}: sayfanın kullanmadığı anahtar \`${key}\``);
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
  if (!listed.has(code)) problems.push(`hreflang: \`${code}\` sayfada duyurulmuyor`);
}
for (const code of listed) {
  if (!codes.includes(code)) problems.push(`hreflang: \`${code}\` duyuruluyor ama tabloda yok`);
}

if (problems.length) {
  console.error(`${problems.length} sorun:`);
  for (const problem of problems) console.error(`  ${problem}`);
  process.exit(1);
}

console.log(
  `${codes.length} dil, ${needed.size} metin — site çevirileri tutuyor.`
);
