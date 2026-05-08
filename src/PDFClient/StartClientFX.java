import PDFService.*;
import org.omg.CosNaming.*;
import org.omg.CORBA.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;

public class StartClientFX extends Application {

    private static GestionnairePDF service;
    private static ORB orb;
    private Stage primaryStage;
    private TextArea logArea;
    private ListView<String> listeFichiers;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Gestionnaire PDF CORBA");
        stage.setResizable(true);
        stage.setScene(buildConnexionScene());
        stage.show();
    }

    // ═══════════════════════════════════════════
    //  ÉCRAN CONNEXION
    // ═══════════════════════════════════════════
    private Scene buildConnexionScene() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #1a1a2e;");

        Label titre = new Label("📄 Gestionnaire PDF");
        titre.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        titre.setTextFill(Color.WHITE);

        Label sous = new Label("Connexion au serveur CORBA");
        sous.setTextFill(Color.web("#4cc9f0"));
        sous.setFont(Font.font("Segoe UI", 13));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        TextField hostField = styledField("localhost");
        TextField portField = styledField("1050");

        grid.add(connLabel("Hôte :"), 0, 0); grid.add(hostField, 1, 0);
        grid.add(connLabel("Port :"), 0, 1); grid.add(portField, 1, 1);

        Label errLabel = new Label("");
        errLabel.setTextFill(Color.web("#ff6b6b"));

        Button btnConn = accentBtn("🔌 Se connecter");
        btnConn.setOnAction(e -> {
            btnConn.setDisable(true);
            btnConn.setText("Connexion...");
            new Thread(() -> {
                try {
                    String[] args = {
                        "-ORBInitialHost", hostField.getText().trim(),
                        "-ORBInitialPort", portField.getText().trim()
                    };
                    orb = ORB.init(args, null);
                    org.omg.CORBA.Object obj = orb.resolve_initial_references("NameService");
                    NamingContextExt nc = NamingContextExtHelper.narrow(obj);
                    service = GestionnairePDFHelper.narrow(nc.resolve_str("GestionnairePDFService"));
                    Platform.runLater(() -> {
                        primaryStage.setScene(buildMainScene());
                        primaryStage.setTitle("Gestionnaire PDF CORBA - Connecté");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        errLabel.setText("❌ " + ex.getMessage());
                        btnConn.setDisable(false);
                        btnConn.setText("🔌 Se connecter");
                    });
                }
            }).start();
        });

        root.getChildren().addAll(titre, sous, grid, errLabel, btnConn);
        return new Scene(root, 420, 320);
    }

    // ═══════════════════════════════════════════
    //  ÉCRAN PRINCIPAL
    // ═══════════════════════════════════════════
    private Scene buildMainScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setPadding(new Insets(16));

        // ── Haut ──
        VBox top = new VBox(4);
        top.setAlignment(Pos.CENTER);
        Label titre = new Label("📄 Gestionnaire PDF CORBA");
        titre.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titre.setTextFill(Color.WHITE);
        Label statut = new Label("✅ Connecté au serveur CORBA");
        statut.setTextFill(Color.web("#a6e3a1"));
        statut.setFont(Font.font("Segoe UI", 12));
        top.getChildren().addAll(titre, statut);
        root.setTop(top);
        BorderPane.setMargin(top, new Insets(0,0,12,0));

        // ── Centre : onglets ──
        TabPane tabs = new TabPane();
        tabs.setStyle("-fx-background-color: #0d0d1a; -fx-tab-min-width: 120px;");
        tabs.getTabs().addAll(
            tabFichiers(),
            tabCreer(),
            tabFusionner(),
            tabExtraire(),
            tabTexte()
        );
        root.setCenter(tabs);

        // ── Bas : logs ──
        VBox bas = new VBox(4);
        Label logTitre = new Label("📋 Journal");
        logTitre.setTextFill(Color.web("#4cc9f0"));
        logTitre.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(4);
        logArea.setStyle(
            "-fx-control-inner-background: #0d0d1a; " +
            "-fx-text-fill: #a6e3a1; " +
            "-fx-font-family: monospace; -fx-font-size: 11px;"
        );
        bas.getChildren().addAll(logTitre, logArea);
        root.setBottom(bas);
        BorderPane.setMargin(bas, new Insets(12,0,0,0));

        return new Scene(root, 780, 680);
    }

    // ── Onglet 1 : Fichiers ──────────────────────────────
    private Tab tabFichiers() {
        Tab tab = new Tab("📁 Fichiers");
        tab.setClosable(false);

        VBox box = styledBox();

        listeFichiers = new ListView<>();
        listeFichiers.setStyle(
            "-fx-background-color: #0d0d1a; " +
            "-fx-control-inner-background: #0d0d1a;"
        );
        listeFichiers.setPrefHeight(220);
        listeFichiers.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText((item.endsWith(".pdf") ? "📄 " : "🖼️ ") + item);
                    setTextFill(Color.web("#cdd6f4"));
                    setStyle("-fx-background-color: transparent; -fx-font-size: 13px;");
                }
            }
        });

        // Boutons
        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER_LEFT);

        Button btnRefresh  = actionBtn("🔄 Actualiser", "#3d3d5c");
        Button btnUpload   = actionBtn("⬆️ Uploader",   "#7209b7");
        Button btnDownload = actionBtn("⬇️ Télécharger","#3d3d5c");
        Button btnInfos    = actionBtn("ℹ️ Infos",       "#3d3d5c");

        btnRefresh.setOnAction(e -> rafraichirListe());

        btnUpload.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir un PDF");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            File f = fc.showOpenDialog(primaryStage);
            if (f != null) {
                new Thread(() -> {
                    try {
                        byte[] contenu = Files.readAllBytes(f.toPath());
                        service.uploadFichier(f.getName(), contenu);
                        Platform.runLater(() -> {
                            log("✅ Uploadé : " + f.getName() + " (" + contenu.length + " octets)");
                            rafraichirListe();
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> log("❌ Upload : " + ex.getMessage()));
                    }
                }).start();
            }
        });

        btnDownload.setOnAction(e -> {
            String sel = listeFichiers.getSelectionModel().getSelectedItem();
            if (sel == null) { log("⚠️ Sélectionnez un fichier"); return; }
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(sel);
            File dest = fc.showSaveDialog(primaryStage);
            if (dest != null) {
                new Thread(() -> {
                    try {
                        byte[] data = service.downloadFichier(sel);
                        Files.write(dest.toPath(), data);
                        Platform.runLater(() -> log("✅ Téléchargé : " + dest.getName()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> log("❌ Download : " + ex.getMessage()));
                    }
                }).start();
            }
        });

        btnInfos.setOnAction(e -> {
            String sel = listeFichiers.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.endsWith(".pdf")) { log("⚠️ Sélectionnez un PDF"); return; }
            new Thread(() -> {
                try {
                    InfosPDF infos = service.getInfos(sel);
                    Platform.runLater(() -> {
                        log("📄 " + infos.nomFichier + " | Pages: " + infos.nombrePages +
                            " | Titre: " + infos.titre + " | Auteur: " + infos.auteur +
                            " | Taille: " + infos.tailleFichier + " o");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ " + ex.getMessage()));
                }
            }).start();
        });

        btns.getChildren().addAll(btnRefresh, btnUpload, btnDownload, btnInfos);
        box.getChildren().addAll(sectionLabel("Fichiers disponibles sur le serveur :"), listeFichiers, btns);
        tab.setContent(box);
        rafraichirListe();
        return tab;
    }

    // ── Onglet 2 : Créer PDF ─────────────────────────────
    private Tab tabCreer() {
        Tab tab = new Tab("✏️ Créer");
        tab.setClosable(false);

        VBox box = styledBox();

        TextArea txtContenu = new TextArea();
        txtContenu.setPromptText("Tapez le contenu du PDF ici...");
        txtContenu.setPrefRowCount(12);
        txtContenu.setStyle(
            "-fx-control-inner-background: #0d0d1a; " +
            "-fx-text-fill: #cdd6f4; -fx-font-size: 13px;"
        );

        HBox ligneNom = new HBox(10);
        ligneNom.setAlignment(Pos.CENTER_LEFT);
        TextField txtNom = styledField("document.pdf");
        ligneNom.getChildren().addAll(connLabel("Nom du fichier :"), txtNom);

        Button btnCreer = accentBtn("✏️ Créer le PDF");
        btnCreer.setOnAction(e -> {
            if (txtContenu.getText().trim().isEmpty()) { log("⚠️ Contenu vide"); return; }
            new Thread(() -> {
                try {
                    byte[] data = service.creerPDF(txtContenu.getText(), txtNom.getText());
                    Platform.runLater(() -> {
                        log("✅ PDF créé : " + txtNom.getText() + " (" + data.length + " octets)");
                        rafraichirListe();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ " + ex.getMessage()));
                }
            }).start();
        });

        box.getChildren().addAll(sectionLabel("Contenu :"), txtContenu, ligneNom, btnCreer);
        tab.setContent(box);
        return tab;
    }

    // ── Onglet 3 : Fusionner ─────────────────────────────
    private Tab tabFusionner() {
        Tab tab = new Tab("🔗 Fusionner");
        tab.setClosable(false);

        VBox box = styledBox();

        TextField f1  = styledField("fichier1.pdf");
        TextField f2  = styledField("fichier2.pdf");
        TextField res = styledField("fusion.pdf");

        Button btn = accentBtn("🔗 Fusionner les PDFs");
        btn.setOnAction(e -> {
            new Thread(() -> {
                try {
                    byte[] data = service.fusionner(f1.getText(), f2.getText(), res.getText());
                    Platform.runLater(() -> {
                        log("✅ Fusion : " + f1.getText() + " + " + f2.getText() + " → " + res.getText());
                        rafraichirListe();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ " + ex.getMessage()));
                }
            }).start();
        });

        box.getChildren().addAll(
            sectionLabel("PDF 1 :"), f1,
            sectionLabel("PDF 2 :"), f2,
            sectionLabel("Résultat :"), res,
            btn
        );
        tab.setContent(box);
        return tab;
    }

    // ── Onglet 4 : Extraire pages ────────────────────────
    private Tab tabExtraire() {
        Tab tab = new Tab("✂️ Extraire");
        tab.setClosable(false);

        VBox box = styledBox();

        TextField fSrc   = styledField("source.pdf");
        TextField fDebut = styledField("1");
        TextField fFin   = styledField("3");
        TextField fRes   = styledField("extrait.pdf");

        HBox lignePages = new HBox(10);
        lignePages.setAlignment(Pos.CENTER_LEFT);
        lignePages.getChildren().addAll(
            connLabel("Page début :"), fDebut,
            connLabel("Page fin :"),   fFin
        );

        Button btn = accentBtn("✂️ Extraire les pages");
        btn.setOnAction(e -> {
            new Thread(() -> {
                try {
                    byte[] data = service.extrairePages(
                        fSrc.getText(),
                        Integer.parseInt(fDebut.getText()),
                        Integer.parseInt(fFin.getText()),
                        fRes.getText()
                    );
                    Platform.runLater(() -> {
                        log("✅ Extraction pages " + fDebut.getText() + "-" + fFin.getText() + " → " + fRes.getText());
                        rafraichirListe();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ " + ex.getMessage()));
                }
            }).start();
        });

        box.getChildren().addAll(
            sectionLabel("Fichier source :"), fSrc,
            lignePages,
            sectionLabel("Résultat :"), fRes,
            btn
        );
        tab.setContent(box);
        return tab;
    }

    // ── Onglet 5 : Extraire texte ────────────────────────
    private Tab tabTexte() {
        Tab tab = new Tab("📝 Texte");
        tab.setClosable(false);

        VBox box = styledBox();

        HBox ligne = new HBox(10);
        ligne.setAlignment(Pos.CENTER_LEFT);
        TextField fSrc = styledField("document.pdf");
        ligne.getChildren().addAll(connLabel("Fichier PDF :"), fSrc);

        TextArea txtRes = new TextArea();
        txtRes.setEditable(false);
        txtRes.setPrefRowCount(14);
        txtRes.setStyle(
            "-fx-control-inner-background: #0d0d1a; " +
            "-fx-text-fill: #cdd6f4; " +
            "-fx-font-family: monospace; -fx-font-size: 12px;"
        );

        Button btn = accentBtn("📝 Extraire le texte");
        btn.setOnAction(e -> {
            new Thread(() -> {
                try {
                    String[] pages = service.extraireTexte(fSrc.getText());
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < pages.length; i++) {
                        sb.append("══ Page ").append(i+1).append(" ══\n");
                        sb.append(pages[i]).append("\n");
                    }
                    Platform.runLater(() -> {
                        txtRes.setText(sb.toString());
                        log("✅ Texte extrait : " + pages.length + " page(s)");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ " + ex.getMessage()));
                }
            }).start();
        });

        box.getChildren().addAll(ligne, btn, sectionLabel("Contenu :"), txtRes);
        tab.setContent(box);
        return tab;
    }

    // ═══════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════
    private void rafraichirListe() {
        new Thread(() -> {
            try {
                String[] fichiers = service.listerFichiers();
                Platform.runLater(() -> {
                    listeFichiers.getItems().clear();
                    listeFichiers.getItems().addAll(fichiers);
                    log("🔄 " + fichiers.length + " fichier(s) sur le serveur");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> log("❌ " + ex.getMessage()));
            }
        }).start();
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private VBox styledBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #1a1a2e;");
        return box;
    }

    private Label sectionLabel(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.web("#4cc9f0"));
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        return l;
    }

    private Label connLabel(String t) {
        Label l = new Label(t);
        l.setTextFill(Color.web("#cdd6f4"));
        l.setFont(Font.font("Segoe UI", 13));
        return l;
    }

    private TextField styledField(String val) {
        TextField tf = new TextField(val);
        tf.setStyle(
            "-fx-background-color: #2d2d44; -fx-text-fill: white; " +
            "-fx-font-size: 13px; -fx-min-width: 200px; -fx-background-radius: 6;"
        );
        return tf;
    }

    private Button accentBtn(String t) {
        Button b = new Button(t);
        b.setStyle(
            "-fx-background-color: #4cc9f0; -fx-text-fill: #1a1a2e; " +
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; " +
            "-fx-padding: 8 20; -fx-background-radius: 8;"
        );
        b.setOnMouseEntered(e -> b.setStyle(
            "-fx-background-color: #7209b7; -fx-text-fill: white; " +
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; " +
            "-fx-padding: 8 20; -fx-background-radius: 8;"
        ));
        b.setOnMouseExited(e -> b.setStyle(
            "-fx-background-color: #4cc9f0; -fx-text-fill: #1a1a2e; " +
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; " +
            "-fx-padding: 8 20; -fx-background-radius: 8;"
        ));
        return b;
    }

    private Button actionBtn(String t, String bg) {
        Button b = new Button(t);
        b.setStyle(
            "-fx-background-color: " + bg + "; -fx-text-fill: #cdd6f4; " +
            "-fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 6;"
        );
        return b;
    }

    @Override
    public void stop() { if (orb != null) orb.destroy(); }
}
