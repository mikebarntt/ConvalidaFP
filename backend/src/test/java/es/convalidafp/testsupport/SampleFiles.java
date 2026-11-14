package es.convalidafp.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.ingestion.DatasetContract;
import es.convalidafp.ingestion.DatasetFiles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Un curso del dataset de ejemplo en memoria, para romperlo a propósito en los tests del importador.
 * Por defecto vuelve a calcular los checksums del manifiesto, para que el único fallo sea el que el
 * test provoca.
 */
public final class SampleFiles {

  public static final Path SAMPLE = Path.of(System.getProperty("convalidafp.dataDir", "../data")).resolve("sample");

  private final Map<String, byte[]> files;
  private String directoryName;
  private boolean resign = true;

  private SampleFiles(Map<String, byte[]> files, String directoryName) {
    this.files = files;
    this.directoryName = directoryName;
  }

  public static SampleFiles of(String academicYear) {
    try {
      DatasetFiles original = DatasetFiles.fromDirectory(SAMPLE.resolve(academicYear));
      return new SampleFiles(new LinkedHashMap<>(original.files()), original.directoryName());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Sustituye la primera aparición de un texto, que debe existir. */
  public SampleFiles replace(String file, String search, String replacement) {
    String content = text(file);
    assertThat(content).as("texto a sustituir en " + file).contains(search);
    return set(file, content.replaceFirst(Pattern.quote(search), Matcher.quoteReplacement(replacement)));
  }

  /** Añade una fila al final del fichero. */
  public SampleFiles append(String file, String line) {
    return set(file, text(file) + line + "\n");
  }

  /** Deja el fichero solo con su cabecera. */
  public SampleFiles keepOnlyHeader(String file) {
    return set(file, text(file).lines().limit(1).collect(Collectors.joining()) + "\n");
  }

  public SampleFiles set(String file, String content) {
    return setBytes(file, content.getBytes(StandardCharsets.UTF_8));
  }

  public SampleFiles setBytes(String file, byte[] content) {
    files.put(file, content);
    return this;
  }

  public SampleFiles remove(String file) {
    files.remove(file);
    return this;
  }

  /** No recalcula los checksums del manifiesto: los ficheros cambiados dejarán de coincidir. */
  public SampleFiles withoutResigning() {
    this.resign = false;
    return this;
  }

  public SampleFiles directoryName(String name) {
    this.directoryName = name;
    return this;
  }

  public String text(String file) {
    return new String(files.get(file), StandardCharsets.UTF_8);
  }

  public DatasetFiles build() {
    // Un manifiesto ilegible a propósito se deja tal cual.
    if (resign && files.containsKey(DatasetContract.MANIFEST) && text(DatasetContract.MANIFEST).contains("\"files\"")) {
      files.put(DatasetContract.MANIFEST, signedManifest());
    }
    return new DatasetFiles(files, directoryName);
  }

  /** Empaqueta los ficheros en un ZIP, dentro de la carpeta indicada (o en la raíz si es null). */
  public byte[] toZip(String folder) {
    Map<String, byte[]> content = build().files();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, byte[]> file : content.entrySet()) {
        zip.putNextEntry(new ZipEntry(folder == null ? file.getKey() : folder + "/" + file.getKey()));
        zip.write(file.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  /** El manifiesto original con los checksums recalculados para los ficheros actuales. */
  private byte[] signedManifest() {
    String manifest = text(DatasetContract.MANIFEST);
    String head = manifest.substring(0, manifest.indexOf("\"files\""));
    String entries =
        DatasetContract.HEADERS.keySet().stream()
            .filter(files::containsKey)
            .map(name -> "    { \"name\": \"" + name + "\", \"sha256\": \"" + sha256(files.get(name)) + "\" }")
            .collect(Collectors.joining(",\n"));
    return (head + "\"files\": [\n" + entries + "\n  ]\n}\n").getBytes(StandardCharsets.UTF_8);
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
