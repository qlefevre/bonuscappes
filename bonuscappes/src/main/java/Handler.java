import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.xssf.usermodel.*;
import org.jboss.logging.Logger;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import static io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class Handler {

    private static final Logger LOGGER = Logger.getLogger(Handler.class);

    private static final String DEFAULT_BC_SG_URL = "https://bourse.societegenerale.fr/EmcWebApi/api/ProductSearch/Export?PageNum=1&ProductClassificationId=19&AssetTypeId=2&AssetTypeMenuId=35&BarrierHit=1";
    private static final String DEFAULT_MODEL_URL = "https://raw.githubusercontent.com/qlefevre/bonuscappes/main/xlsx/modele_indices.xlsx";
    private static final String DEFAULT_PORT = "8080";

    public static void main(String[] args) {
        String port = SystemUtils.getEnvironmentVariable("PORT", DEFAULT_PORT);
        LOGGER.infof("HTTP server listening on 0.0.0.0:%s", port);
        LOGGER.infof("Try http://localhost:%s/sg/indices", port);
        Undertow server = Undertow.builder()
                .addHttpListener(Integer.parseInt(port), "0.0.0.0")
                .setHandler(Handlers.pathTemplate().add("/sg/{assetType}", exchange -> {

                    // Type
                    PathTemplateMatch pathMatch = exchange.getAttachment(ATTACHMENT_KEY);
                    String assetType = pathMatch.getParameters().get(("assetType"));
                    LOGGER.infof("Selected asset type: %s", assetType);

                    // Téléchargement
                    ByteArrayInputStream excelSG = downloadSG(assetType);
                    ByteArrayInputStream excelModel = downloadModel(assetType);

                    // Fusionne les fichiers Excel
                    ByteArrayOutputStream excelOutput = mergeExcels(excelSG, excelModel);

                    String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
                    String filename = "Bonus_Cappes_SG_%s_%s.xlsx".formatted(capitalize(assetType),date);

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION,"attachment;filename=%s".formatted(filename));
                    exchange.getResponseSender().send(ByteBuffer.wrap(excelOutput.toByteArray()));

                })).build();
        server.start();
    }

    private static ByteArrayOutputStream mergeExcels(ByteArrayInputStream excelSG, ByteArrayInputStream excelModel) throws IOException {

        LOGGER.infof("Excel file generation in progress.");

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
        modelWb.setForceFormulaRecalculation(true);
        //XSSFFormulaEvaluator.evaluateAllFormulaCells(modelWb);

        // Sauvegarde
        ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
        try (OutputStream os = excelOutput) {
            modelWb.write(os);
        }

        LOGGER.infof("Excel file generation completed.");

        return excelOutput;
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
