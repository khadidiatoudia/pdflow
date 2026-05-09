import PDFService.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * Serveur HTTP REST sans CORBA - pour déploiement cloud (Render)
 */
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
            } else if (path.startsWith("/api/")) {
                traiterAPI(exchange);
            } else {
                repondre(exchange, 404, "text/plain", "Non trouvé");
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ Serveur démarré sur le port " + HTTP_PORT);
    }

    private static void traiterAPI(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String query  = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if (method.equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }

        try {
            String op = path.replace("/api/", "");
            String json;

            switch (op) {
                case "lister":
                    json = toJsonArray(impl.listerFichiers());
                    break;

                case "infos":
                    InfosPDF infos = impl.getInfos(getParam(query, "nom"));
                    json = "{\"nomFichier\":\"" + infos.nomFichier + "\"," +
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
                        String b64 = getJsonField(bs, "texteB64");
                        texte = new String(Base64.getDecoder().decode(b64), "UTF-8");
                        nom = getJsonField(bs, "nom");
                    } else {
                        texte = getJsonField(bs, "texte");
                        nom = getJsonField(bs, "nom");
                    }
                    byte[] pdf = impl.creerPDF(texte, nom);
                    json = "{\"succes\":true,\"taille\":" + pdf.length + ",\"nom\":\"" + nom + "\"}";
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
                    impl.extrairePages(getJsonField(bs,"nom"), Integer.parseInt(getJsonField(bs,"debut")), Integer.parseInt(getJsonField(bs,"fin")), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true}";
                    break;
                }

                case "extraireTexte":
                    json = toJsonArray(impl.extraireTexte(getParam(query, "nom")));
                    break;

                case "supprimerPages": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String nomS = getJsonField(bs, "nom");
                    String resS = getJsonField(bs, "resultat");
                    InfosPDF infosS = impl.getInfos(nomS);
                    String pRaw = bs.substring(bs.indexOf("\"pages\":")+8);
                    pRaw = pRaw.substring(pRaw.indexOf("[")+1, pRaw.indexOf("]"));
                    int[] pArr;
                    if (pRaw.trim().isEmpty()) {
                        pArr = new int[0];
                    } else {
                        String[] tok = pRaw.split(",");
                        pArr = new int[tok.length];
                        for (int i=0;i<tok.length;i++) pArr[i]=Integer.parseInt(tok[i].trim());
                    }
                    // Vérifier si on supprime toutes les pages
                    if (pArr.length >= infosS.nombrePages) {
                        // Supprimer le fichier définitivement
                        new File("/pdfs/" + nomS).delete();
                        json = "{\"succes\":true,\"supprime\":true}";
                    } else {
                        impl.supprimerPages(nomS, pArr, resS);
                        json = "{\"succes\":true,\"fichier\":\"" + resS + "\"}";
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
                    json = "{\"succes\":true,\"fichier\":\"" + getJsonField(bs,"resultat") + "\"}";
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
                    json = "{\"succes\":true,\"fichier\":\"" + getJsonField(bs,"resultat") + "\"}";
                    break;
                }

                case "proteger": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.protegerPDF(getJsonField(bs,"nom"), getJsonField(bs,"motDePasse"), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + getJsonField(bs,"resultat") + "\"}";
                    break;
                }

                case "numeroter": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    impl.numeroterPages(getJsonField(bs,"nom"), getJsonField(bs,"resultat"));
                    json = "{\"succes\":true,\"fichier\":\"" + getJsonField(bs,"resultat") + "\"}";
                    break;
                }

                case "upload": {
                    byte[] body = readBody(exchange);
                    String bs = new String(body, "UTF-8");
                    String b64 = getJsonField(bs, "contenu");
                    impl.uploadFichier(getJsonField(bs,"nom"), Base64.getDecoder().decode(b64));
                    json = "{\"succes\":true}";
                    break;
                }

                case "download": {
                    String nom = getParam(query, "nom");
                    byte[] data = impl.downloadFichier(nom);
                    String b64 = Base64.getEncoder().encodeToString(data);
                    json = "{\"nom\":\"" + nom + "\",\"contenu\":\"" + b64 + "\"}";
                    break;
                }

                case "apercu": {
                    String nom = getParam(query, "nom");
                    int page = Integer.parseInt(getParam(query, "page").isEmpty() ? "1" : getParam(query, "page"));
                    String b64 = impl.apercuPage(nom, page);
                    json = "{\"image\":\"" + b64 + "\"}";
                    break;
                }

                default:
                    json = "{\"erreur\":\"Opération inconnue : " + op + "\"}";
            }

            repondre(exchange, 200, "application/json", json);

        } catch (PDFException e) {
            repondre(exchange, 400, "application/json", "{\"erreur\":\"" + esc(e.message) + "\"}");
        } catch (Exception e) {
            repondre(exchange, 500, "application/json", "{\"erreur\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private static void servirHTML(HttpExchange ex) throws IOException {
        File f = new File("/app/web/index.html");
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
                    if (next == 'n') { sb.append('\n'); j+=2; continue; }
                    if (next == 'r') { j+=2; continue; }
                    if (next == 't') { sb.append('\t'); j+=2; continue; }
                    if (next == '"') { sb.append('"'); j+=2; continue; }
                    if (next == '\\') { sb.append('\\'); j+=2; continue; }
                }
                if (c == '"') break;
                sb.append(c); j++;
            }
            return sb.toString();
        }
        String pn = "\"" + key + "\":";
        i = json.indexOf(pn);
        if (i == -1) return "";
        int start = i + pn.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        return json.substring(start, end).trim();
    }

    private static String toJsonArray(String[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append("\"").append(esc(arr[i])).append("\"");
            if (i < arr.length-1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","").replace("\t","\\t");
    }
}
