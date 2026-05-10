import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Service d'envoi d'emails via Brevo (ex-Sendinblue)
 * Variable d'environnement : BREVO_API_KEY
 */
public class EmailService {

    private static final String API_KEY = System.getenv("BREVO_API_KEY") != null
        ? System.getenv("BREVO_API_KEY")
        : System.getProperty("BREVO_API_KEY", "");

    private static final String APP_URL = System.getenv().getOrDefault(
        "APP_URL", "https://pdflow.onrender.com"
    );

    private static final String FROM_EMAIL = "khadidiatou.dia@etu.ussein.edu.sn";
    private static final String FROM_NAME  = "PDFlow";

    public static void envoyerConfirmation(String destinataire, String username, String confirmToken) {
        new Thread(() -> {
            try {
                String confirmUrl = APP_URL + "/api/auth/confirm?token=" + confirmToken;
                String html = buildHTML(username, confirmUrl);

                String body = "{" +
                    "\"sender\":{\"name\":\"" + FROM_NAME + "\",\"email\":\"" + FROM_EMAIL + "\"}," +
                    "\"to\":[{\"email\":\"" + destinataire + "\",\"name\":\"" + username + "\"}]," +
                    "\"subject\":\"Confirmez votre compte PDFlow\"," +
                    "\"htmlContent\":" + toJsonString(html) +
                    "}";

                URL url = new URL("https://api.brevo.com/v3/smtp/email");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("api-key", API_KEY);
                con.setDoOutput(true);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = con.getResponseCode();
                if (code == 201) {
                    System.out.println("✅ Email envoyé à : " + destinataire);
                } else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    System.err.println("❌ Erreur Brevo (" + code + ") : " + sb);
                }
                con.disconnect();

            } catch (Exception e) {
                System.err.println("❌ Erreur envoi email : " + e.getMessage());
            }
        }, "email-sender").start();
    }

    private static String buildHTML(String username, String confirmUrl) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>" +
            "<body style='font-family:sans-serif;background:#F5F2EE;margin:0;padding:0'>" +
            "<div style='max-width:560px;margin:40px auto;background:#fff;border-radius:20px;overflow:hidden;box-shadow:0 4px 30px rgba(0,0,0,.1)'>" +
            "<div style='background:linear-gradient(135deg,#E85D2F,#7C3AED);padding:40px;text-align:center'>" +
            "<h1 style='color:#fff;font-size:32px;margin:0'>PDFflow</h1>" +
            "<p style='color:rgba(255,255,255,.8);margin:8px 0 0'>Gestionnaire de documents PDF</p></div>" +
            "<div style='padding:40px'>" +
            "<h2 style='color:#1A1714;font-size:22px;margin:0 0 16px'>Bienvenue, " + username + " !</h2>" +
            "<p style='color:#6B6560;line-height:1.7;margin:0 0 24px'>Votre compte a été créé avec succès. Cliquez sur le bouton ci-dessous pour l'activer :</p>" +
            "<div style='text-align:center;margin:32px 0'>" +
            "<a href='" + confirmUrl + "' style='display:inline-block;background:#E85D2F;color:#fff;padding:16px 36px;border-radius:100px;text-decoration:none;font-weight:700;font-size:16px'>Confirmer mon compte</a>" +
            "</div>" +
            "<p style='color:#A8A29C;font-size:12px'>Ce lien expire dans 24h. Si vous n'avez pas créé ce compte, ignorez cet email.</p>" +
            "</div>" +
            "<div style='padding:20px 40px;background:#F5F2EE;text-align:center'>" +
            "<p style='color:#A8A29C;font-size:12px;margin:0'>2026 PDFlow</p>" +
            "</div></div></body></html>";
    }

    private static String toJsonString(String s) {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            + "\"";
    }
}
