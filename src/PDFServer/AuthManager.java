import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Gestionnaire d'authentification avec SQLite
 */
public class AuthManager {

    private static final String DB_PATH = initDB();
    private static Connection conn;

    private static String initDB() {
        // Chercher un dossier accessible
        String[] paths = {"/pdfs/users.db", "pdfs/users.db", "/tmp/users.db"};
        for (String p : paths) {
            try {
                java.io.File dir = new java.io.File(p).getParentFile();
                if (dir != null && (dir.exists() || dir.mkdirs())) return p;
            } catch (Exception e) {}
        }
        return "/tmp/users.db";
    }

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            creerTables();
            creerAdminParDefaut();
            System.out.println("✅ Base de données : " + DB_PATH);
        } catch (Exception e) {
            System.err.println("❌ Erreur SQLite : " + e.getMessage());
        }
    }

    private static void creerTables() throws SQLException {
        Statement st = conn.createStatement();
        st.execute(
            "CREATE TABLE IF NOT EXISTS utilisateurs (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  username TEXT UNIQUE NOT NULL," +
            "  password TEXT NOT NULL," +
            "  email TEXT," +
            "  role TEXT DEFAULT 'user'," +
            "  actif INTEGER DEFAULT 1," +
            "  created_at TEXT DEFAULT (datetime('now'))," +
            "  last_login TEXT" +
            ")"
        );
        st.execute(
            "CREATE TABLE IF NOT EXISTS sessions (" +
            "  token TEXT PRIMARY KEY," +
            "  username TEXT NOT NULL," +
            "  role TEXT NOT NULL," +
            "  created_at TEXT DEFAULT (datetime('now'))," +
            "  expires_at TEXT" +
            ")"
        );
        st.close();
    }

    private static void creerAdminParDefaut() throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO utilisateurs (username, password, email, role) VALUES (?,?,?,?)"
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
        if (rs.getInt("actif") == 0) throw new Exception("Compte désactivé");

        String role = rs.getString("role");
        rs.close(); ps.close();

        // Mettre à jour last_login
        PreparedStatement up = conn.prepareStatement(
            "UPDATE utilisateurs SET last_login=datetime('now') WHERE username=?"
        );
        up.setString(1, username); up.executeUpdate(); up.close();

        // Créer session
        String token = UUID.randomUUID().toString();
        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO sessions (token, username, role, expires_at) VALUES (?,?,?,datetime('now','+24 hours'))"
        );
        ins.setString(1, token); ins.setString(2, username); ins.setString(3, role);
        ins.executeUpdate(); ins.close();

        Map<String,String> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("role", role);
        return result;
    }

    public static Map<String,String> verifierToken(String token) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT username, role FROM sessions WHERE token=? AND expires_at > datetime('now')"
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
    //  Gestion utilisateurs
    // ══════════════════════════════════════

    public static void inscrire(String username, String password, String email) throws Exception {
        if (username.length() < 3) throw new Exception("Nom d'utilisateur trop court (min 3 caractères)");
        if (password.length() < 6) throw new Exception("Mot de passe trop court (min 6 caractères)");

        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO utilisateurs (username, password, email, role) VALUES (?,?,?,'user')"
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

        // Créer le dossier de l'utilisateur
        new java.io.File(getDossierUser(username)).mkdirs();
    }

    public static List<Map<String,String>> listerUtilisateurs() throws Exception {
        List<Map<String,String>> liste = new ArrayList<>();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(
            "SELECT username, email, role, actif, created_at, last_login FROM utilisateurs ORDER BY created_at DESC"
        );
        while (rs.next()) {
            Map<String,String> u = new HashMap<>();
            u.put("username", rs.getString("username"));
            u.put("email", rs.getString("email") != null ? rs.getString("email") : "");
            u.put("role", rs.getString("role"));
            u.put("actif", String.valueOf(rs.getInt("actif")));
            u.put("created_at", rs.getString("created_at") != null ? rs.getString("created_at") : "");
            u.put("last_login", rs.getString("last_login") != null ? rs.getString("last_login") : "Jamais");
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
        String base = DB_PATH.replace("users.db", "");
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
