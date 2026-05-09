FROM eclipse-temurin:8-jdk

WORKDIR /app

COPY lib/ /app/lib/
COPY src/ /app/src/
COPY web/ /app/web/

RUN mkdir -p /pdfs /app/bin

RUN idlj -fall -td /app/src /app/src/PDFService.idl 2>/dev/null || true

RUN javac -d /app/bin /app/src/PDFService/*.java

RUN javac -cp /app/bin:/app/lib/* -d /app/bin \
    /app/src/PDFServer/GestionnairePDFImpl.java \
    /app/src/PDFServer/StartServer.java

EXPOSE 8080

CMD orbd -ORBInitialPort 1050 & sleep 3 && java -cp /app/bin:/app/lib/* StartServer -ORBInitialPort 1050 -ORBInitialHost localhost
