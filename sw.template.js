const CACHE_NAME = 'openword-__CACHE_NAME__';
const CRITICAL_ASSETS = __CRITICAL_ASSETS_PLACEHOLDER__;
const LAZY_ASSETS = __LAZY_ASSETS_PLACEHOLDER__;

// Install: only pre-cache critical assets (HTML, JS, WASM, manifest, icons)
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => Promise.all(
                CRITICAL_ASSETS.map(url =>
                    cache.add(url).catch(err => console.error(`Failed to cache ${url}:`, err))
                )
            ))
            .catch(err => console.error('Fatal error during installation:', err))
    );
});

// Activate: clean old caches, claim clients immediately
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

// Fetch: cache-first for all, runtime-cache lazy assets on first access
self.addEventListener('fetch', (event) => {
    if (event.request.method !== 'GET') return;
    event.respondWith(
        caches.match(event.request).then(cached => {
            if (cached) return cached;
            return fetch(event.request).then(response => {
                if (response && response.status === 200 && response.type === 'basic') {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            });
        }).catch(() => caches.match(event.request))
    );
});

self.addEventListener('message', (event) => {
    if (event.data?.action === 'skipWaiting') {
        self.skipWaiting();
    }
});