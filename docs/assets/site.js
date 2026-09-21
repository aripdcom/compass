/*
  Sayfanın dili.
  ---------------------------------------------------------------------------
  Uygulamada dil ayarı yok, sistemin dili kullanılıyor. Sitede de aynısı:
  sayfa tarayıcının diliyle açılır. Sıra şu:

      1. adresteki ?lang=xx        (paylaşılan bağlantı; her şeyi ezer)
      2. daha önce yapılmış seçim  (localStorage)
      3. navigator.languages       (tarayıcının, yani sistemin dili)
      4. İngilizce

  İngilizce metinler ayrı bir tabloda değil, index.html'in içinde duruyor.
  Burada ilk yüklemede anlık görüntüleri alınıyor: hem İngilizceye dönüş hem
  de bir dilde eksik kalan anahtarın karşılığı o görüntüden geliyor. Böylece
  aynı cümle iki dosyada birden tutulmuyor.
*/

(function () {
  "use strict";

  var TABLE = window.COMPASS_I18N || {};
  var DEFAULT = "en";
  var STORE_KEY = "kerteriz.lang";

  // Birbirinin yerine geçen kodlar: Norveççenin eski kodu `no`, Endonezce ve
  // İbranicenin eski kodları da tarayıcılarda hâlâ görülüyor. Bizi ilgilendiren
  // Norveççe: `no` ve `nb` aynı yazı dilini gösteriyor.
  var ALIAS = { no: "nb", nob: "nb", nno: "nn" };

  var english = null;   // index.html'den alınan anlık görüntü

  function snapshot() {
    var shot = {
      _title: document.title,
      _desc: metaDescription() ? metaDescription().content : ""
    };
    each("[data-i18n]", function (node) {
      var key = node.getAttribute("data-i18n");
      if (!(key in shot)) shot[key] = node.textContent;
    });
    each("[data-i18n-alt]", function (node) {
      var key = node.getAttribute("data-i18n-alt");
      if (!(key in shot)) shot[key] = node.getAttribute("alt") || "";
    });
    return shot;
  }

  function each(selector, fn) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i++) fn(nodes[i]);
  }

  function metaDescription() {
    return document.querySelector('meta[name="description"]');
  }

  function known(code) {
    if (!code) return null;
    code = String(code).toLowerCase().replace("_", "-");
    if (ALIAS[code]) code = ALIAS[code];
    if (code === DEFAULT || TABLE[code]) return code;
    // "de-AT" → "de", "pt-BR" → "pt"
    var base = code.split("-")[0];
    if (ALIAS[base]) base = ALIAS[base];
    if (base === DEFAULT || TABLE[base]) return base;
    return null;
  }

  function stored() {
    try { return known(localStorage.getItem(STORE_KEY)); } catch (e) { return null; }
  }

  function remember(code) {
    try { localStorage.setItem(STORE_KEY, code); } catch (e) { /* özel sekme */ }
  }

  function fromUrl() {
    var match = /[?&]lang=([\w-]+)/.exec(location.search);
    return match ? known(decodeURIComponent(match[1])) : null;
  }

  function fromBrowser() {
    var wanted = navigator.languages || [navigator.language];
    for (var i = 0; i < wanted.length; i++) {
      var hit = known(wanted[i]);
      if (hit) return hit;
    }
    return null;
  }

  function text(code, key) {
    var entry = code === DEFAULT ? null : TABLE[code];
    if (entry && typeof entry[key] === "string" && entry[key] !== "") return entry[key];
    return english[key] !== undefined ? english[key] : "";
  }

  function apply(code) {
    document.documentElement.lang = code;
    document.title = text(code, "_title");
    var desc = metaDescription();
    if (desc) desc.content = text(code, "_desc");

    each("[data-i18n]", function (node) {
      node.textContent = text(code, node.getAttribute("data-i18n"));
    });
    each("[data-i18n-alt]", function (node) {
      node.setAttribute("alt", text(code, node.getAttribute("data-i18n-alt")));
    });

    // Seçili dili bağlantı listesinde de işaretle.
    each("#langList a", function (link) {
      link.setAttribute("aria-current", link.dataset.code === code ? "true" : "false");
    });

    var select = document.getElementById("lang");
    if (select) select.value = code;
  }

  function codes() {
    var list = [DEFAULT];
    for (var code in TABLE) {
      if (Object.prototype.hasOwnProperty.call(TABLE, code) && code !== DEFAULT) list.push(code);
    }
    // Kendi dilindeki adına göre sırala: listeye bakan kendi dilini arar.
    return list.sort(function (a, b) {
      return name(a).localeCompare(name(b), "en");
    });
  }

  function name(code) {
    return (TABLE[code] && TABLE[code].name) || code;
  }

  function buildPicker(all, current) {
    var form = document.getElementById("picker");
    var select = document.getElementById("lang");
    if (!form || !select) return;

    all.forEach(function (code) {
      var option = document.createElement("option");
      option.value = code;
      option.textContent = name(code);
      option.lang = code;
      select.appendChild(option);
    });
    select.value = current;
    form.hidden = false;

    select.addEventListener("change", function () {
      choose(select.value);
    });
    // JavaScript varken form gönderilmesin; değişim yeterli.
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      choose(select.value);
    });
  }

  function buildList(all) {
    var list = document.getElementById("langList");
    if (!list) return;

    all.forEach(function (code) {
      var item = document.createElement("li");
      var link = document.createElement("a");
      link.href = "?lang=" + code;
      link.hreflang = code;
      link.lang = code;
      link.textContent = name(code);
      link.dataset.code = code;
      link.addEventListener("click", function (event) {
        event.preventDefault();
        choose(code);
      });
      item.appendChild(link);
      list.appendChild(item);
    });
  }

  function choose(code) {
    code = known(code) || DEFAULT;
    remember(code);
    apply(code);
    // Adres çubuğu seçimi göstersin ki bağlantı paylaşılabilsin.
    if (history.replaceState) {
      var url = location.pathname + "?lang=" + code + location.hash;
      history.replaceState(null, "", url);
    }
  }

  function start() {
    english = snapshot();

    var urlChoice = fromUrl();
    var current = urlChoice || stored() || fromBrowser() || DEFAULT;
    if (urlChoice) remember(urlChoice);

    var all = codes();
    buildPicker(all, current);
    buildList(all);
    apply(current);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
