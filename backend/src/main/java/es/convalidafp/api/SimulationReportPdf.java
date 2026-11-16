package es.convalidafp.api;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import es.convalidafp.api.SimulationDtos.CitationDto;
import es.convalidafp.api.SimulationDtos.ConditionalRecognitionDto;
import es.convalidafp.api.SimulationDtos.OptativeCreditsDto;
import es.convalidafp.api.SimulationDtos.RecognizedSubjectDto;
import es.convalidafp.api.SimulationDtos.RemainingSubjectDto;
import es.convalidafp.api.SimulationDtos.RemainingYearDto;
import es.convalidafp.api.SimulationDtos.SimulationResponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Informe PDF de una simulación, con el mismo desglose que la pantalla de resultado: cifra, tope,
 * asignaturas reconocidas por curso con su fuente (documento, página y fragmento literal),
 * condicionados aparte, asignaturas restantes y el aviso de que el resultado es orientativo.
 *
 * <p>Solo da formato a una {@link SimulationResponse} ya calculada: no decide nada.
 */
final class SimulationReportPdf {

  private static final Color INK = new Color(0x1f, 0x29, 0x37);
  private static final Color MUTED = new Color(0x4b, 0x55, 0x63);
  private static final Color BRAND = new Color(0x1d, 0x4e, 0xd8);
  private static final Color CAP = new Color(0x9a, 0x34, 0x12);
  private static final Color NOTICE_BG = new Color(0xfe, 0xf3, 0xc7);
  private static final Color LINE = new Color(0xd1, 0xd5, 0xdb);

  private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, INK);
  private static final Font H2 = new Font(Font.HELVETICA, 13, Font.BOLD, INK);
  private static final Font H3 = new Font(Font.HELVETICA, 10, Font.BOLD, MUTED);
  private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
  private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, INK);
  private static final Font SMALL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
  private static final Font SMALL_ITALIC = new Font(Font.HELVETICA, 8.5f, Font.ITALIC, MUTED);
  private static final Font BIG = new Font(Font.HELVETICA, 30, Font.BOLD, BRAND);
  private static final Font CAP_BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, CAP);

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  private SimulationReportPdf() {}

  static byte[] render(SimulationResponse r) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
    PdfWriter.getInstance(doc, out);
    doc.addTitle("ConvalidaFP - " + r.degree().name());
    doc.addCreator("ConvalidaFP");
    doc.open();

    doc.add(paragraph("ConvalidaFP · Informe de simulación", SMALL, 0));
    doc.add(paragraph(degreeLabel(r), TITLE, 4));
    String cycle = r.cycle().name() + (r.cycle().regulationRef() != null ? " (" + r.cycle().regulationRef() + ")" : "");
    doc.add(
        paragraph(
            nullToEmpty(r.degree().universityName()) + " · desde " + cycle + " · curso " + r.dataset().academicYear(),
            BODY,
            4));
    doc.add(notice(r.notice()));

    switch (r.outcome()) {
      case "RECOGNIZED" -> recognized(doc, r);
      case "NO_AGREEMENT" -> noAgreement(doc, r);
      default -> noData(doc);
    }

    String published =
        r.dataset().publishedAt() == null
            ? ""
            : ", actualizados el " + DATE.format(r.dataset().publishedAt().atZone(MADRID).toLocalDate());
    doc.add(paragraph("Datos del curso " + r.dataset().academicYear() + published + ".", SMALL, 16));
    doc.add(paragraph(r.notice(), SMALL, 2));
    doc.close();
    return out.toByteArray();
  }

  // ---------- reconocimiento calculado ----------

  private static void recognized(Document doc, SimulationResponse r) {
    Paragraph figure = new Paragraph();
    figure.setSpacingBefore(14);
    figure.add(new Chunk(ects(r.recognizedEcts()), BIG));
    figure.add(
        new Chunk(
            "  de " + number(r.degreeTotalEcts()) + " ECTS " + (Boolean.TRUE.equals(r.capped()) ? "reconocibles" : "reconocidos"),
            H2));
    doc.add(figure);
    doc.add(
        paragraph(
            percentage(r.recognizedPercentage()) + " del grado. Te quedarían " + ects(r.pendingEcts()) + " por cursar.",
            BODY,
            2));

    if (Boolean.TRUE.equals(r.capped())) {
      Paragraph capped = paragraph("El resultado está limitado por el tope. ", CAP_BOLD, 8);
      capped.add(
          new Chunk(
              "Las tablas suman " + ects(r.rulesTotalEcts()) + ", pero el máximo reconocible es "
                  + ects(r.appliedCapEcts()) + " (" + capReason(r) + "). La universidad decide qué asignaturas"
                  + " entran hasta ese límite.",
              BODY));
      doc.add(capped);
    } else {
      doc.add(
          paragraph(
              "El máximo reconocible en este grado es " + ects(r.appliedCapEcts()) + " (" + capReason(r) + ").",
              SMALL,
              4));
    }

    doc.add(
        heading(
            Boolean.TRUE.equals(r.capped())
                ? "Reconocibles hasta el tope de " + ects(r.appliedCapEcts())
                : "Asignaturas reconocidas"));
    if (r.recognizedSubjects().isEmpty()) {
      doc.add(paragraph("Con tu ciclo no se reconoce ninguna asignatura concreta de este grado.", BODY, 4));
    }
    Map<Integer, List<RecognizedSubjectDto>> byYear = new LinkedHashMap<>();
    for (RecognizedSubjectDto s : r.recognizedSubjects()) {
      byYear.computeIfAbsent(s.courseYear(), k -> new java.util.ArrayList<>()).add(s);
    }
    byYear.forEach(
        (year, subjects) -> {
          doc.add(paragraph(courseYear(year), H3, 8));
          PdfPTable table = table();
          subjects.forEach(s -> addItem(table, s.name(), s.ects(), null, s.source()));
          doc.add(table);
        });

    if (!r.optativeCredits().isEmpty()) {
      doc.add(heading("Créditos optativos"));
      doc.add(paragraph("Se reconocen como créditos optativos, sin corresponder a una asignatura concreta.", SMALL, 2));
      PdfPTable table = table();
      for (OptativeCreditsDto c : r.optativeCredits()) {
        addItem(table, "Créditos optativos", c.ects(), null, c.source());
      }
      doc.add(table);
    }

    if (!r.conditionalRecognitions().isEmpty()) {
      doc.add(heading("Reconocimientos condicionados"));
      doc.add(
          paragraph(
              "Dependen de una condición que valora la universidad. No están sumados en el total.", SMALL, 2));
      PdfPTable table = table();
      for (ConditionalRecognitionDto c : r.conditionalRecognitions()) {
        String condition =
            "Condición: "
                + (c.conditionText() != null ? c.conditionText() : "condición no especificada en el documento");
        addItem(table, c.subjectName() != null ? c.subjectName() : "Créditos optativos", c.ects(), condition, c.source());
      }
      doc.add(table);
    }

    int remaining = r.remainingByYear().stream().mapToInt(y -> y.subjects().size()).sum();
    doc.add(heading("Asignaturas restantes"));
    doc.add(
        paragraph(
            remaining + " asignaturas del grado que no aparecen en la tabla de reconocimiento."
                + (Boolean.TRUE.equals(r.capped())
                    ? " Como el resultado está limitado, además tendrás que cursar parte de las asignaturas de la"
                        + " lista anterior."
                    : ""),
            SMALL,
            2));
    for (RemainingYearDto year : r.remainingByYear()) {
      doc.add(paragraph(courseYear(year.courseYear()), H3, 8));
      PdfPTable table = table();
      for (RemainingSubjectDto s : year.subjects()) {
        table.addCell(cell(new Phrase(s.name() + "  ·  " + subjectType(s.subjectType()), BODY), Element.ALIGN_LEFT));
        table.addCell(cell(new Phrase(ects(s.ects()), BODY), Element.ALIGN_RIGHT));
      }
      doc.add(table);
    }
  }

  private static void noAgreement(Document doc, SimulationResponse r) {
    doc.add(heading("No existe tabla de reconocimiento firmada"));
    doc.add(
        paragraph(
            "Lo hemos comprobado: para pasar de este ciclo a este grado no hay ninguna tabla de reconocimiento"
                + " firmada. Eso significa que no hay asignaturas reconocidas de forma automática.",
            BODY,
            4));
    doc.add(
        paragraph(
            "Aun así, puedes preguntar en la universidad: a veces estudian las solicitudes una a una.", BODY, 4));
    if (r.noAgreement() != null) {
      if (r.noAgreement().notes() != null) {
        doc.add(paragraph("Nota: " + r.noAgreement().notes(), SMALL, 4));
      }
      if (r.noAgreement().source() != null) {
        var s = r.noAgreement().source();
        doc.add(
            paragraph(
                "Fuente: " + s.title() + " · " + documentType(s.documentType()) + date(s.publicationDate())
                    + (s.url() != null ? " · " + s.url() : ""),
                SMALL,
                4));
      }
    }
  }

  private static void noData(Document doc) {
    doc.add(heading("Todavía no tenemos datos de esta combinación"));
    doc.add(
        paragraph(
            "No hemos encontrado ningún documento oficial que relacione este ciclo con este grado. No significa"
                + " que no haya convenio: significa que todavía no lo hemos podido comprobar.",
            BODY,
            4));
    doc.add(
        paragraph(
            "Consulta directamente a la universidad para saber si reconoce créditos de tu ciclo.", BODY, 4));
  }

  // ---------- piezas ----------

  private static void addItem(PdfPTable table, String name, BigDecimal ects, String condition, CitationDto source) {
    Paragraph left = new Paragraph(name, BOLD);
    if (condition != null) {
      left.add(Chunk.NEWLINE);
      left.add(new Chunk(condition, BODY));
    }
    left.add(Chunk.NEWLINE);
    left.add(new Chunk(sourceLine(source), SMALL));
    if (source.excerpt() != null) {
      left.add(Chunk.NEWLINE);
      left.add(new Chunk("«" + source.excerpt() + "»", SMALL_ITALIC));
    }
    table.addCell(cell(left, Element.ALIGN_LEFT));
    table.addCell(cell(new Phrase(ects(ects), BOLD), Element.ALIGN_RIGHT));
  }

  private static String sourceLine(CitationDto s) {
    String title = s.title() != null ? s.title() : "Documento sin identificar";
    String type = s.documentType() != null ? " · " + documentType(s.documentType()) : "";
    return "Fuente: " + title + type + date(s.publicationDate()) + " · página " + s.page()
        + (s.url() != null ? " · " + s.url() : "");
  }

  private static PdfPTable table() {
    PdfPTable table = new PdfPTable(new float[] {5.2f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingBefore(4);
    return table;
  }

  private static PdfPCell cell(Phrase content, int align) {
    PdfPCell cell = new PdfPCell(content);
    cell.setBorder(Rectangle.BOTTOM);
    cell.setBorderColor(LINE);
    cell.setPaddingTop(5);
    cell.setPaddingBottom(7);
    cell.setHorizontalAlignment(align);
    return cell;
  }

  private static PdfPTable notice(String text) {
    PdfPTable box = new PdfPTable(1);
    box.setWidthPercentage(100);
    box.setSpacingBefore(12);
    PdfPCell cell = new PdfPCell(new Phrase(text, BOLD));
    cell.setBackgroundColor(NOTICE_BG);
    cell.setBorderColor(new Color(0xd9, 0x77, 0x06));
    cell.setPadding(8);
    box.addCell(cell);
    return box;
  }

  private static Paragraph heading(String text) {
    Paragraph p = new Paragraph(text, H2);
    p.setSpacingBefore(16);
    return p;
  }

  private static Paragraph paragraph(String text, Font font, float spacingBefore) {
    Paragraph p = new Paragraph(text, font);
    p.setSpacingBefore(spacingBefore);
    return p;
  }

  private static String degreeLabel(SimulationResponse r) {
    return r.degree().name() + (r.degree().center() != null ? " (" + r.degree().center() + ")" : "");
  }

  private static String capReason(SimulationResponse r) {
    return r.tableCapEcts() != null
            && r.legalCapEcts() != null
            && r.tableCapEcts().compareTo(r.legalCapEcts()) < 0
        ? "el tope propio de esta tabla de reconocimiento"
        : "el 25 % de la carga del grado";
  }

  /** Número en formato español, sin ceros sobrantes: 51, 4,5. */
  static String number(BigDecimal value) {
    if (value == null) {
      return "—";
    }
    return value.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  static String ects(BigDecimal value) {
    return value == null ? "ECTS sin determinar" : number(value) + " ECTS";
  }

  private static String percentage(BigDecimal value) {
    return value == null ? "—" : number(value) + " %";
  }

  private static String courseYear(Integer year) {
    return year == null ? "Curso sin determinar" : year + ".º curso";
  }

  private static String date(LocalDate date) {
    return date == null ? "" : " · " + DATE.format(date);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String documentType(String type) {
    return switch (type) {
      case "CONVENIO" -> "Convenio";
      case "ADENDA" -> "Adenda";
      case "TABLA_UNIVERSIDAD" -> "Tabla de la universidad";
      case "BOLETIN_OFICIAL" -> "Boletín oficial";
      default -> "Otro documento";
    };
  }

  private static String subjectType(String type) {
    if (type == null) {
      return "";
    }
    return switch (type) {
      case "BASICA" -> "Básica";
      case "OBLIGATORIA" -> "Obligatoria";
      case "OPTATIVA" -> "Optativa";
      case "PRACTICAS_EXTERNAS" -> "Prácticas externas";
      case "TFG" -> "Trabajo fin de grado";
      default -> type;
    };
  }
}
