# Système de Gestion de Documents PDF avec CORBA

## 1. Présentation du projet

**PDFlow** est une application de gestion de documents PDF développée dans le cadre d'un TP sur la technologie CORBA (Common Object Request Broker Architecture). L'application permet à plusieurs clients de se connecter à un serveur centralisé pour effectuer diverses opérations sur des fichiers PDF, en utilisant PDFBox comme bibliothèque de traitement.

---

## 2. Architecture générale

```
┌─────────────────────────────────────────────────────────────┐
│                    Clients                                   │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Client JavaFX│  │ Interface Web│  │  Client Python   │  │
│  │  (CORBA)     │  │  (HTML/JS)   │  │  (HTTP REST)     │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
└─────────┼────────────────┼──────────────────┼──────────────┘
          │ CORBA/IDL      │ HTTP REST        │ HTTP REST
          ▼                ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Serveur Java                              │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              StartServerWeb.java                     │    │
│  │   ┌─────────────────┐  ┌──────────────────────┐    │    │
│  │   │  Pont HTTP REST  │  │  Serveur CORBA       │    │    │
│  │   │  (port 8080)     │  │  (port 1050)         │    │    │
│  │   └────────┬─────────┘  └──────────────────────┘    │    │
│  │            │                                          │    │
│  │   ┌────────▼─────────────────────────────────────┐  │    │
│  │   │         GestionnairePDFImpl.java              │  │    │
│  │   │              (PDFBox)                         │  │    │
│  │   └───────────────────────────────────────────────┘  │    │
│  │   ┌─────────────────┐  ┌──────────────────────────┐  │    │
│  │   │  AuthManager    │  │     EmailService          │  │    │
│  │   │  (PostgreSQL)   │  │     (Brevo API)           │  │    │
│  │   └─────────────────┘  └──────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Stockage                                  │
│   ┌──────────────────┐      ┌──────────────────────────┐   │
│   │  Fichiers PDF     │      │  PostgreSQL (Render)     │   │
│   │  /pdfs/{user}/   │      │  - utilisateurs          │   │
│   └──────────────────┘      │  - sessions              │   │
│                              └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Technologies utilisées

| Technologie | Version | Rôle |
|-------------|---------|------|
| Java | 8 (Zulu FX) | Langage principal |
| CORBA/IDL | Java 8 built-in | Communication distribuée |
| PDFBox | 2.0.31 | Manipulation des PDFs |
| JavaFX | 8 (Zulu FX) | Interface graphique Java |
| HTML/CSS/JS | Vanilla | Interface web |
| PostgreSQL | 42.7.3 (JDBC) | Base de données utilisateurs |
| SQLite | 3.36.0 | Base de données locale |
| Brevo API | v3 | Envoi d'emails |
| Docker | - | Conteneurisation |
| Render | - | Déploiement cloud |
| GitHub | - | Gestion de version |
| Python | 3.12 | Client Python |

---

## 4. Fichier IDL — Interface CORBA

Le fichier `PDFService.idl` définit le contrat entre clients et serveur :

```idl
module PDFService {
    exception PDFException { string message; };

    struct InfosPDF {
        string nomFichier;
        long nombrePages;
        string auteur;
        string titre;
        long tailleFichier;
    };

    typedef sequence<octet> Fichier;
    typedef sequence<string> ListeTextes;
    typedef sequence<long> ListePages;

    interface GestionnairePDF {
        InfosPDF getInfos(in string nomFichier) raises (PDFException);
        Fichier fusionner(in string nom1, in string nom2, in string nomResultat) raises (PDFException);
        Fichier extrairePages(in string nomFichier, in long pageDebut, in long pageFin, in string nomResultat) raises (PDFException);
        Fichier supprimerPages(in string nomFichier, in ListePages pages, in string nomResultat) raises (PDFException);
        Fichier ajouterTexte(in string nomFichier, in string texte, in long page, in float x, in float y, in string nomResultat) raises (PDFException);
        ListeTextes convertirEnImages(in string nomFichier, in long dpi) raises (PDFException);
        ListeTextes extraireTexte(in string nomFichier) raises (PDFException);
        Fichier creerPDF(in string texte, in string nomResultat) raises (PDFException);
        boolean uploadFichier(in string nomFichier, in Fichier contenu) raises (PDFException);
        Fichier downloadFichier(in string nomFichier) raises (PDFException);
        ListeTextes listerFichiers() raises (PDFException);
        Fichier rotationPages(in string nomFichier, in ListePages pages, in long angle, in string nomResultat) raises (PDFException);
        Fichier protegerPDF(in string nomFichier, in string motDePasse, in string nomResultat) raises (PDFException);
        Fichier compresserPDF(in string nomFichier, in string nomResultat) raises (PDFException);
        Fichier numeroterPages(in string nomFichier, in string nomResultat) raises (PDFException);
        string apercuPage(in string nomFichier, in long page) raises (PDFException);
    };
};
```

---

## 5. Fonctionnalités implémentées

### 5.1 Opérations PDF (côté serveur — GestionnairePDFImpl.java)

| # | Opération | Description | Statut |
|---|-----------|-------------|--------|
| 1 | `getInfos` | Récupère les métadonnées d'un PDF | ✅ |
| 2 | `fusionner` | Fusionne deux PDFs en un seul | ✅ |
| 3 | `extrairePages` | Extrait une plage de pages | ✅ |
| 4 | `supprimerPages` | Supprime des pages spécifiques | ✅ |
| 5 | `ajouterTexte` | Ajoute du texte sur une page | ✅ |
| 6 | `convertirEnImages` | Convertit chaque page en PNG | ✅ |
| 7 | `extraireTexte` | Extrait le contenu textuel | ✅ |
| 8 | `creerPDF` | Crée un PDF depuis du texte | ✅ |
| 9 | `uploadFichier` | Upload un fichier vers le serveur | ✅ |
| 10 | `downloadFichier` | Télécharge un fichier du serveur | ✅ |
| 11 | `listerFichiers` | Liste les fichiers disponibles | ✅ |
| 12 | `rotationPages` | Fait pivoter des pages | ✅ |
| 13 | `protegerPDF` | Protège un PDF par mot de passe | ✅ |
| 14 | `compresserPDF` | Compresse un PDF | ✅ |
| 15 | `numeroterPages` | Numérote les pages | ✅ |
| 16 | `apercuPage` | Génère un aperçu en base64 | ✅ |

### 5.2 API HTTP REST (pont pour clients non-CORBA)

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/api/lister` | GET | Liste les fichiers |
| `/api/infos?nom=` | GET | Infos d'un PDF |
| `/api/creer` | POST | Créer un PDF |
| `/api/creerBase64` | POST | Créer PDF (texte encodé) |
| `/api/fusionner` | POST | Fusionner deux PDFs |
| `/api/extrairePages` | POST | Extraire des pages |
| `/api/extraireTexte?nom=` | GET | Extraire le texte |
| `/api/supprimerPages` | POST | Supprimer des pages |
| `/api/ajouterTexte` | POST | Ajouter du texte |
| `/api/convertirImages` | POST | PDF vers images |
| `/api/rotation` | POST | Rotation de pages |
| `/api/proteger` | POST | Protéger par MDP |
| `/api/numeroter` | POST | Numéroter les pages |
| `/api/upload` | POST | Upload un fichier |
| `/api/download?nom=` | GET | Télécharger un fichier |
| `/api/supprimerFichier` | POST | Supprimer un fichier |

### 5.3 Authentification et gestion des utilisateurs

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/api/auth/login` | POST | Connexion |
| `/api/auth/register` | POST | Inscription |
| `/api/auth/logout` | POST | Déconnexion |
| `/api/auth/me` | GET | Infos session |
| `/api/auth/confirm?token=` | GET | Confirmation email |
| `/api/admin/utilisateurs` | GET | Liste utilisateurs (admin) |
| `/api/admin/toggleActif` | POST | Activer/désactiver (admin) |
| `/api/admin/changerRole` | POST | Changer le rôle (admin) |
| `/api/admin/supprimer` | POST | Supprimer utilisateur (admin) |
| `/api/admin/fichiersTous` | GET | Tous les fichiers (admin) |

---

## 6. Clients développés

### 6.1 Client Java Console (StartClient.java)
- Client CORBA classique en mode texte
- Menu interactif pour toutes les opérations PDF
- Communication directe via CORBA/IDL

### 6.2 Client JavaFX (StartClientFX.java)
- Interface graphique avec Zulu JDK 8 FX
- Écran de connexion CORBA
- Calculatrice et gestionnaire PDF graphiques
- Historique des opérations

### 6.3 Interface Web (index.html)
- Application web complète (HTML/CSS/JS vanilla)
- Design moderne inspiré des meilleures applications PDF
- Authentification (connexion/inscription)
- Tableau de bord administrateur
- Actualisation en temps réel (polling 5 secondes)
- Drag & Drop pour l'upload
- Aperçu PDF dans le navigateur

### 6.4 Client Python (client_python.py)
- Client console en Python
- Communication via HTTP REST
- Menu interactif identique au client Java

### 6.5 Client Python GUI (client_python_gui.py)
- Interface graphique Tkinter
- Connexion au pont HTTP REST
- Historique des opérations

---

## 7. Système d'authentification

### Rôles
- **Administrateur** : accès à tous les fichiers, gestion des utilisateurs, tableau de bord admin
- **Utilisateur simple** : accès uniquement à ses propres fichiers dans `/pdfs/{username}/`

### Flux d'inscription
```
Utilisateur → Formulaire d'inscription → Compte créé (inactif)
    → Email de confirmation envoyé (Brevo)
    → Clic sur le lien → Compte activé
    → Connexion possible
```

### Sécurité
- Mots de passe hashés en SHA-256
- Sessions avec tokens UUID
- Expiration des sessions après 24h
- Séparation des fichiers par utilisateur

---

## 8. Déploiement

### Infrastructure
- **Hébergement** : Render.com (plan gratuit)
- **Base de données** : PostgreSQL sur Render (plan gratuit)
- **Conteneurisation** : Docker (image eclipse-temurin:8-jdk)
- **CI/CD** : Push GitHub → Build automatique Render

### Dockerfile
```dockerfile
FROM eclipse-temurin:8-jdk
WORKDIR /app
COPY lib/ /app/lib/
COPY src/ /app/src/
COPY web/ /app/web/
RUN mkdir -p /pdfs /app/bin
RUN javac -d /app/bin /app/src/PDFService/*.java
RUN javac -cp /app/bin:/app/lib/* -d /app/bin \
    /app/src/PDFServer/EmailService.java \
    /app/src/PDFServer/AuthManager.java \
    /app/src/PDFServer/GestionnairePDFImpl.java \
    /app/src/PDFServer/StartServerWeb.java
EXPOSE 8080
CMD java -cp /app/bin:/app/lib/* StartServerWeb
```

### Variables d'environnement
| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | URL PostgreSQL |
| `BREVO_API_KEY` | Clé API Brevo |
| `APP_URL` | URL de l'application |
| `PORT` | Port HTTP (8080) |

---

## 9. Problèmes rencontrés et solutions

| Problème | Cause | Solution |
|----------|-------|----------|
| CORBA supprimé de Java 11+ | Évolution du JDK | Utilisation de Zulu JDK 8 avec FX |
| JavaFX incompatible | Versions non alignées | Zulu JDK 8 avec JavaFX intégré |
| `Unable to open DISPLAY` | WSLg non actif | `export DISPLAY=:0` + `~/.bashrc` |
| SQLite non persistant sur Render | Système de fichiers éphémère | Migration vers PostgreSQL |
| Secrets GitHub bloqués | GitHub Secret Scanning | Secrets dans variables d'environnement |
| SQLite nécessitait slf4j | Version trop récente | Downgrade vers sqlite-jdbc 3.36 |
| URL PostgreSQL mal parsée | Format non standard | Parser manuel avec port 5432 explicite |
| `getJsonField` coupe les `\n` | Parser JSON maison limité | Encodage base64 pour les textes longs |

---

## 10. Points à améliorer (travaux futurs)

- [ ] Envoi d'emails de confirmation (Brevo configuré, à tester)
- [ ] Suppression d'utilisateur et changement de rôle côté admin
- [ ] Suppression de fichier depuis l'interface web
- [ ] Persistance des fichiers PDF sur Render (volume payant ou S3)
- [ ] Parser JSON robuste (utiliser une vraie bibliothèque comme Gson)
- [ ] Texte multi-lignes dans la création de PDF
- [ ] Aperçu PDF dans le navigateur
- [ ] Conversion de fichiers Word vers PDF
- [ ] Compression PDF fonctionnelle
- [ ] Tests unitaires

---

## 11. Structure du projet

```
TP_CORBA_PDF/
├── Dockerfile
├── .gitignore
├── lib/
│   ├── pdfbox-2.0.31.jar
│   ├── fontbox-2.0.31.jar
│   ├── commons-logging-1.2.jar
│   ├── sqlite-jdbc-3.36.0.3.jar
│   ├── javax.mail-1.6.2.jar
│   └── postgresql-42.7.3.jar
├── src/
│   ├── PDFService.idl
│   ├── PDFService/          ← Stubs générés par idlj
│   │   ├── GestionnairePDF.java
│   │   ├── GestionnairePDFPOA.java
│   │   ├── PDFException.java
│   │   └── ...
│   ├── PDFServer/
│   │   ├── GestionnairePDFImpl.java  ← Implémentation CORBA + PDFBox
│   │   ├── StartServer.java          ← Serveur CORBA + HTTP (local)
│   │   ├── StartServerWeb.java       ← Serveur HTTP REST (production)
│   │   ├── AuthManager.java          ← Authentification PostgreSQL
│   │   └── EmailService.java         ← Envoi emails Brevo
│   └── PDFClient/
│       └── StartClientFX.java        ← Client JavaFX
└── web/
    └── index.html                    ← Interface web complète
```

---

## 12. Conclusion

Ce TP a permis de mettre en œuvre une application distribuée complète basée sur CORBA, en intégrant :

- La **conception d'une interface IDL** définissant les services PDF
- Le **développement d'un serveur CORBA** implémentant 16 opérations PDF
- La création de **5 types de clients** différents (Java console, JavaFX, Web, Python console, Python GUI)
- Un **pont HTTP REST** permettant l'interopérabilité avec des clients non-CORBA
- Un système d'**authentification complet** avec rôles admin/utilisateur
- Le **déploiement cloud** via Docker et Render

L'application est accessible publiquement à l'adresse **https://pdflow.onrender.com**.

