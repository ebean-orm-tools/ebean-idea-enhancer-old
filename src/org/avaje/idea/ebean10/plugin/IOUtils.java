package org.avaje.idea.ebean10.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;

/**
 * Utilities for IO.
 */
class IOUtils {

  /**
   * Reads the entire contents of the specified input stream and returns them
   * as a byte array.
   */
  public static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    pump(in, buffer);
    return buffer.toByteArray();
  }

  /**
   * Reads data from the specified input stream and copies it to the specified
   * output stream, until the input stream is at EOF. Both streams are then
   * closed.
   *
   * @throws IOException if the input or output stream is <code>null</code>
   */
  public static void pump(InputStream in, OutputStream out) throws IOException {

    if (in == null) throw new IOException("Input stream is null");
    if (out == null) throw new IOException("Output stream is null");

    try {
      try {
        byte[] buffer = new byte[4096];
        for (; ; ) {
          int bytes = in.read(buffer);
          if (bytes < 0) {
            break;
          }
          out.write(buffer, 0, bytes);
        }
      } finally {
        in.close();
      }
    } finally {
      out.close();
    }
  }
}
