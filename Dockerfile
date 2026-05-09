FROM eclipse-temurin:8-jdk

WORKDIR /app

COPY lib/ /app/lib/
COPY src/ /app/src/
COPY web/ /app/web/

RUN mkdir -p /pdfs /app/bin

RUN javac -d /app/bin /app/src/PDFService/*.java

RUN javac -cp /app/bin:/app/lib/* -d /app/bin \
    /app/src/PDFServer/AuthManager.java \
    /app/src/PDFServer/GestionnairePDFImpl.java \
    /app/src/PDFServer/StartServerWeb.java

EXPOSE 8080

CMD java -cp /app/bin:/app/lib/* StartServerWeb
