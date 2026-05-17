const CACHE_NAME = 'pdflow-v1';
const CACHE_STATIC = [
  '/',
  '/index.html',
  '/manifest.json',
  '/i18n/fr.json',
  '/i18n/en.json',
  '/i18n/ar.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  'https://fonts.googleapis.com/css2?family=Syne:wght@400;600;700;800&family=DM+Sans:wght@300;400;500&display=swap'
];

// Installation - mise en cache des ressources statiques
self.addEventListener('install', event => {
  console.log('[SW] Installation...');
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      console.log('[SW] Mise en cache des ressources');
      return cache.addAll(CACHE_STATIC.map(url => {
        return new Request(url, {mode: 'no-cors'});
      }));
    }).then(() => self.skipWaiting())
  );
});

// Activation - nettoyage des anciens caches
self.addEventListener('activate', event => {
  console.log('[SW] Activation...');
  event.waitUntil(
    caches.keys().then(keys => 
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

// Fetch - stratégie Network First pour API, Cache First pour statique
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  
  // API : toujours réseau, pas de cache
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(event.request).catch(() => 
        new Response(JSON.stringify({erreur: 'Hors ligne - fonctionnalité indisponible'}), 
          {headers: {'Content-Type': 'application/json'}})
      )
    );
    return;
  }
  
  // Ressources statiques : Cache First, puis réseau
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(response => {
        // Mettre en cache les nouvelles ressources statiques
        if (response.ok && event.request.method === 'GET') {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      }).catch(() => {
        // Fallback hors ligne : retourner la page principale
        if (event.request.headers.get('accept').includes('text/html')) {
          return caches.match('/index.html');
        }
      });
    })
  );
});

// Message du client
self.addEventListener('message', event => {
  if (event.data === 'skipWaiting') self.skipWaiting();
});
