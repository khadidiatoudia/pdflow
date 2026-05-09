FROM eclipse-temurin:8-jdk

WORKDIR /app

COPY lib/ /app/lib/
COPY src/ /app/src/
COPY web/ /app/web/

RUN mkdir -p /pdfs /app/bin

# Compiler les stubs IDL
RUN javac -d /app/bin /app/src/PDFService/*.java

# Compiler le serveur web (sans CORBA)
RUN javac -cp /app/bin:/app/lib/* -d /app/bin \
    /app/src/PDFServer/GestionnairePDFImpl.java \
    /app/src/PDFServer/StartServerWeb.java

EXPOSE 8080

CMD java -cp /app/bin:/app/lib/* StartServerWeb
