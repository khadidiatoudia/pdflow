import PDFService.*;
import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class StartServer {

    private static final int HTTP_PORT = 8080;
    private static GestionnairePDFImpl impl;

    public static void main(String[] args) {
        try {
            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║    SERVEUR CORBA PDF + HTTP REST       ║");
            System.out.println("╚══════════════════════════════════════╝");

            ORB orb = ORB.init(args, null);
            impl = new GestionnairePDFImpl();

            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootPOA.the_POAManager().activate();
            org.omg.CORBA.Object ref = rootPOA.servant_to_reference(impl);
            GestionnairePDF href = GestionnairePDFHelper.narrow(ref);

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            NameComponent[] path = ncRef.to_name("GestionnairePDFService");
            ncRef.rebind(path, href);

            System.out.println("[4/4] Démarrage du pont HTTP REST sur le port " + HTTP_PORT + "...");
            demarrerHTTP();
            System.out.println("    ✅ Interface web : http://localhost:" + HTTP_PORT);
            System.out.println("\n🚀 Serveur prêt !\n");

            orb.run();

        } catch (Exception e) {
            System.err.println("ERREUR : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void demarrerHTTP() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                servirFichierStatique(exchange, "/app/web/index.html", "text/html");
            } else if (path.startsWith("/api/")) {
                traiterAPI(exchange);
            } else {
                repondre(exchange, 404, "text/plain", "Non trouvé");
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    private static void traiterAPI(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String query  = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if (method.equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            String op = path.replace("/api/", "");
            String json = "";

            switch (op) {
                case "lister":
                    json = toJsonArray(impl.listerFichiers());
                    break;

                case "infos":
                    String nom = getParam(query, "nom");
                    InfosPDF infos = impl.getInfos(nom);
                    json = "{\"nomFichier\":\"" + infos.nomFichier + "\",\"nombrePages\":" + infos.nombrePages + "}";
                    break;

                case "creer":
                    byte[] body = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyStr = new String(body, "UTF-8");
                    byte[] pdf = impl.creerPDF(getJsonField(bodyStr, "texte"), getJsonField(bodyStr, "nom"));
                    json = "{\"succes\":true,\"taille\":" + pdf.length + "}";
                    break;

                case "creerBase64":
                    byte[] bodyB64 = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyB64Str = new String(bodyB64, "UTF-8");
                    String nomB64 = getJsonField(bodyB64Str, "nom");
                    String texteB64 = getJsonField(bodyB64Str, "texteB64");
                    byte[] texteBytes = java.util.Base64.getDecoder().decode(texteB64);
                    String texteDecoded = new String(texteBytes, "UTF-8");
                    byte[] pdfB64 = impl.creerPDF(texteDecoded, nomB64);
                    json = "{\"succes\":true,\"taille\":" + pdfB64.length + ",\"nom\":\"" + nomB64 + "\"}";
                    break;


                case "fusionner":
                    byte[] bodyF = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyFStr = new String(bodyF, "UTF-8");
                    impl.fusionner(getJsonField(bodyFStr, "nom1"), getJsonField(bodyFStr, "nom2"), getJsonField(bodyFStr, "resultat"));
                    json = "{\"succes\":true}";
                    break;

                case "extraireTexte":
                    String nomET = getParam(query, "nom");
                    String[] lignesText = impl.extraireTexte(nomET);
                    json = toJsonArray(lignesText);
                    break;

                case "extrairePages":
                    byte[] bodyEP = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyEPStr = new String(bodyEP, "UTF-8");
                    String nomEP = getJsonField(bodyEPStr, "nom");
                    int debut = Integer.parseInt(getJsonField(bodyEPStr, "debut"));
                    int fin = Integer.parseInt(getJsonField(bodyEPStr, "fin"));
                    String resEP = getJsonField(bodyEPStr, "resultat");
                    impl.extrairePages(nomEP, debut, fin, resEP);
                    json = "{\"succes\":true,\"fichier\":\"" + resEP + "\"}";
                    break;

                case "upload":
                    byte[] bodyU = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyUStr = new String(bodyU, "UTF-8");
                    String b64 = getJsonField(bodyUStr, "contenu");
                    impl.uploadFichier(getJsonField(bodyUStr, "nom"), Base64.getDecoder().decode(b64));
                    json = "{\"succes\":true}";
                    break;

                case "download":
                    String nomD = getParam(query, "nom");
                    byte[] dataD = impl.downloadFichier(nomD);
                    String b64D = java.util.Base64.getEncoder().encodeToString(dataD);
                    json = "{\"nom\":\"" + nomD + "\",\"contenu\":\"" + b64D + "\"}";
                    break;

                case "supprimerPages":
                    byte[] bodyS = readAllBytesCompatible(exchange.getRequestBody());
                    String bodySStr = new String(bodyS, "UTF-8");
                    String nomS = getJsonField(bodySStr, "nom");
                    String resS = getJsonField(bodySStr, "resultat");
                    String pagesRaw = bodySStr.substring(bodySStr.indexOf("\"pages\":")+8);
                    pagesRaw = pagesRaw.substring(pagesRaw.indexOf("[")+1, pagesRaw.indexOf("]"));
                    String[] pagesTokens = pagesRaw.split(",");
                    int[] pagesArr = new int[pagesTokens.length];
                    for (int ii = 0; ii < pagesTokens.length; ii++) pagesArr[ii] = Integer.parseInt(pagesTokens[ii].trim());
                    impl.supprimerPages(nomS, pagesArr, resS);
                    json = "{\"succes\":true,\"fichier\":\"" + resS + "\"}";
                    break;

                case "ajouterTexte":
                    byte[] bodyAT = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyATStr = new String(bodyAT, "UTF-8");
                    String nomAT = getJsonField(bodyATStr, "nom");
                    String texteAT = getJsonField(bodyATStr, "texte");
                    int pageAT = Integer.parseInt(getJsonField(bodyATStr, "page"));
                    float xAT = Float.parseFloat(getJsonField(bodyATStr, "x"));
                    float yAT = Float.parseFloat(getJsonField(bodyATStr, "y"));
                    String resAT = getJsonField(bodyATStr, "resultat");
                    impl.ajouterTexte(nomAT, texteAT, pageAT, xAT, yAT, resAT);
                    json = "{\"succes\":true,\"fichier\":\"" + resAT + "\"}";
                    break;

                case "convertirImages":
                    byte[] bodyCI = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyCIStr = new String(bodyCI, "UTF-8");
                    String nomCI = getJsonField(bodyCIStr, "nom");
                    int dpiCI = Integer.parseInt(getJsonField(bodyCIStr, "dpi"));
                    String[] imgsCI = impl.convertirEnImages(nomCI, dpiCI);
                    json = toJsonArray(imgsCI);
                    break;

                case "rotation":
                    byte[] bodyR = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyRStr = new String(bodyR, "UTF-8");
                    String nomR = getJsonField(bodyRStr, "nom");
                    String resR = getJsonField(bodyRStr, "resultat");
                    int angleR = Integer.parseInt(getJsonField(bodyRStr, "angle"));
                    String pagesRStr = bodyRStr.substring(bodyRStr.indexOf("\"pages\":")+8);
                    pagesRStr = pagesRStr.substring(pagesRStr.indexOf("[")+1, pagesRStr.indexOf("]"));
                    int[] pagesR;
                    if (pagesRStr.trim().isEmpty()) {
                        pagesR = new int[0];
                    } else {
                        String[] tokR = pagesRStr.split(",");
                        pagesR = new int[tokR.length];
                        for (int i=0;i<tokR.length;i++) pagesR[i]=Integer.parseInt(tokR[i].trim());
                    }
                    impl.rotationPages(nomR, pagesR, angleR, resR);
                    json = "{\"succes\":true,\"fichier\":\"" + resR + "\"}";
                    break;

                case "proteger":
                    byte[] bodyP = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyPStr = new String(bodyP, "UTF-8");
                    String nomP = getJsonField(bodyPStr, "nom");
                    String mdpP = getJsonField(bodyPStr, "motDePasse");
                    String resP = getJsonField(bodyPStr, "resultat");
                    impl.protegerPDF(nomP, mdpP, resP);
                    json = "{\"succes\":true,\"fichier\":\"" + resP + "\"}";
                    break;

                case "numeroter":
                    byte[] bodyN = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyNStr = new String(bodyN, "UTF-8");
                    String nomN = getJsonField(bodyNStr, "nom");
                    String resN = getJsonField(bodyNStr, "resultat");
                    impl.numeroterPages(nomN, resN);
                    json = "{\"succes\":true,\"fichier\":\"" + resN + "\"}";
                    break;

                case "convertirWordEnPDF":
                    byte[] bodyW = readAllBytesCompatible(exchange.getRequestBody());
                    String bodyWStr = new String(bodyW, "UTF-8");
                    String nomW = getJsonField(bodyWStr, "nom");
                    String contenuW = getJsonField(bodyWStr, "contenu");
                    byte[] pdfW = impl.creerPDF(contenuW, nomW.replace(".docx",".pdf").replace(".txt",".pdf"));
                    json = "{\"succes\":true,\"taille\":" + pdfW.length + "}";
                    break;

                case "apercu":
                    String nomAp = getParam(query, "nom");
                    String pageApStr = getParam(query, "page");
                    int pageAp = pageApStr.isEmpty() ? 1 : Integer.parseInt(pageApStr);
                    String b64Ap = impl.apercuPage(nomAp, pageAp);
                    json = "{\"image\":\"" + b64Ap + "\"}";
                    break;



                default:
                    json = "{\"erreur\":\"Opération inconnue\"}";
            }
            repondre(exchange, 200, "application/json", json);
        } catch (Exception e) {
            repondre(exchange, 500, "application/json", "{\"erreur\":\"" + e.getMessage() + "\"}");
        }
    }

    private static byte[] readAllBytesCompatible(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static void servirFichierStatique(HttpExchange ex, String chemin, String ct) throws IOException {
        File f = new File(chemin);
        if (!f.exists()) { repondre(ex, 404, "text/plain", "Fichier introuvable"); return; }
        byte[] bytes = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().add("Content-Type", ct);
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

    private static String getParam(String query, String key) {
        if (query == null) return "";
        for (String p : query.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return "";
    }

    private static String getJsonField(String json, String key) {
        // Valeur avec guillemets : "key":"value"
        String ps = "\"" + key + "\":\"";
        int i = json.indexOf(ps);
        if (i != -1) {
            int start = i + ps.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        // Valeur sans guillemets : "key":123
        String pn = "\"" + key + "\":";
        i = json.indexOf(pn);
        if (i == -1) return "";
        int start = i + pn.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        return json.substring(start, end).trim();
    }

    private static byte[] lireBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = ex.getRequestBody().read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static String toJsonArray(String[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append("\"").append(arr[i]).append("\"");
            if (i < arr.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
