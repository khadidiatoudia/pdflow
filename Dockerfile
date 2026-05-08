FROM eclipse-temurin:8-jdk

WORKDIR /app

# Copier les libs PDFBox
COPY lib/ /app/lib/

# Copier les sources
COPY src/ /app/src/

# Copier l'interface web
COPY web/ /app/web/

# Créer les dossiers nécessaires
RUN mkdir -p /pdfs /app/bin

# Générer les stubs IDL
RUN idlj -fall -td /app/src /app/src/PDFService.idl 2>/dev/null || true

# Compiler les stubs IDL
RUN javac -d /app/bin /app/src/PDFService/*.java

# Compiler le serveur
RUN javac -cp /app/bin:/app/lib/* -d /app/bin \
    /app/src/PDFServer/GestionnairePDFImpl.java \
    /app/src/PDFServer/StartServer.java

# Port HTTP
EXPOSE 8080

# Lancement
CMD orbd -ORBInitialPort 1050 & \
    sleep 3 && \
    java -cp /app/bin:/app/lib/* \
    StartServer \
    -ORBInitialPort 1050 \
    -ORBInitialHost localhost
