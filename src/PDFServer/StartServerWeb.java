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
            try {
                if (path.equals("/") || path.equals("/index.html")) {
                    servirHTML(exchange);
                } else if (path.startsWith("/i18n/")) {
                    servirI18n(exchange, path);
                } else if (path.equals("/manifest.json")) {
                    servirStatique(exchange, "/app/web/manifest.json", "web/manifest.json", "application/json");
                } else if (path.equals("/sw.js")) {
                    servirStatique(exchange, "/app/web/sw.js", "web/sw.js", "application/javascript");
                } else if (path.equals("/favicon.svg")) {
                    servirStatique(exchange, "/app/web/favicon.svg", "web/favicon.svg", "image/svg+xml");
                } else if (path.startsWith("/icons/")) {
                    String iconPath = path.substring(1);
                    servirStatique(exchange, "/app/web/" + iconPath, "web/" + iconPath, "image/svg+xml");
                } else if (path.startsWith("/api/auth/")) {
                    traiterAuth(exchange);
                } else if (path.startsWith("/api/admin/")) {
                    traiterAdmin(exchange);
                } else if (path.startsWith("/api/")) {
                    traiterAPI(exchange);
                } else {
                    repondre(exchange, 404, "text/plain", "Non trouvé");
                }
            } catch (Exception e) {
                try { repondre(exchange, 500, "application/json", "{\"erreur\":\"" + esc(e.getMessage()) + "\"}"); } catch (Exception ignored) {}
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
        setCORS(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }
        try {
            String json;
            switch (op) {
                case "login": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    Map<String,String> result = AuthManager.login(getJsonField(bs,"username"), getJsonField(bs,"password"));
                    new File("/tmp/pdfs/" + result.get("username") + "/").mkdirs();
                    json = "{\"succes\":true,\"token\":\"" + result.get("token") + "\"," +
                           "\"username\":\"" + result.get("username") + "\"," +
                           "\"role\":\"" + result.get("role") + "\"," +
                           "\"actif\":" + result.get("actif") + "}";
                    break;
                }
                case "register": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.inscrire(getJsonField(bs,"username"), getJsonField(bs,"password"), getJsonField(bs,"email"));
                    json = "{\"succes\":true,\"message\":\"Compte créé. Vérifiez votre email.\"}";
                    break;
                }
                case "logout": {
                    String token = getToken(exchange);
                    if (!token.isEmpty()) AuthManager.logout(token);
                    json = "{\"succes\":true}";
                    break;
                }
                case "me": {
                    Map<String,String> info = AuthManager.verifierToken(getToken(exchange));
                    json = "{\"username\":\"" + info.get("username") + "\",\"role\":\"" + info.get("role") + "\"}";
                    break;
                }
                case "confirm": {
                    String confirmToken = exchange.getRequestURI().getQuery();
                    confirmToken = confirmToken != null ? confirmToken.replace("token=","") : "";
                    AuthManager.confirmerCompte(confirmToken);
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
        setCORS(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }
        try {
            Map<String,String> info = AuthManager.verifierToken(getToken(exchange));
            if (!"admin".equals(info.get("role"))) { repondre(exchange, 403, "application/json", "{\"erreur\":\"Accès refusé\"}"); return; }

            String op = exchange.getRequestURI().getPath().replace("/api/admin/", "");
            String json;

            switch (op) {
                case "utilisateurs": {
                    List<Map<String,String>> users = AuthManager.listerUtilisateurs();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i=0; i<users.size(); i++) {
                        Map<String,String> u = users.get(i);
                        if (i>0) sb.append(",");
                        sb.append("{\"username\":\"").append(esc(u.get("username"))).append("\",")
                          .append("\"email\":\"").append(esc(u.get("email"))).append("\",")
                          .append("\"role\":\"").append(u.get("role")).append("\",")
                          .append("\"actif\":").append(u.get("actif")).append(",")
                          .append("\"created_at\":\"").append(esc(u.get("created_at"))).append("\",")
                          .append("\"last_login\":\"").append(esc(u.get("last_login"))).append("\"}");
                    }
                    json = sb.append("]").toString();
                    break;
                }
                case "toggleActif": {
                    byte[] body = readBody(exchange);
                    AuthManager.toggleActif(getJsonField(new String(body,"UTF-8"), "username"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "changerRole": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    AuthManager.changerRole(getJsonField(bs,"username"), getJsonField(bs,"role"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "supprimer": {
                    byte[] body = readBody(exchange);
                    AuthManager.supprimerUtilisateur(getJsonField(new String(body,"UTF-8"), "username"));
                    json = "{\"succes\":true}";
                    break;
                }
                case "fichiersTous": {
                    List<Map<String,String>> tousF = AuthManager.listerTousFichiersAdmin();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i=0; i<tousF.size(); i++) {
                        Map<String,String> ff = tousF.get(i);
                        if (i>0) sb.append(",");
                        sb.append("{\"nom\":\"").append(esc(ff.get("nom"))).append("\",")
                          .append("\"user\":\"").append(esc(ff.get("user"))).append("\",")
                          .append("\"taille\":").append(ff.get("taille")).append(",")
                          .append("\"created_at\":\"").append(esc(ff.get("created_at"))).append("\"}");
                    }
                    json = sb.append("]").toString();
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
        setCORS(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204,-1); return; }

        try {
            Map<String,String> userInfo = AuthManager.verifierToken(getToken(exchange));
            String username = userInfo.get("username");

            String tmpDir = "/tmp/pdfs/" + username + "/";
            new File(tmpDir).mkdirs();
            impl.setDossier(tmpDir);

            String op = path.replace("/api/", "");
            String json;

            switch (op) {
                case "lister":
                    json = toJsonArray(AuthManager.listerFichiersDB(username));
                    break;

                case "infos": {
                    String nom = getParam(query, "nom");
                    preparerFichier(username, nom);
                    InfosPDF infos = impl.getInfos(nom);
                    json = "{\"nomFichier\":\"" + esc(infos.nomFichier) + "\"," +
                           "\"nombrePages\":" + infos.nombrePages + "," +
                           "\"auteur\":\"" + esc(infos.auteur) + "\"," +
                           "\"titre\":\"" + esc(infos.titre) + "\"," +
                           "\"tailleFichier\":" + infos.tailleFichier + "}";
                    break;
                }

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
                    AuthManager.sauvegarderFichier(username, nom, pdf);
                    json = "{\"succes\":true,\"taille\":" + pdf.length + ",\"nom\":\"" + esc(nom) + "\"}";
                    break;
                }

                case "fusionner": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String n1 = getJsonField(bs,"nom1"), n2 = getJsonField(bs,"nom2"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, n1); preparerFichier(username, n2);
                    impl.fusionner(n1, n2, res);
                    byte[] data = sauvegarderResultat(username, res);
                    json = "{\"succes\":true,\"taille\":" + data.length + "}";
                    break;
                }

                case "extrairePages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    impl.extrairePages(nom, Integer.parseInt(getJsonField(bs,"debut")), Integer.parseInt(getJsonField(bs,"fin")), res);
                    sauvegarderResultat(username, res);
                    json = "{\"succes\":true}";
                    break;
                }

                case "extraireTexte": {
                    String nom = getParam(query, "nom");
                    preparerFichier(username, nom);
                    json = toJsonArray(impl.extraireTexte(nom));
                    break;
                }

                case "supprimerPages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    InfosPDF infos = impl.getInfos(nom);
                    String pRaw = bs.substring(bs.indexOf("\"pages\":")+8);
                    pRaw = pRaw.substring(pRaw.indexOf("[")+1, pRaw.indexOf("]"));
                    int[] pArr = pRaw.trim().isEmpty() ? new int[0] :
                        Arrays.stream(pRaw.split(",")).mapToInt(s->Integer.parseInt(s.trim())).toArray();
                    if (pArr.length >= infos.nombrePages) {
                        AuthManager.supprimerFichierDB(username, nom);
                        json = "{\"succes\":true,\"supprime\":true}";
                    } else {
                        impl.supprimerPages(nom, pArr, res);
                        sauvegarderResultat(username, res);
                        json = "{\"succes\":true,\"fichier\":\"" + esc(res) + "\"}";
                    }
                    break;
                }

                case "ajouterTexte": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    impl.ajouterTexte(nom, getJsonField(bs,"texte"),
                        Integer.parseInt(getJsonField(bs,"page")),
                        Float.parseFloat(getJsonField(bs,"x")),
                        Float.parseFloat(getJsonField(bs,"y")), res);
                    sauvegarderResultat(username, res);
                    json = "{\"succes\":true,\"fichier\":\"" + esc(res) + "\"}";
                    break;
                }

                case "convertirImages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom");
                    preparerFichier(username, nom);
                    String[] imgs = impl.convertirEnImages(nom, Integer.parseInt(getJsonField(bs,"dpi")));
                    for (String img : imgs) sauvegarderResultat(username, img);
                    json = toJsonArray(imgs);
                    break;
                }

                case "rotation": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    String pRaw = bs.substring(bs.indexOf("\"pages\":")+8);
                    pRaw = pRaw.substring(pRaw.indexOf("[")+1, pRaw.indexOf("]"));
                    int[] pArr = pRaw.trim().isEmpty() ? new int[0] :
                        Arrays.stream(pRaw.split(",")).mapToInt(s->Integer.parseInt(s.trim())).toArray();
                    impl.rotationPages(nom, pArr, Integer.parseInt(getJsonField(bs,"angle")), res);
                    sauvegarderResultat(username, res);
                    json = "{\"succes\":true,\"fichier\":\"" + esc(res) + "\"}";
                    break;
                }

                case "proteger": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    impl.protegerPDF(nom, getJsonField(bs,"motDePasse"), res);
                    sauvegarderResultat(username, res);
                    json = "{\"succes\":true,\"fichier\":\"" + esc(res) + "\"}";
                    break;
                }

                case "numeroter": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom"), res = getJsonField(bs,"resultat");
                    preparerFichier(username, nom);
                    impl.numeroterPages(nom, res);
                    sauvegarderResultat(username, res);
                    json = "{\"succes\":true,\"fichier\":\"" + esc(res) + "\"}";
                    break;
                }

                case "upload": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nom = getJsonField(bs,"nom");
                    byte[] contenu = Base64.getDecoder().decode(getJsonField(bs,"contenu"));
                    AuthManager.sauvegarderFichier(username, nom, contenu);
                    Files.write(Paths.get(tmpDir + nom), contenu);
                    json = "{\"succes\":true}";
                    break;
                }

                case "download": {
                    String nom = getParam(query, "nom");
                    byte[] data = AuthManager.lireFichier(username, nom);
                    json = "{\"nom\":\"" + esc(nom) + "\",\"contenu\":\"" + Base64.getEncoder().encodeToString(data) + "\"}";
                    break;
                }

                case "supprimerFichier": {
                    byte[] body = readBody(exchange);
                    String nom = getJsonField(new String(body,"UTF-8"), "nom");
                    AuthManager.supprimerFichierDB(username, nom);
                    json = "{\"succes\":true,\"message\":\"Fichier supprimé\"}";
                    break;
                }

                case "apercu": {
                    String nom = getParam(query, "nom");
                    preparerFichier(username, nom);
                    int page = Integer.parseInt(getParam(query,"page").isEmpty()?"1":getParam(query,"page"));
                    json = "{\"image\":\"" + impl.apercuPage(nom, page) + "\"}";
                    break;
                }

                default:
                    json = "{\"erreur\":\"Opération inconnue : " + op + "\"}";
            }
            repondre(exchange, 200, "application/json", json);

        } catch (PDFException e) {
            repondre(exchange, 400, "application/json", "{\"erreur\":\"" + esc(e.message) + "\"}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Erreur inconnue";
            int code = msg.contains("Session") || msg.contains("invalide") ? 401 : 500;
            repondre(exchange, code, "application/json", "{\"erreur\":\"" + esc(msg) + "\"}");
        }
    }

    // ══════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════
    private static void preparerFichier(String username, String nom) throws Exception {
        byte[] data = AuthManager.lireFichier(username, nom);
        String dir = "/tmp/pdfs/" + username + "/";
        new File(dir).mkdirs();
        Files.write(Paths.get(dir + nom), data);
    }

    private static byte[] sauvegarderResultat(String username, String nom) throws Exception {
        String path = "/tmp/pdfs/" + username + "/" + nom;
        byte[] data = Files.readAllBytes(Paths.get(path));
        AuthManager.sauvegarderFichier(username, nom, data);
        return data;
    }

    private static String getToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return "";
    }

    private static void setCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
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

    private static void servirStatique(HttpExchange ex, String path1, String path2, String ct) throws IOException {
        File f = new File(path1);
        if (!f.exists()) f = new File(path2);
        if (!f.exists()) { repondre(ex, 404, "text/plain", "Fichier introuvable"); return; }
        byte[] bytes = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().add("Content-Type", ct + "; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static void servirI18n(HttpExchange ex, String path) throws IOException {
        File f = new File("/app/web" + path);
        if (!f.exists()) f = new File("web" + path);
        if (!f.exists()) { repondre(ex, 404, "application/json", "{}"); return; }
        byte[] bytes = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
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
            if (i>0) sb.append(",");
            sb.append("\"").append(esc(arr[i])).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        if (s==null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","").replace("\t","\\t");
    }
}
