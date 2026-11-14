package es.convalidafp.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Los ficheros de un dataset tal como llegan, en bruto: el manifiesto y los CSV, por nombre.
 *
 * @param directoryName nombre de la carpeta de la que se leyeron; null si llegaron en un ZIP. Si
 *     existe, el curso del manifiesto debe coincidir con él
 */
public record DatasetFiles(Map<String, byte[]> files, String directoryName) {

  /** Límite por fichero y para el total, para no aceptar ZIP desproporcionados. */
  static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

  private static final Set<String> KNOWN_FILES;

  static {
    Set<String> known = new java.util.LinkedHashSet<>(DatasetContract.HEADERS.keySet());
    known.add(DatasetContract.MANIFEST);
    KNOWN_FILES = Collections.unmodifiableSet(known);
  }

  public DatasetFiles {
    files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
  }

  /** Lee los ficheros del contrato de una carpeta de curso, por ejemplo data/sample/2026-27. */
  public static DatasetFiles fromDirectory(Path directory) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (Stream<Path> paths = Files.list(directory)) {
      for (Path path : paths.sorted().toList()) {
        String name = path.getFileName().toString();
        if (Files.isRegularFile(path) && KNOWN_FILES.contains(name)) {
          files.put(name, Files.readAllBytes(path));
        }
      }
    }
    return new DatasetFiles(files, directory.getFileName().toString());
  }

  /**
   * Lee los ficheros del contrato de un ZIP. Los ficheros pueden estar en la raíz o dentro de una
   * carpeta; cualquier otro fichero se ignora.
   *
   * @throws IllegalArgumentException si un fichero aparece dos veces o supera el tamaño máximo
   */
  public static DatasetFiles fromZip(InputStream zip) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (ZipInputStream in = new ZipInputStream(zip)) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        // Solo se usa el nombre del fichero: la ruta del ZIP nunca se escribe en disco.
        String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
        if (entry.isDirectory() || !KNOWN_FILES.contains(name)) {
          continue;
        }
        if (files.containsKey(name)) {
          throw new IllegalArgumentException("El ZIP contiene dos veces el fichero " + name);
        }
        files.put(name, readLimited(in, name));
      }
    }
    return new DatasetFiles(files, null);
  }

  private static byte[] readLimited(InputStream in, String name) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      if (out.size() > MAX_FILE_BYTES) {
        throw new IllegalArgumentException("El fichero " + name + " supera el tamaño máximo permitido");
      }
    }
    return out.toByteArray();
  }

  public byte[] get(String name) {
    return files.get(name);
  }
}
