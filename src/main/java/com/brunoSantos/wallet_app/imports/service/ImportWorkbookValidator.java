package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.shared.exception.ImportException;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.MulBlankRecord;
import org.apache.poi.hssf.record.MulRKRecord;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Streaming preflight before POI allocates an in-memory workbook. */
final class ImportWorkbookValidator {
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final long MAX_EXPANDED_BYTES = 50L * 1024 * 1024;
    static final int MAX_DATA_ROWS = 5000;
    static final int MAX_COLUMNS = 20;

    private ImportWorkbookValidator() {}

    static Path prepare(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xls") && !name.endsWith(".xlsx")) {
            throw invalid("Formato permitido: XLS ou XLSX");
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_FILE_BYTES) {
            throw invalid("Arquivo deve conter dados e ter até 10 MiB");
        }
        Path path = Files.createTempFile("wallet-import-", ".workbook");
        try {
            // Do not trust MultipartFile.getSize(), nor ZIP entry size declarations.
            try (var input = file.getInputStream(); var output = Files.newOutputStream(path)) {
                byte[] buffer = new byte[8192];
                long size = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > MAX_FILE_BYTES) throw invalid("Arquivo excede 10 MiB");
                    output.write(buffer, 0, read);
                }
                if (size == 0) throw invalid("Arquivo vazio");
            }
            FileMagic magic = FileMagic.valueOf(path.toFile());
            if (name.endsWith(".xlsx") && magic == FileMagic.OOXML) {
                validateXlsx(path);
            } else if (name.endsWith(".xls") && magic == FileMagic.OLE2) {
                validateXls(path);
            } else {
                throw invalid("Conteúdo incompatível com XLS/XLSX");
            }
            return path;
        } catch (Exception ex) {
            Files.deleteIfExists(path);
            if (ex instanceof ImportException invalid) throw invalid;
            throw invalid("Arquivo XLS/XLSX inválido ou corrompido");
        }
    }

    private static void validateXlsx(Path path) throws Exception {
        long expanded = 0;
        byte[] buffer = new byte[8192];
        // Enumerate the central directory, as OPCPackage will, but count bytes
        // actually inflated instead of trusting each entry's declared size.
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                try (var input = zip.getInputStream(entries.nextElement())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        expanded += read;
                        if (expanded > MAX_EXPANDED_BYTES) throw invalid("XLSX descompactado excede 50 MiB");
                    }
                }
            }
        }
        // Follow actual OPC relationships, not conventional ZIP entry names:
        // a worksheet may legally live outside xl/worksheets/.
        try (var archive = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            var workbookReader = new XSSFReader(archive);
            var reader = XMLHelper.newXMLReader();
            reader.setContentHandler(new SheetBoundsHandler());
            try (var input = workbookReader.getWorkbookData()) {
                reader.parse(new InputSource(input));
            }
            int worksheets = 0;
            var sheets = workbookReader.getSheetsData();
            while (sheets.hasNext()) {
                try (var input = sheets.next()) {
                    if (++worksheets > 1) throw invalid("Permitida apenas 1 planilha");
                    reader.setContentHandler(new SheetBoundsHandler());
                    reader.parse(new InputSource(input));
                }
            }
            if (worksheets != 1) throw invalid("Arquivo deve conter 1 planilha");
        }
    }

    private static void validateXls(Path path) throws IOException {
        try (var filesystem = new POIFSFileSystem(path.toFile(), true)) {
            var request = new HSSFRequest();
            int[] sheets = {0};
            request.addListenerForAllRecords(record -> {
                if (record instanceof BoundSheetRecord && ++sheets[0] > 1) {
                    throw invalid("Permitida apenas 1 planilha");
                }
                if (record instanceof RowRecord row) checkBounds(row.getRowNumber(), row.getLastCol() - 1);
                if (record instanceof CellValueRecordInterface cell) checkBounds(cell.getRow(), cell.getColumn());
                if (record instanceof MulBlankRecord cells) checkBounds(cells.getRow(), cells.getLastColumn());
                if (record instanceof MulRKRecord cells) checkBounds(cells.getRow(), cells.getLastColumn());
            });
            new HSSFEventFactory().processWorkbookEvents(request, filesystem);
        }
    }

    static void validateWorkbook(Workbook workbook) {
        if (workbook.getNumberOfSheets() != 1) throw invalid("Permitida apenas 1 planilha");
        var sheet = workbook.getSheetAt(0);
        checkBounds(sheet.getLastRowNum(), -1);
        for (var row : sheet) checkBounds(row.getRowNum(), row.getLastCellNum() - 1);
    }

    private static void checkBounds(int row, int column) {
        if (row > MAX_DATA_ROWS || column >= MAX_COLUMNS) {
            throw invalid("Limite: 5000 linhas de dados e 20 colunas");
        }
    }

    private static ImportException invalid(String message) {
        return new ImportException(message);
    }

    private static final class SheetBoundsHandler extends DefaultHandler {
        private int sheets;
        private int rows;
        private int columns;

        @Override
        public void startElement(String uri, String local, String qualified, Attributes attributes) {
            switch (local) {
                case "sheet" -> {
                    if (++sheets > 1) throw invalid("Permitida apenas 1 planilha");
                }
                case "row" -> {
                    if (++rows > MAX_DATA_ROWS + 1) throw invalid("Limite: 5000 linhas de dados");
                    columns = 0;
                    String index = attributes.getValue("r");
                    if (index != null) checkBounds(Integer.parseInt(index) - 1, -1);
                }
                case "c" -> {
                    if (++columns > MAX_COLUMNS) throw invalid("Limite: 20 colunas");
                    String reference = attributes.getValue("r");
                    if (reference != null) {
                        int column = 0;
                        int i = 0;
                        while (i < reference.length() && reference.charAt(i) >= 'A' && reference.charAt(i) <= 'Z') {
                            column = column * 26 + reference.charAt(i++) - 'A' + 1;
                            if (column > MAX_COLUMNS) throw invalid("Limite: 20 colunas");
                        }
                        checkBounds(Integer.parseInt(reference.substring(i)) - 1, column - 1);
                    }
                }
                default -> { }
            }
        }
    }
}
