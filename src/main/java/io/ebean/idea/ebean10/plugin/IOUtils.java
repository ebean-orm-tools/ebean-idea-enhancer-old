package io.ebean.idea.ebean10.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Utilities for IO.
 */
class IOUtils {

  /**
   * Reads the entire contents of the specified input stream and returns them
   * as a byte array.
   */
  static byte[] read(InputStream in) throws IOException {
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
  private static void pump(InputStream in, OutputStream out) throws IOException {

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

  static URL byteArrayToURL(final byte[] bytes) {
    try {
      return new URL(null, "foobar://foo/bar", new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(final URL u) throws IOException {
          final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

          return new URLConnection(null) {

            @Override
            public void connect() throws IOException {
            }

            @Override
            public InputStream getInputStream() throws IOException {
              return bais;
            }
          };
        }
      });
    } catch (MalformedURLException e) {
      throw new UncheckedIOException(e);
    }
  }
}
