import io.undertow.Undertow;
import io.undertow.util.Headers;
import org.apache.commons.lang3.SystemUtils;

import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

public class Handler {

    private static Logger LOGGER = Logger.getLogger(Handler.class);

    private static final String DEFAULT_MODEL_URL = "https://raw.githubusercontent.com/qlefevre/bonuscappes/main/xlsx/modele_indices.xlsx";
    private static final String DEFAULT_PORT = "8080";
// https://www.stubbornjava.com/posts/query-parameters-and-path-parameters-in-undertow
    public static void main(String[] args) {
        String port = SystemUtils.getEnvironmentVariable("PORT", DEFAULT_PORT);
        LOGGER.infof("HTTP server listening on 0.0.0.0:" + port);
        Undertow server = Undertow.builder()
                .addHttpListener(Integer.parseInt(port), "0.0.0.0")
                .setHandler(exchange -> {

                    ByteArrayInputStream excelModel = downloadModel("indices");

                    String body = Base64.getEncoder().encodeToString(excelModel.readAllBytes());
                    String filename = "Bonus_Cappes_SG_Indices_"+new SimpleDateFormat( "yyyyMMdd").format(new Date())+".xlsx";

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    exchange.getResponseSender().send("""
                            {
                                "body": %s,
                                "statusCode": 200,
                                "isBase64Encoded": True,
                                "headers": {
                                    "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "Content-Disposition": "attachment;filename=%s"
                                }
                            }""".formatted(body,filename));
                }).build();
        server.start();
    }

    private static ByteArrayInputStream downloadModel(String assetType){
        String url = SystemUtils.getEnvironmentVariable("MODELE_"+assetType.toUpperCase(),DEFAULT_MODEL_URL);
        LOGGER.infof("Download %s",url);
        try(InputStream is = new URL(url).openStream()){
            return new ByteArrayInputStream(is.readAllBytes());
        } catch (Exception e) {
            LOGGER.errorf("Error while downloading %s", url);
            throw new RuntimeException(e);
        }
    }
}
