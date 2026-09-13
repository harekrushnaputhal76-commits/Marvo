/* ================================================================
   MARVO AI — service-worker.js
   Production-Grade PWA Service Worker (PWABuilder 100% Compliant)
   - Cache-First for static assets & icons
   - Network-First for navigation and HTML documents
   - Network-Only for /api/ routes
   - Guaranteed offline app shell fallback
   ================================================================ */

const CACHE_NAME = 'marvo-pwa-v2';

const PRECACHE_ASSETS = [
  '/',
  '/index.html',
  '/style.css',
  '/app.js',
  '/manifest.json',
  '/icon.svg',
  '/icon-72.png',
  '/icon-96.png',
  '/icon-128.png',
  '/icon-144.png',
  '/icon-152.png',
  '/icon-192.png',
  '/icon-384.png',
  '/icon-512.png',
  '/icon-maskable-192.png',
  '/icon-maskable-512.png',
  '/screenshot-mobile.png',
  '/screenshot-desktop.png'
];

// 1. Install Event: Cache entire application shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(PRECACHE_ASSETS).catch((err) => {
        console.warn('[Marvo SW] Precache warning:', err);
      });
    }).then(() => self.skipWaiting())
  );
});

// 2. Activate Event: Clean up outdated caches & claim clients immediately
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// 3. Fetch Event: Intelligent multi-strategy router
self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // A. Non-GET or Backend API calls: Network-only, bypass SW
  if (req.method !== 'GET' || url.pathname.startsWith('/api/')) {
    return;
  }

  // B. Navigation requests (HTML pages): Network-First, fallback to cached index.html
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((networkRes) => {
          if (networkRes && networkRes.status === 200) {
            const clone = networkRes.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(req, clone));
          }
          return networkRes;
        })
        .catch(() => {
          return caches.match('/index.html')
            .then((cached) => cached || caches.match('/'));
        })
    );
    return;
  }

  // C. Static assets (CSS, JS, Images, Manifest): Cache-First with Network fallback
  event.respondWith(
    caches.match(req).then((cachedRes) => {
      if (cachedRes) {
        // Optional background refresh (Stale-While-Revalidate)
        fetch(req).then((networkRes) => {
          if (networkRes && networkRes.status === 200) {
            caches.open(CACHE_NAME).then((cache) => cache.put(req, networkRes));
          }
        }).catch(() => {});
        return cachedRes;
      }

      // If not in cache, fetch from network and store
      return fetch(req).then((networkRes) => {
        if (networkRes && networkRes.status === 200 && networkRes.type === 'basic') {
          const clone = networkRes.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, clone));
        }
        return networkRes;
      }).catch((err) => {
        console.warn('[Marvo SW] Fetch failed for:', req.url, err);
      });
    })
  );
});
