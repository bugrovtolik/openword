const CACHE_NAME = 'openword-1774704558301';
const ASSETS = [
  "",
  "236.js",
  "bccfa839aa4b38489c76.wasm",
  "composeApp.js",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/CBSC.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/CUV.commentaries.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/GRM.commentaries.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/IVP.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/NPU.commentaries.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/UBIO.commentaries.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/UMT.commentaries.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/benson.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/constable.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/dallas.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/edwards.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/commentaries/macarthur.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/crossreferences/GRM.crossreferences.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/CUV.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/GRM.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/HOM.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/KJV.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/MSC.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/NPU.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/NUP.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/UBIO.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/UKRK.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/translations/UMT.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/vocabulary/GRM.dictionary.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/vocabulary/lexicon.SQLite3",
  "composeResources/openword.composeapp.generated.resources/files/vocabulary/wordsDefinitions.SQLite3",
  "composeResources/openword.composeapp.generated.resources/font/OpenSans.ttf",
  "favicon.ico",
  "icons/icon-192.png",
  "icons/icon-32.png",
  "icons/icon-512.png",
  "icons/icon-dark-512.png",
  "icons/icon-mono.png",
  "index.html",
  "js-reexport-symbols.mjs",
  "manifest.json",
  "skiko.mjs",
  "skiko.wasm",
  "skikod8.mjs",
  "sql-wasm.js",
  "sql-wasm.wasm",
  "sw.template.js"
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => Promise.all(ASSETS.map(url => cache.add(url).catch(err => console.error(`Failed to cache ${url}:`, err)))))
            .catch(err => console.error('Fatal error during installation:', err))
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        Promise.all([
            clients.claim(),
            caches.keys().then(keys =>
                Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key)))
            )
        ])
    );
});

self.addEventListener('fetch', (event) => {
    if (event.request.method !== 'GET') return;
    event.respondWith(
        caches.match(event.request)
            .then(cached => cached || fetch(event.request))
            .catch(() => caches.match(event.request))
    );
});

self.addEventListener('message', (event) => {
    if (event.data?.action === 'skipWaiting') {
        self.skipWaiting();
    }
});