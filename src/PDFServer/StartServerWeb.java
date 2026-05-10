import PDFService.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class StartServerWeb {

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static GestionnairePDFImpl impl = new GestionnairePDFImpl();

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║      PDFlow - Serveur HTTP REST       ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("Port : " + HTTP_PORT);

        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                servirHTML(exchange);
            } else if (path.startsWith("/api/auth/")) {
                traiterAuth(exchange);
            } else if (path.startsWith("/api/admin/")) {
                traiterAdmin(exchange);
            } else if (path.startsWith("/api/")) {
                traiterAPI(exchange);
            } else {
                repondre(exchange, 404, "text/plain", "Non trouvé");
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ Serveur démarré sur le port " + HTTP_PORT);
        System.out.println("👤 Admin par défaut : admin / admin123");
    }

    // ══════════════════════════════════════
    //  AUTH
    // ══════════════════════════════════════
    private static void traiterAuth(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String op = path.replace("/api/auth/", "");

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }

        try {
            String json;
            switch (op) {
                case "login": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String username = getJsonField(bs, "username");
                    String password = getJsonField(bs, "password");
                    Map<String,String> result = AuthManager.login(username, password);
                    // Créer dossier user si nécessaire
                    new File(AuthManager.getDossierUser(username)).mkdirs();
                    json = "{\"succes\":true,\"token\":\"" + result.get("token") + "\"," +
                           "\"username\":\"" + result.get("username") + "\"," +
                           "\"role\":\"" + result.get("role") + "\"," +
                           "\"actif\":" + result.get("actif") + "}";
                    break;
                }
                case "register": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.inscrire(
                        getJsonField(bs, "username"),
                        getJsonField(bs, "password"),
                        getJsonField(bs, "email")
                    );
                    json = "{\"succes\":true,\"message\":\"Compte créé avec succès\"}";
                    break;
                }
                case "confirm": {
                    String confirmToken = exchange.getRequestURI().getQuery();
                    confirmToken = confirmToken != null ? confirmToken.replace("token=","") : "";
                    AuthManager.confirmerCompte(confirmToken);
                    // Rediriger vers la page principale avec message
                    String html = "<html><head><meta charset='UTF-8'><meta http-equiv='refresh' content='3;url=/'></head>" +
                        "<body style='font-family:sans-serif;text-align:center;padding:60px;background:#F5F2EE'>" +
                        "<h1 style='color:#2BB673'>✅ Compte confirmé !</h1>" +
                        "<p style='color:#6B6560'>Votre compte a été activé. Redirection en cours...</p>" +
                        "<a href='/' style='color:#E85D2F'>Cliquer ici si la redirection ne fonctionne pas</a>" +
                        "</body></html>";
                    byte[] htmlBytes = html.getBytes("UTF-8");
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, htmlBytes.length);
                    exchange.getResponseBody().write(htmlBytes);
                    exchange.getResponseBody().close();
                    return;
                }

                case "logout": {
                    String token = getToken(exchange);
                    if (!token.isEmpty()) AuthManager.logout(token);
                    json = "{\"succes\":true}";
                    break;
                }
                case "me": {
                    String token = getToken(exchange);
                    Map<String,String> info = AuthManager.verifierToken(token);
                    json = "{\"username\":\"" + info.get("username") + "\",\"role\":\"" + info.get("role") + "\"}";
                    break;
                }
                default:
                    json = "{\"erreur\":\"Route inconnue\"}";
            }
            repondre(exchange, 200, "application/json", json);
        } catch (Exception e) {
            repondre(exchange, 401, "application/json", "{\"erreur\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ══════════════════════════════════════
    //  ADMIN
    // ══════════════════════════════════════
    private static void traiterAdmin(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }

        try {
            // Vérifier que c'est un admin
            String token = getToken(exchange);
            Map<String,String> info = AuthManager.verifierToken(token);
            if (!"admin".equals(info.get("role"))) {
                repondre(exchange, 403, "application/json", "{\"erreur\":\"Accès refusé\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String op = path.replace("/api/admin/", "");
            String json;

            switch (op) {
                case "utilisateurs": {
                    List<Map<String,String>> users = AuthManager.listerUtilisateurs();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i=0; i<users.size(); i++) {
                        Map<String,String> u = users.get(i);
                        sb.append("{\"username\":\"").append(esc(u.get("username"))).append("\",")
                          .append("\"email\":\"").append(esc(u.get("email"))).append("\",")
                          .append("\"role\":\"").append(u.get("role")).append("\",")
                          .append("\"actif\":").append(u.get("actif")).append(",")
                          .append("\"created_at\":\"").append(esc(u.get("created_at"))).append("\",")
                          .append("\"last_login\":\"").append(esc(u.get("last_login"))).append("\"}");
                        if (i < users.size()-1) sb.append(",");
                    }
                    sb.append("]");
                    json = sb.toString();
                    break;
                }
                case "toggleActif": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.toggleActif(getJsonField(bs, "username"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "changerRole": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.changerRole(getJsonField(bs, "username"), getJsonField(bs, "role"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "supprimer": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.supprimerUtilisateur(getJsonField(bs, "username"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "fichiersTous": {
                    // Admin voit tous les fichiers de tous les users
                    String basePath = AuthManager.getDossierUser("admin").replace("admin/","");
                    File base = new File(basePath);
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    File[] users = base.listFiles(File::isDirectory);
                    if (users != null) {
                        for (File ud : users) {
                            File[] files = ud.listFiles(f -> f.getName().endsWith(".pdf") || f.getName().endsWith(".png"));
                            if (files != null) for (File f : files) {
                                if (!first) sb.append(",");
                                sb.append("{\"nom\":\"").append(esc(f.getName())).append("\",\"user\":\"").append(esc(ud.getName())).append("\"}");
                                first = false;
                            }
                        }
                    }
                    sb.append("]");
                    json = sb.toString();
                    break;
                }
                default:
                    json = "{\"erreur\":\"Route admin inconnue\"}";
            }
            repondre(exchange, 200, "application/json", json);
        } catch (Exception e) {
            repondre(exchange, 500, "application/json", "{\"erreur\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ══════════════════════════════════════
    //  API PDF
    // ══════════════════════════════════════
    private static void traiterAPI(HttpExchange exchange) throws IOException {
        String path  = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }

        try {
            // Vérifier le token
            String token = getToken(exchange);
            Map<String,String> userInfo = AuthManager.verifierToken(token);
            String username = userInfo.get("username");
            String role = userInfo.get("role");

            // Dossier de l'utilisateur
            String dossier = AuthManager.getDossierUser(username);
            new File(dossier).mkdirs();

            // Utiliser le dossier de l'utilisateur
            impl.setDossier(dossier);

            String op = path.replace("/api/", "");
            String json;

            switch (op) {
                case "lister":
                    String[] fichiers;
                    if ("admin".equals(role)) {
                        fichiers = impl.listerFichiers();
                    } else {
                        fichiers = impl.listerFichiers();
                    }
                    json = toJsonArray(fichiers);
                    break;

                case "infos":
                    InfosPDF infos = impl.getInfos(getParam(query, "nom"));
                    json = "{\"nomFichier\":\"" + esc(infos.nomFichier) + "\"," +
                           "\"nombrePages\":" + infos.nombrePages + "," +
                           "\"auteur\":\"" + esc(infos.auteur) + "\"," +
                           "\"titre\":\"" + esc(infos.titre) + "\"," +
                           "\"tailleFichier\":" + infos.tailleFichier + "}";
                    break;

                case "creer": case "creerBase64": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String texte, nom;
                    if (op.equals("creerBase64")) {
                        texte = new String(Base64.getDecoder().decode(getJsonField(bs,"texteB64")), "UTF-8");
                        nom = getJsonField(bs, "nom");
                    } else {
                        texte = getJsonField(bs, "texte");
                        nom = getJsonField(bs, "nom");
                    }
                    byte[] pdf = impl.creerPDF(texte, nom);
                    json = "{\"succes\":true,\"taille\":" + pdf.length + ",\"nom\":\"" + esc(nom) + "\"}";
                    break;
                }

                case "fusionner": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    byte[] r = impl.fusionner(getJsonField(bs,"nom1"), getJsonField(bs,"nom2"), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"taille\":" + r.length + "}";
                    break;
                }

                case "extrairePages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.extrairePages(getJsonField(bs,"nom"),
                        Integer.parseInt(getJsonField(bs,"debut")),
                        Integer.parseInt(getJsonField(bs,"fin")),
                        getJsonField(bs,"resultat"));
                    json = "{\"succes\":true}";
                    break;
                }

                case "extraireTexte":
                    json = toJsonArray(impl.extraireTexte(getParam(query, "nom")));
                    break;

                case "supprimerPages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nomS = getJsonField(bs,"nom"), resS = getJsonField(bs,"resultat");
                    InfosPDF infosS = impl.getInfos(nomS);
                    String pRaw = bs.substring(bs.indexOf("\"pages\":")+8);
                    pRaw = pRaw.substring(pRaw.indexOf("[")+1, pRaw.indexOf("]"));
                    int[] pArr = pRaw.trim().isEmpty() ? new int[0] :
                        Arrays.stream(pRaw.split(",")).mapToInt(s->Integer.parseInt(s.trim())).toArray();
                    if (pArr.length >= infosS.nombrePages) {
                        new File(dossier + nomS).delete();
                        json = "{\"succes\":true,\"supprime\":true}";
                    } else {
                        impl.supprimerPages(nomS, pArr, resS);
                        json = "{\"succes\":true,\"fichier\":\"" + esc(resS) + "\"}";
                    }
                    break;
                }

                case "ajouterTexte": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.ajouterTexte(getJsonField(bs,"nom"), getJsonField(bs,"texte"),
                        Integer.parseInt(getJsonField(bs,"page")),
                        Float.parseFloat(getJsonField(bs,"x")),
                        Float.parseFloat(getJsonField(bs,"y")),
                        getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + esc(getJsonField(bs,"resultat")) + "\"}";
                    break;
                }

                case "convertirImages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String[] imgs = impl.convertirEnImages(getJsonField(bs,"nom"), Integer.parseInt(getJsonField(bs,"dpi")));
                    json = toJsonArray(imgs);
                    break;
                }

                case "rotation": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String pRaw = bs.substring(bs.indexOf("\"pages\":")+8);
                    pRaw = pRaw.substring(pRaw.indexOf("[")+1, pRaw.indexOf("]"));
                    int[] pArr = pRaw.trim().isEmpty() ? new int[0] :
                        Arrays.stream(pRaw.split(",")).mapToInt(s->Integer.parseInt(s.trim())).toArray();
                    impl.rotationPages(getJsonField(bs,"nom"), pArr, Integer.parseInt(getJsonField(bs,"angle")), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + esc(getJsonField(bs,"resultat")) + "\"}";
                    break;
                }

                case "proteger": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.protegerPDF(getJsonField(bs,"nom"), getJsonField(bs,"motDePasse"), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + esc(getJsonField(bs,"resultat")) + "\"}";
                    break;
                }

                case "numeroter": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.numeroterPages(getJsonField(bs,"nom"), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + esc(getJsonField(bs,"resultat")) + "\"}";
                    break;
                }

                case "upload": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.uploadFichier(getJsonField(bs,"nom"), Base64.getDecoder().decode(getJsonField(bs,"contenu")));
                    json = "{\"succes\":true}";
                    break;
                }

                case "download": {
                    String nom = getParam(query, "nom");
                    byte[] data = impl.downloadFichier(nom);
                    json = "{\"nom\":\"" + esc(nom) + "\",\"contenu\":\"" + Base64.getEncoder().encodeToString(data) + "\"}";
                    break;
                }

                case "supprimerFichier": {
                    byte[] bodySF = readBody(exchange);
                    String bodySFStr = new String(bodySF, "UTF-8");
                    String nomSF = getJsonField(bodySFStr, "nom");
                    java.io.File fileSF = new java.io.File(dossier + nomSF);
                    if (!fileSF.exists()) throw new Exception("Fichier introuvable : " + nomSF);
                    fileSF.delete();
                    json = "{\"succes\":true,\"message\":\"Fichier supprimé\"}" ;
                    break;
                }

                case "apercu": {
                    String nom = getParam(query, "nom");
                    int page = Integer.parseInt(getParam(query,"page").isEmpty()?"1":getParam(query,"page"));
                    json = "{\"image\":\"" + impl.apercuPage(nom, page) + "\"}";
                    break;
                }

                default:
                    json = "{\"erreur\":\"Opération inconnue : " + op + "\"}";
            }

            repondre(exchange, 200, "application/json", json);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Erreur inconnue";
            if (msg.contains("Session") || msg.contains("invalide")) {
                repondre(exchange, 401, "application/json", "{\"erreur\":\"" + esc(msg) + "\"}");
            } else {
                repondre(exchange, 500, "application/json", "{\"erreur\":\"" + esc(msg) + "\"}");
            }
        }
    }

    // ══════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════
    private static String getToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return "";
    }

    private static void servirHTML(HttpExchange ex) throws IOException {
        File f = new File("/app/web/index.html");
        if (!f.exists()) f = new File("web/index.html");
        if (!f.exists()) { repondre(ex, 404, "text/plain", "index.html introuvable"); return; }
        byte[] bytes = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static void repondre(HttpExchange ex, int code, String ct, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        ex.getResponseHeaders().add("Content-Type", ct + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static byte[] readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = ex.getRequestBody().read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static String getParam(String query, String key) {
        if (query == null) return "";
        for (String p : query.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2 && kv[0].equals(key))
                try { return URLDecoder.decode(kv[1], "UTF-8"); } catch (Exception e) { return kv[1]; }
        }
        return "";
    }

    private static String getJsonField(String json, String key) {
        String ps = "\"" + key + "\":\"";
        int i = json.indexOf(ps);
        if (i != -1) {
            int start = i + ps.length();
            StringBuilder sb = new StringBuilder();
            int j = start;
            while (j < json.length()) {
                char c = json.charAt(j);
                if (c == '\\' && j+1 < json.length()) {
                    char next = json.charAt(j+1);
                    if (next=='n'){sb.append('\n');j+=2;continue;}
                    if (next=='r'){j+=2;continue;}
                    if (next=='t'){sb.append('\t');j+=2;continue;}
                    if (next=='"'){sb.append('"');j+=2;continue;}
                    if (next=='\\'){sb.append('\\');j+=2;continue;}
                }
                if (c=='"') break;
                sb.append(c); j++;
            }
            return sb.toString();
        }
        String pn = "\"" + key + "\":";
        i = json.indexOf(pn);
        if (i==-1) return "";
        int start = i + pn.length();
        while (start < json.length() && json.charAt(start)==' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end)!=',' && json.charAt(end)!='}' && json.charAt(end)!=']') end++;
        return json.substring(start, end).trim();
    }

    private static String toJsonArray(String[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0; i<arr.length; i++) {
            sb.append("\"").append(esc(arr[i])).append("\"");
            if (i<arr.length-1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        if (s==null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","").replace("\t","\\t");
    }
}
