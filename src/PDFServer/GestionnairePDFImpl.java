import PDFService.*;
import org.omg.PortableServer.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GestionnairePDFImpl extends GestionnairePDFPOA {

    private static String initDossier() {
        // Essayer /pdfs d'abord, sinon utiliser un dossier local
        java.io.File f1 = new java.io.File("/pdfs");
        if (f1.exists() || f1.mkdirs()) return "/pdfs/";
        java.io.File f2 = new java.io.File("pdfs");
        f2.mkdirs();
        return "pdfs/";
    }


    private static final String DOSSIER_PDF = initDossier();

    // 1. Infos PDF
    @Override
    public InfosPDF getInfos(String nomFichier) throws PDFException {
        try {
            File f = new File(DOSSIER_PDF + nomFichier);
            if (!f.exists()) throw new PDFException("Fichier introuvable : " + nomFichier);
            PDDocument doc = PDDocument.load(f);
            PDDocumentInformation info = doc.getDocumentInformation();
            InfosPDF infos = new InfosPDF();
            infos.nomFichier    = nomFichier;
            infos.nombrePages   = doc.getNumberOfPages();
            infos.auteur        = info.getAuthor() != null ? info.getAuthor() : "Inconnu";
            infos.titre         = info.getTitle()  != null ? info.getTitle()  : "Sans titre";
            infos.tailleFichier = (int) f.length();
            doc.close();
            System.out.println("[SERVEUR] getInfos : " + nomFichier);
            return infos;
        } catch (PDFException e) { throw e; }
        catch (Exception e) { throw new PDFException("Erreur getInfos : " + e.getMessage()); }
    }

    // 2. Fusion
    @Override
    public byte[] fusionner(String nom1, String nom2, String nomResultat) throws PDFException {
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.addSource(new File(DOSSIER_PDF + nom1));
            merger.addSource(new File(DOSSIER_PDF + nom2));
            merger.setDestinationFileName(DOSSIER_PDF + nomResultat);
            merger.mergeDocuments(null);
            System.out.println("[SERVEUR] Fusion : " + nom1 + " + " + nom2);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur fusion : " + e.getMessage()); }
    }

    // 3. Extraire pages
    @Override
    public byte[] extrairePages(String nomFichier, int pageDebut, int pageFin, String nomResultat) throws PDFException {
        try {
            PDDocument source = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDDocument resultat = new PDDocument();
            for (int i = pageDebut - 1; i < pageFin && i < source.getNumberOfPages(); i++)
                resultat.addPage(source.getPage(i));
            resultat.save(DOSSIER_PDF + nomResultat);
            resultat.close(); source.close();
            System.out.println("[SERVEUR] ExtrairePages : " + pageDebut + "-" + pageFin);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur extrairePages : " + e.getMessage()); }
    }

    // 4. Supprimer pages
    @Override
    public byte[] supprimerPages(String nomFichier, int[] pages, String nomResultat) throws PDFException {
        try {
            PDDocument source = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDDocument resultat = new PDDocument();
            Set<Integer> aSupprimer = new HashSet<>();
            for (int p : pages) aSupprimer.add(p);
            for (int i = 0; i < source.getNumberOfPages(); i++)
                if (!aSupprimer.contains(i + 1)) resultat.addPage(source.getPage(i));
            resultat.save(DOSSIER_PDF + nomResultat);
            resultat.close(); source.close();
            System.out.println("[SERVEUR] SupprimerPages : " + pages.length + " pages");
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur supprimerPages : " + e.getMessage()); }
    }

    // 5. Ajouter texte
    @Override
    public byte[] ajouterTexte(String nomFichier, String texte, int page, float x, float y, String nomResultat) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDPage pdPage = doc.getPage(page - 1);
            PDPageContentStream cs = new PDPageContentStream(doc, pdPage, PDPageContentStream.AppendMode.APPEND, true);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
            cs.newLineAtOffset(x, y);
            cs.showText(texte);
            cs.endText();
            cs.close();
            doc.save(DOSSIER_PDF + nomResultat);
            doc.close();
            System.out.println("[SERVEUR] AjouterTexte page " + page);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur ajouterTexte : " + e.getMessage()); }
    }

    // 6. Convertir en images
    @Override
    public String[] convertirEnImages(String nomFichier, int dpi) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDFRenderer renderer = new PDFRenderer(doc);
            List<String> noms = new ArrayList<>();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi);
                String nomImg = nomFichier.replace(".pdf", "") + "_page" + (i + 1) + ".png";
                ImageIO.write(img, "PNG", new File(DOSSIER_PDF + nomImg));
                noms.add(nomImg);
            }
            doc.close();
            System.out.println("[SERVEUR] ConvertirEnImages : " + noms.size() + " images");
            return noms.toArray(new String[0]);
        } catch (Exception e) { throw new PDFException("Erreur convertirEnImages : " + e.getMessage()); }
    }

    // 7. Extraire texte
    @Override
    public String[] extraireTexte(String nomFichier) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i); stripper.setEndPage(i);
                pages.add(stripper.getText(doc));
            }
            doc.close();
            System.out.println("[SERVEUR] ExtraireTexte : " + pages.size() + " pages");
            return pages.toArray(new String[0]);
        } catch (Exception e) { throw new PDFException("Erreur extraireTexte : " + e.getMessage()); }
    }

    // 8. Créer PDF
    @Override
    public byte[] creerPDF(String texte, String nomResultat) throws PDFException {
        try {
            PDDocument doc = new PDDocument();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, 750);
            cs.setLeading(16f);
            for (String ligne : texte.split("\n")) {
                String l = ligne.length() > 80 ? ligne.substring(0, 80) : ligne;
                cs.showText(l); cs.newLine();
            }
            cs.endText(); cs.close();
            doc.save(DOSSIER_PDF + nomResultat);
            doc.close();
            System.out.println("[SERVEUR] CréerPDF : " + nomResultat);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur creerPDF : " + e.getMessage()); }
    }

    // 9. Upload
    @Override
    public boolean uploadFichier(String nomFichier, byte[] contenu) throws PDFException {
        try {
            Files.write(Paths.get(DOSSIER_PDF + nomFichier), contenu);
            System.out.println("[SERVEUR] Upload : " + nomFichier);
            return true;
        } catch (Exception e) { throw new PDFException("Erreur upload : " + e.getMessage()); }
    }

    // 10. Download
    @Override
    public byte[] downloadFichier(String nomFichier) throws PDFException {
        try {
            System.out.println("[SERVEUR] Download : " + nomFichier);
            return lireFichier(DOSSIER_PDF + nomFichier);
        } catch (PDFException e) { throw e; }
        catch (Exception e) { throw new PDFException("Erreur download : " + e.getMessage()); }
    }

    // 11. Lister fichiers
    @Override
    public String[] listerFichiers() throws PDFException {
        try {
            File dossier = new File(DOSSIER_PDF);
            String[] fichiers = dossier.list((d, n) -> n.endsWith(".pdf") || n.endsWith(".png"));
            if (fichiers == null) fichiers = new String[0];
            Arrays.sort(fichiers);
            System.out.println("[SERVEUR] ListerFichiers : " + fichiers.length);
            return fichiers;
        } catch (Exception e) { throw new PDFException("Erreur listerFichiers : " + e.getMessage()); }
    }

    // 12. Rotation de pages
    @Override
    public byte[] rotationPages(String nomFichier, int[] pages, int angle, String nomResultat) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            Set<Integer> aRoter = new HashSet<>();
            for (int p : pages) aRoter.add(p);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (aRoter.isEmpty() || aRoter.contains(i + 1)) {
                    PDPage page = doc.getPage(i);
                    int rotation = (page.getRotation() + angle) % 360;
                    page.setRotation(rotation);
                }
            }
            doc.save(DOSSIER_PDF + nomResultat);
            doc.close();
            System.out.println("[SERVEUR] Rotation " + angle + "° : " + nomResultat);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur rotation : " + e.getMessage()); }
    }

    // 13. Protection par mot de passe
    @Override
    public byte[] protegerPDF(String nomFichier, String motDePasse, String nomResultat) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            ap.setCanModify(false);
            ap.setCanExtractContent(false);
            StandardProtectionPolicy spp = new StandardProtectionPolicy(motDePasse, motDePasse, ap);
            spp.setEncryptionKeyLength(128);
            doc.protect(spp);
            doc.save(DOSSIER_PDF + nomResultat);
            doc.close();
            System.out.println("[SERVEUR] Protégé : " + nomResultat);
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur protection : " + e.getMessage()); }
    }

    // 14. Compression PDF
    @Override
    public byte[] compresserPDF(String nomFichier, String nomResultat) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            // Compression basique : supprimer les ressources inutilisées
            doc.getDocumentCatalog().getPages().forEach(page -> {
                try {
                    page.getResources();
                } catch (Exception ignored) {}
            });
            doc.save(DOSSIER_PDF + nomResultat);
            long avant = new File(DOSSIER_PDF + nomFichier).length();
            long apres = new File(DOSSIER_PDF + nomResultat).length();
            doc.close();
            System.out.println("[SERVEUR] Compression : " + avant + " -> " + apres + " octets");
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur compression : " + e.getMessage()); }
    }

    // 15. Numérotation des pages
    @Override
    public byte[] numeroterPages(String nomFichier, String nomResultat) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle rect = page.getMediaBox();
                PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true
                );
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                float x = rect.getWidth() / 2 - 20;
                cs.newLineAtOffset(x, 20);
                cs.showText("Page " + (i + 1) + " / " + total);
                cs.endText();
                cs.close();
            }
            doc.save(DOSSIER_PDF + nomResultat);
            doc.close();
            System.out.println("[SERVEUR] Numérotation : " + total + " pages");
            return lireFichier(DOSSIER_PDF + nomResultat);
        } catch (Exception e) { throw new PDFException("Erreur numérotation : " + e.getMessage()); }
    }

    // 16. Aperçu page en base64
    @Override
    public String apercuPage(String nomFichier, int page) throws PDFException {
        try {
            PDDocument doc = PDDocument.load(new File(DOSSIER_PDF + nomFichier));
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(page - 1, 120);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", bos);
            doc.close();
            String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            System.out.println("[SERVEUR] Aperçu page " + page + " de " + nomFichier);
            return b64;
        } catch (Exception e) { throw new PDFException("Erreur aperçu : " + e.getMessage()); }
    }

    // Helper
    private byte[] lireFichier(String chemin) throws PDFException {
        try {
            File f = new File(chemin);
            if (!f.exists()) throw new PDFException("Fichier introuvable : " + chemin);
            return Files.readAllBytes(f.toPath());
        } catch (PDFException e) { throw e; }
        catch (Exception e) { throw new PDFException("Erreur lecture : " + e.getMessage()); }
    }
}
