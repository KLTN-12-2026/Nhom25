package org.example.meetingbe.service.meeting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MeetingDocument {
    private MeetingDocument() {}

    public static byte[] toDocx(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            write(zip, "[Content_Types].xml", """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            write(zip, "_rels/.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            StringBuilder document = new StringBuilder("""
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                    """);
            for (String line : text.split("\\R", -1)) {
                document.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                        .append(escapeXml(line)).append("</w:t></w:r></w:p>");
            }
            document.append("</w:body></w:document>");
            write(zip, "word/document.xml", document.toString());
        }
        return bytes.toByteArray();
    }

    private static String escapeXml(String text) {
        return text.replaceAll("[^\\x{9}\\x{20}-\\x{D7FF}\\x{E000}-\\x{FFFD}\\x{10000}-\\x{10FFFF}]", "")
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void write(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
