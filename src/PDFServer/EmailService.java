import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/**
 * Service d'envoi d'emails via Gmail SMTP
 * Configure avec les variables d'environnement :
 *   MAIL_USER : votre email Gmail
 *   MAIL_PASS : mot de passe d'application Gmail
 */
public class EmailService {

    private static final String MAIL_USER = System.getenv().getOrDefault("MAIL_USER", "");
    private static final String MAIL_PASS = System.getenv().getOrDefault("MAIL_PASS", "");
    private static final String APP_URL   = System.getenv().getOrDefault("APP_URL", "https://pdflow.onrender.com");

    public static void envoyerConfirmation(String destinataire, String username, String confirmToken) {
        if (MAIL_USER.isEmpty() || MAIL_PASS.isEmpty()) {
            System.out.println("⚠️ Email non configuré (MAIL_USER/MAIL_PASS manquants)");
            return;
        }

        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(MAIL_USER, MAIL_PASS);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(MAIL_USER, "PDFlow"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire));
                message.setSubject("Bienvenue sur PDFlow ! 🎉");
                message.setContent(buildHTML(username, confirmToken), "text/html; charset=utf-8");

                Transport.send(message);
                System.out.println("✅ Email envoyé à : " + destinataire);

            } catch (Exception e) {
                System.err.println("❌ Erreur envoi email : " + e.getMessage());
            }
        }, "email-sender").start();
    }

    private static String buildHTML(String username, String confirmToken) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif;background:#F5F2EE;margin:0;padding:0'>" +
            "<div style='max-width:560px;margin:40px auto;background:#fff;border-radius:20px;overflow:hidden;box-shadow:0 4px 30px rgba(0,0,0,.1)'>" +
            "<div style='background:linear-gradient(135deg,#E85D2F,#7C3AED);padding:40px;text-align:center'>" +
            "<h1 style='color:#fff;font-size:32px;margin:0'>PDF<span style=\"color:#FFD0C2\">flow</span></h1>" +
            "<p style='color:rgba(255,255,255,.8);margin:8px 0 0'>Gestionnaire de documents PDF</p></div>" +
            "<div style='padding:40px'>" +
            "<h2 style='color:#1A1714;font-size:24px;margin:0 0 16px'>Bienvenue, " + username + " ! 🎉</h2>" +
            "<p style='color:#6B6560;line-height:1.7;margin:0 0 24px'>Votre compte PDFlow a été créé avec succès. Vous pouvez maintenant :</p>" +
            "<ul style='color:#6B6560;line-height:2;padding-left:20px;margin:0 0 32px'>" +
            "<li>📄 Créer et gérer vos documents PDF</li>" +
            "<li>🔗 Fusionner plusieurs PDFs</li>" +
            "<li>✂️ Extraire et supprimer des pages</li>" +
            "<li>🔒 Protéger vos documents</li>" +
            "<li>📝 Extraire le texte de vos PDFs</li>" +
            "</ul>" +
            "<a href='" + APP_URL + "' style='display:inline-block;background:#E85D2F;color:#fff;padding:14px 32px;border-radius:100px;text-decoration:none;font-weight:600;font-size:15px'>Accéder à PDFlow →</a>" +
            "</div>" +
            "<div style='padding:20px 40px;background:#F5F2EE;text-align:center'>" +
            "<p style='color:#A8A29C;font-size:12px;margin:0'>© 2026 PDFlow — Tous droits réservés</p>" +
            "</div></div></body></html>";
    }
}
