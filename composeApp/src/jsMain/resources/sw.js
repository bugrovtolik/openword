const CACHE_NAME = 'openword-v4';

const PRECACHE_URLS = [
    './',
    'index.html',
    'composeApp.js',
    'manifest.json',
    'icons/icon-light-512.png',
    'icons/icon-dark-512.png',
    'icons/icon-192.png',
    'icons/icon-512.png',
    'icons/icon-mono.png',
    'https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.12.0/sql-wasm.js'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            // We use individual fetches so one 404 doesn't kill the whole PWA installation
            return Promise.allSettled(
                PRECACHE_URLS.map(url =>
                    fetch(url).then(res => {
                        if (res.ok) return cache.put(url, res);
                    }).catch(err => console.error('SW: Precache failed for', url, err))
                )
            );
        }).then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then(keys => Promise.all(
            keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
        )).then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const url = event.request.url;

    // FIX: Skip non-HTTP schemes (chrome-extension, etc.)
    if (!url.startsWith('http')) return;
    // Skip non-GET requests
    if (event.request.method !== 'GET') return;

    event.respondWith(
        caches.match(event.request).then(cached => {
            if (cached) return cached;

            return fetch(event.request).then(response => {
                // Only cache valid, same-origin responses to be safe
                if (response && response.status === 200 && response.type === 'basic') {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            }).catch(() => caches.match(event.request));
        })
    );
});