import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.xssf.usermodel.*;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import static io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY;
import static java.lang.Boolean.parseBoolean;

public class Handler {

    private static Logger LOGGER = Logger.getLogger(Handler.class);

    private static final String DEFAULT_BC_SG_URL = "https://bourse.societegenerale.fr/EmcWebApi/api/ProductSearch/Export?PageNum=1&ProductClassificationId=19&AssetTypeId=2&AssetTypeMenuId=35&BarrierHit=1";
    private static final String DEFAULT_MODEL_URL = "https://raw.githubusercontent.com/qlefevre/bonuscappes/main/xlsx/modele_indices.xlsx";
    private static final String DEFAULT_PORT = "8080";

    public static void main(String[] args) {
        String port = SystemUtils.getEnvironmentVariable("PORT", DEFAULT_PORT);
        LOGGER.infof("HTTP server listening on 0.0.0.0:%s", port);
        Undertow server = Undertow.builder()
                .addHttpListener(Integer.parseInt(port), "0.0.0.0")
                .setHandler(Handlers.pathTemplate().add("/sg/{assetType}", exchange -> {

                    // https://howtodoinjava.com/java/library/readingwriting-excel-files-in-java-poi-tutorial/

                    // Type
                    PathTemplateMatch pathMatch = exchange.getAttachment(ATTACHMENT_KEY);
                    String assetType = pathMatch.getParameters().get(("assetType"));
                    LOGGER.infof("Selected asset type: %s", assetType);

                    // Téléchargement
                    ByteArrayInputStream excelSG = downloadSG(assetType);
                    ByteArrayInputStream excelModel = downloadModel(assetType);

                    // Bonus Cappes SG Excel
                    XSSFWorkbook bcSgWb = new XSSFWorkbook(excelSG);
                    XSSFSheet exportBcSgWs = bcSgWb.getSheet("EXPORT");

                    // Modèle
                    XSSFWorkbook modelWb = new XSSFWorkbook(excelModel);
                    XSSFSheet exportModelWs = modelWb.getSheet("EXPORT");

                    // Copie l'onglet modèle
                    copySheet(exportBcSgWs,exportModelWs);
                    XSSFSheet calculModelWs = modelWb.getSheet("calcul");
                    removeRows(calculModelWs,exportModelWs.getLastRowNum());
                    XSSFFormulaEvaluator.evaluateAllFormulaCells(modelWb);

                    // Sauvegarde
                    ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
                    try (OutputStream os = excelOutput) {
                        modelWb.write(os);
                    }

                    String body = Base64.getEncoder().encodeToString(excelOutput.toByteArray());
                    String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
                    String filename = "Bonus_Cappes_SG_Indices_%s.xlsx".formatted(date);

                    boolean devMode = !exchange.getQueryParameters().get("dev").isEmpty();
                    if(devMode){
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION,"attachment;filename=%s".formatted(filename));
                        exchange.getResponseSender().send(ByteBuffer.wrap(excelOutput.toByteArray()));
                    }else {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                        exchange.getResponseSender().send("""
                                {
                                    "body": "%s",
                                    "statusCode": 200,
                                    "isBase64Encoded": true,
                                    "headers": {
                                        "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "Content-Disposition": "attachment;filename=%s"
                                    }
                                }""".formatted(body, filename));
                    }
                })).build();
        server.start();
    }

    private static void removeRows(XSSFSheet calculModelWs, int lastRowNum) {
        int fRow = lastRowNum+1;
        int lRow = calculModelWs.getLastRowNum();
        for (int iRow = fRow; iRow <= lRow; iRow++) {
            XSSFRow row = calculModelWs.getRow(iRow);
            calculModelWs.removeRow(row);
        }
    }

    private static void copySheet(XSSFSheet sheet, XSSFSheet mySheet) {
        int fRow = sheet.getFirstRowNum();
        int lRow = sheet.getLastRowNum();
        for (int iRow = fRow; iRow <= lRow; iRow++) {
            XSSFRow row = sheet.getRow(iRow);
            XSSFRow myRow = mySheet.createRow(iRow);
            if (row != null) {
                int fCell = row.getFirstCellNum();
                int lCell = row.getLastCellNum();
                for (int iCell = fCell; iCell < lCell; iCell++) {
                    XSSFCell cell = row.getCell(iCell);
                    XSSFCell myCell = myRow.createCell(iCell);
                    if (cell != null) {
                        myCell.setCellType(cell.getCellType());
                        switch (cell.getCellType()) {
                            case BLANK:
                                myCell.setCellValue("");
                                break;
                            case BOOLEAN:
                                myCell.setCellValue(cell.getBooleanCellValue());
                                break;
                            case ERROR:
                                myCell.setCellErrorValue(cell.getErrorCellValue());
                                break;
                            case FORMULA:
                                myCell.setCellFormula(cell.getCellFormula());
                                break;
                            case NUMERIC:
                                myCell.setCellValue(cell.getNumericCellValue());
                                break;
                            case STRING:
                                myCell.setCellValue(cell.getStringCellValue());
                                break;
                            default:
                                myCell.setCellFormula(cell.getCellFormula());
                        }
                    }
                }
            }
        }


    }

    private static ByteArrayInputStream downloadModel(String assetType) {
        String url = SystemUtils.getEnvironmentVariable("MODELE_" + assetType.toUpperCase(), DEFAULT_MODEL_URL);
        return downloadURL(url);
    }

    private static ByteArrayInputStream downloadSG(String assetType) {
        String url = SystemUtils.getEnvironmentVariable("BC_SG_" + assetType.toUpperCase(), DEFAULT_BC_SG_URL);
        return downloadURL(url);
    }

    private static ByteArrayInputStream downloadURL(String url) {
        LOGGER.infof("Download %s", url);
        try (InputStream is = new URL(url).openStream()) {
            return new ByteArrayInputStream(is.readAllBytes());
        } catch (Exception e) {
            LOGGER.errorf("Error while downloading %s", url);
            throw new RuntimeException(e);
        }
    }

}
