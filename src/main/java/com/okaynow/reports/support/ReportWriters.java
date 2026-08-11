package com.okaynow.reports.support;

import com.okaynow.reports.dto.ReportMeta;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ReportWriters {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZONE);
    /** Brand teal #0D7377 — matches OkayNow logo / marketing. */
    private static final Color BRAND = new Color(0x0d, 0x73, 0x77);
    private static final Color INK = new Color(0x12, 0x1a, 0x24);
    private static final String LOGO_CLASSPATH = "static/branding/okaynow-logo.png";
    private static final String LOGO_FALLBACK_CLASSPATH = "static/branding/okaynow_primary_logo.png";

    private ReportWriters() {
    }

    public static byte[] excel(ReportMeta meta, List<String> headers, List<List<String>> rows)
            throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");
            CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);

            CellStyle labelStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font labelFont = wb.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);

            int r = 0;
            byte[] logoBytes = loadLogoBytes();
            if (logoBytes != null) {
                int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_PNG);
                CreationHelper helper = wb.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                anchor.setCol1(0);
                anchor.setRow1(0);
                Picture pict = drawing.createPicture(anchor, pictureIdx);
                // Primary wordmark is wide (~3.3:1); size for a readable header band.
                pict.resize(2.4);
                r = 5;
            }

            if (logoBytes == null) {
                Row brand = sheet.createRow(r++);
                Cell brandCell = brand.createCell(0);
                brandCell.setCellValue("OkayNow");
                brandCell.setCellStyle(titleStyle);
            }

            Row subtitle = sheet.createRow(r++);
            subtitle.createCell(0).setCellValue("Home care staffing · Massachusetts");

            Row title = sheet.createRow(r++);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(meta.title());
            titleCell.setCellStyle(labelStyle);

            Row gen = sheet.createRow(r++);
            gen.createCell(0).setCellValue("Generated");
            gen.createCell(1).setCellValue(WHEN.format(meta.generatedAt()));

            Row who = sheet.createRow(r++);
            who.createCell(0).setCellValue("Generated for");
            who.createCell(1).setCellValue(meta.generatedFor() != null ? meta.generatedFor() : "—");

            for (Map.Entry<String, String> filter : meta.filters().entrySet()) {
                Row fr = sheet.createRow(r++);
                Cell fk = fr.createCell(0);
                fk.setCellValue(filter.getKey());
                fk.setCellStyle(labelStyle);
                fr.createCell(1).setCellValue(filter.getValue());
            }

            r++; // blank
            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            for (List<String> row : rows) {
                Row data = sheet.createRow(r++);
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < row.size() && row.get(i) != null ? row.get(i) : "";
                    data.createCell(i).setCellValue(value);
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] pdf(ReportMeta meta, List<String> headers, List<List<String>> rows)
            throws DocumentException, IOException {
        Document document = new Document(PageSize.LETTER.rotate(), 36, 36, 42, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        addBrandHeader(document);

        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND);
        document.add(new Paragraph(" "));
        document.add(new Paragraph(meta.title(), titleFont));
        document.add(new Paragraph("Generated: " + WHEN.format(meta.generatedAt()), muted));
        document.add(new Paragraph("Generated for: "
                + (meta.generatedFor() != null ? meta.generatedFor() : "—"), muted));
        for (Map.Entry<String, String> filter : meta.filters().entrySet()) {
            document.add(new Paragraph(filter.getKey() + ": " + filter.getValue(), muted));
        }
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, INK);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            table.addCell(cell);
        }
        for (List<String> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                String value = i < row.size() && row.get(i) != null ? row.get(i) : "";
                PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                cell.setPadding(4);
                table.addCell(cell);
            }
        }
        document.add(table);
        if (rows.isEmpty()) {
            Paragraph empty = new Paragraph("No rows matched the selected filters.", muted);
            empty.setSpacingBefore(12);
            document.add(empty);
        }
        document.close();
        return out.toByteArray();
    }

    /**
     * Letter-size client invoice with OkayNow logo/header, bill-to block, line items, and total due.
     */
    public static byte[] invoicePdf(
            String invoiceNumber,
            String status,
            String billToName,
            String billToAddress,
            String billToContact,
            String issuedDate,
            String dueDate,
            String notes,
            List<InvoiceLine> lines,
            String totalAmount,
            String generatedFor
    ) throws DocumentException, IOException {
        Document document = new Document(PageSize.LETTER, 48, 48, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        addBrandHeader(document);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font moneyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("INVOICE", titleFont));
        document.add(new Paragraph(invoiceNumber + "  ·  " + status, muted));
        document.add(new Paragraph(" "));

        PdfPTable meta = new PdfPTable(new float[]{1f, 1f});
        meta.setWidthPercentage(100);
        meta.addCell(labelValueCell("Issued", issuedDate, labelFont, bodyFont));
        meta.addCell(labelValueCell("Due", dueDate, labelFont, bodyFont));
        document.add(meta);
        document.add(new Paragraph(" "));

        Paragraph billToLabel = new Paragraph("Bill to", labelFont);
        document.add(billToLabel);
        document.add(new Paragraph(billToName != null ? billToName : "—", bodyFont));
        if (billToAddress != null && !billToAddress.isBlank()) {
            document.add(new Paragraph(billToAddress, muted));
        }
        if (billToContact != null && !billToContact.isBlank()) {
            document.add(new Paragraph(billToContact, muted));
        }
        document.add(new Paragraph(" "));

        if (notes != null && !notes.isBlank()) {
            document.add(new Paragraph("Notes", labelFont));
            document.add(new Paragraph(notes, bodyFont));
            document.add(new Paragraph(" "));
        }

        PdfPTable table = new PdfPTable(new float[]{1.2f, 4.2f, 1f, 1.2f, 1.4f});
        table.setWidthPercentage(100);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
        for (String header : List.of("Date", "Description", "Hours", "Rate", "Amount")) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(6);
            table.addCell(cell);
        }
        for (InvoiceLine line : lines) {
            table.addCell(padded(line.shiftDate(), cellFont));
            table.addCell(padded(line.description(), cellFont));
            PdfPCell hours = padded(line.hours(), cellFont);
            hours.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
            table.addCell(hours);
            PdfPCell rate = padded(line.billRate(), cellFont);
            rate.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
            table.addCell(rate);
            PdfPCell amount = padded(line.amount(), cellFont);
            amount.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
            table.addCell(amount);
        }
        document.add(table);

        PdfPTable totalTable = new PdfPTable(new float[]{4f, 1.5f});
        totalTable.setWidthPercentage(100);
        totalTable.setSpacingBefore(12);
        PdfPCell totalLabel = new PdfPCell(new Phrase("Amount due", labelFont));
        totalLabel.setBorder(PdfPCell.NO_BORDER);
        totalLabel.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        totalLabel.setPadding(4);
        PdfPCell totalValue = new PdfPCell(new Phrase(totalAmount, moneyFont));
        totalValue.setBorder(PdfPCell.NO_BORDER);
        totalValue.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        totalValue.setPadding(4);
        totalTable.addCell(totalLabel);
        totalTable.addCell(totalValue);
        document.add(totalTable);

        document.add(new Paragraph(" "));
        Paragraph demand = new Paragraph(
                "Payment is requested by the due date above. Please contact OkayNow if you have questions about this invoice.",
                muted);
        document.add(demand);
        document.add(new Paragraph(" "));
        document.add(new Paragraph(
                "Generated: " + WHEN.format(java.time.Instant.now())
                        + " · For: " + (generatedFor != null ? generatedFor : "—"),
                muted));

        document.close();
        return out.toByteArray();
    }

    public record InvoiceLine(
            String shiftDate,
            String description,
            String hours,
            String billRate,
            String amount
    ) {
    }

    private static void addBrandHeader(Document document) throws DocumentException {
        Image logo = loadLogo();
        if (logo != null) {
            // Primary wordmark 1200×360 — keep readable on letter width.
            logo.scaleToFit(200, 60);
            logo.setAlignment(Image.ALIGN_LEFT);
            document.add(logo);
            Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Paragraph tag = new Paragraph("Home care staffing · Massachusetts", muted);
            tag.setSpacingBefore(4);
            tag.setSpacingAfter(8);
            document.add(tag);
            return;
        }

        PdfPTable brandTable = new PdfPTable(1);
        brandTable.setWidthPercentage(100);
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(PdfPCell.NO_BORDER);
        textCell.addElement(new Paragraph("OkayNow", brandFont));
        textCell.addElement(new Paragraph("Home care staffing · Massachusetts", muted));
        brandTable.addCell(textCell);
        document.add(brandTable);
    }

    private static PdfPCell labelValueCell(String label, String value, Font labelFont, Font bodyFont) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(label + "\n", labelFont));
        p.add(new Phrase(value != null ? value : "—", bodyFont));
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.addElement(p);
        return cell;
    }

    private static PdfPCell padded(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "", font));
        cell.setPadding(5);
        return cell;
    }

    private static Image loadLogo() {
        try {
            byte[] bytes = loadLogoBytes();
            return bytes == null ? null : Image.getInstance(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] loadLogoBytes() {
        byte[] bytes = readClasspathBytes(LOGO_CLASSPATH);
        if (bytes != null) {
            return bytes;
        }
        return readClasspathBytes(LOGO_FALLBACK_CLASSPATH);
    }

    private static byte[] readClasspathBytes(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ex) {
            return null;
        }
    }
}
