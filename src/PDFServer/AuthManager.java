import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

public class AuthManager {

    private static Connection conn;

    static {
        try {
            Class.forName("org.postgresql.Driver");
            // Construire l'URL JDBC manuellement avec le port explicite
            String dbUrl = System.getenv("DATABASE_URL");
                if (dbUrl == null) throw new Exception("DATABASE_URL non définie");

            // Parser l'URL manuellement
            // Format: postgresql://user:pass@host/dbname
            String withoutProto = dbUrl.replace("postgresql://", "");
            String userPass = withoutProto.substring(0, withoutProto.indexOf("@"));
            String hostDb   = withoutProto.substring(withoutProto.indexOf("@") + 1);
            String user     = userPass.substring(0, userPass.indexOf(":"));
            String pass     = userPass.substring(userPass.indexOf(":") + 1);
            String host     = hostDb.contains("/") ? hostDb.substring(0, hostDb.indexOf("/")) : hostDb;
            String dbname   = hostDb.contains("/") ? hostDb.substring(hostDb.indexOf("/") + 1) : "";

            String jdbcUrl = "jdbc:postgresql://" + host + ":5432/" + dbname + "?sslmode=require";
            System.out.println("🔌 Connexion PostgreSQL : " + host + "/" + dbname);
            conn = DriverManager.getConnection(jdbcUrl, user, pass);
            creerTables();
            creerAdminParDefaut();
            System.out.println("✅ PostgreSQL connecté !");
        } catch (Exception e) {
            System.err.println("❌ Erreur PostgreSQL : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void creerTables() throws SQLException {
        Statement st = conn.createStatement();
        st.execute(
            "CREATE TABLE IF NOT EXISTS utilisateurs (" +
            "  id SERIAL PRIMARY KEY," +
            "  username VARCHAR(100) UNIQUE NOT NULL," +
            "  password VARCHAR(255) NOT NULL," +
            "  email VARCHAR(255)," +
            "  role VARCHAR(20) DEFAULT 'user'," +
            "  actif INTEGER DEFAULT 0," +
            "  created_at TIMESTAMP DEFAULT NOW()," +
            "  last_login TIMESTAMP," +
            "  confirmation_token VARCHAR(255)" +
            ")"
        );
        st.execute(
            "CREATE TABLE IF NOT EXISTS sessions (" +
            "  token VARCHAR(255) PRIMARY KEY," +
            "  username VARCHAR(100) NOT NULL," +
            "  role VARCHAR(20) NOT NULL," +
            "  created_at TIMESTAMP DEFAULT NOW()," +
            "  expires_at TIMESTAMP" +
            ")"
        );
        st.close();
    }

    private static void creerAdminParDefaut() throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO utilisateurs (username, password, email, role, actif) VALUES (?,?,?,?,1) ON CONFLICT (username) DO NOTHING"
        );
        ps.setString(1, "admin");
        ps.setString(2, hashPassword("admin123"));
        ps.setString(3, "admin@pdflow.com");
        ps.setString(4, "admin");
        ps.executeUpdate();
        ps.close();
    }

    // ══════════════════════════════════════
    //  Authentification
    // ══════════════════════════════════════
    public static Map<String,String> login(String username, String password) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT username, role, actif FROM utilisateurs WHERE username=? AND password=?"
        );
        ps.setString(1, username);
        ps.setString(2, hashPassword(password));
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) throw new Exception("Identifiants incorrects");
        if (rs.getInt("actif") == 0) throw new Exception("Compte non activé. Vérifiez votre email.");

        String role = rs.getString("role");
        int actif = rs.getInt("actif");
        rs.close(); ps.close();

        // Mettre à jour last_login
        PreparedStatement up = conn.prepareStatement(
            "UPDATE utilisateurs SET last_login=NOW() WHERE username=?"
        );
        up.setString(1, username); up.executeUpdate(); up.close();

        // Créer session
        String token = UUID.randomUUID().toString();
        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO sessions (token, username, role, expires_at) VALUES (?,?,?,NOW() + INTERVAL '24 hours')"
        );
        ins.setString(1, token); ins.setString(2, username); ins.setString(3, role);
        ins.executeUpdate(); ins.close();

        Map<String,String> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("role", role);
        result.put("actif", String.valueOf(actif));
        return result;
    }

    public static Map<String,String> verifierToken(String token) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT username, role FROM sessions WHERE token=? AND expires_at > NOW()"
        );
        ps.setString(1, token);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) throw new Exception("Session expirée ou invalide");
        Map<String,String> info = new HashMap<>();
        info.put("username", rs.getString("username"));
        info.put("role", rs.getString("role"));
        rs.close(); ps.close();
        return info;
    }

    public static void logout(String token) throws Exception {
        PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE token=?");
        ps.setString(1, token); ps.executeUpdate(); ps.close();
    }

    // ══════════════════════════════════════
    //  Inscription
    // ══════════════════════════════════════
    public static void inscrire(String username, String password, String email) throws Exception {
        if (username.length() < 3) throw new Exception("Nom d'utilisateur trop court (min 3 caractères)");
        if (password.length() < 6) throw new Exception("Mot de passe trop court (min 6 caractères)");

        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO utilisateurs (username, password, email, role, actif) VALUES (?,?,?,'user',0)"
        );
        ps.setString(1, username);
        ps.setString(2, hashPassword(password));
        ps.setString(3, email);
        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new Exception("Nom d'utilisateur déjà pris");
        }
        ps.close();

        // Créer dossier utilisateur
        new java.io.File(getDossierUser(username)).mkdirs();

        // Générer token et envoyer email
        if (email != null && !email.isEmpty()) {
            String confirmToken = genererTokenConfirmation(username);
            EmailService.envoyerConfirmation(email, username, confirmToken);
        }
    }

    public static String genererTokenConfirmation(String username) throws Exception {
        String token = UUID.randomUUID().toString();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE utilisateurs SET confirmation_token=? WHERE username=?"
        );
        ps.setString(1, token); ps.setString(2, username);
        ps.executeUpdate(); ps.close();
        return token;
    }

    public static void confirmerCompte(String token) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE utilisateurs SET actif=1, confirmation_token=NULL WHERE confirmation_token=?"
        );
        ps.setString(1, token);
        int rows = ps.executeUpdate(); ps.close();
        if (rows == 0) throw new Exception("Token invalide ou déjà utilisé");
    }

    // ══════════════════════════════════════
    //  Gestion utilisateurs (Admin)
    // ══════════════════════════════════════
    public static List<Map<String,String>> listerUtilisateurs() throws Exception {
        List<Map<String,String>> liste = new ArrayList<>();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(
            "SELECT username, email, role, actif, created_at, last_login FROM utilisateurs ORDER BY created_at DESC"
        );
        while (rs.next()) {
            Map<String,String> u = new HashMap<>();
            u.put("username",   rs.getString("username"));
            u.put("email",      rs.getString("email") != null ? rs.getString("email") : "");
            u.put("role",       rs.getString("role"));
            u.put("actif",      String.valueOf(rs.getInt("actif")));
            u.put("created_at", rs.getString("created_at") != null ? rs.getString("created_at").substring(0,16) : "");
            u.put("last_login", rs.getString("last_login") != null ? rs.getString("last_login").substring(0,16) : "Jamais");
            liste.add(u);
        }
        rs.close(); st.close();
        return liste;
    }

    public static void toggleActif(String username) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE utilisateurs SET actif = CASE WHEN actif=1 THEN 0 ELSE 1 END WHERE username=? AND username != 'admin'"
        );
        ps.setString(1, username); ps.executeUpdate(); ps.close();
    }

    public static void changerRole(String username, String role) throws Exception {
        if (username.equals("admin")) throw new Exception("Impossible de modifier l'admin");
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE utilisateurs SET role=? WHERE username=?"
        );
        ps.setString(1, role); ps.setString(2, username);
        ps.executeUpdate(); ps.close();
    }

    public static void supprimerUtilisateur(String username) throws Exception {
        if (username.equals("admin")) throw new Exception("Impossible de supprimer l'admin");
        PreparedStatement ps = conn.prepareStatement("DELETE FROM utilisateurs WHERE username=?");
        ps.setString(1, username); ps.executeUpdate(); ps.close();
        PreparedStatement ps2 = conn.prepareStatement("DELETE FROM sessions WHERE username=?");
        ps2.setString(1, username); ps2.executeUpdate(); ps2.close();
    }

    // ══════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════
    public static String getDossierUser(String username) {
        String base = "/pdfs/";
        return base + username + "/";
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return password; }
    }
}
