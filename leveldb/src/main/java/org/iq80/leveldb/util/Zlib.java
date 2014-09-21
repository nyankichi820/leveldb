package org.iq80.leveldb.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import org.iq80.leveldb.util.Snappy.SPI;

/**
 * Some glue code that uses the java.util.zip classes to implement ZLIB
 * compression for leveldb.
 */
public class Zlib {

  /**
   * From:
   * http://stackoverflow.com/questions/4332264/wrapping-a-bytebuffer-with-
   * an-inputstream
   */
  public static class ByteBufferBackedInputStream extends InputStream {

    ByteBuffer buf;

    public ByteBufferBackedInputStream(ByteBuffer buf) {
      this.buf = buf;
    }

    public int read() throws IOException {
      if (!buf.hasRemaining()) {
        return -1;
      }
      return buf.get() & 0xFF;
    }

    public int read(byte[] bytes, int off, int len) throws IOException {
      if (!buf.hasRemaining()) {
        return -1;
      }

      len = Math.min(len, buf.remaining());
      buf.get(bytes, off, len);
      return len;
    }
  }

  public static class ByteBufferBackedOutputStream extends OutputStream {
    ByteBuffer buf;

    public ByteBufferBackedOutputStream(ByteBuffer buf) {
      this.buf = buf;
    }

    public void write(int b) throws IOException {
      buf.put((byte) b);
    }

    public void write(byte[] bytes, int off, int len) throws IOException {
      buf.put(bytes, off, len);
    }

  }

  /**
   * Use the same SPI interface as Snappy, for the case if leveldb ever gets
   * a compression plug-in type.
   */
  private static class ZLibSPI implements SPI {

    private int copy(InputStream in, OutputStream out) throws IOException {
      byte[] buffer = new byte[1024];
      int read;
      int count = 0;
      while (-1 != (read = in.read(buffer))) {
        out.write(buffer, 0, read);
        count += read;
      }
      return count;
    }

    @Override
    public int uncompress(ByteBuffer compressed, ByteBuffer uncompressed)
        throws IOException {
      int count = copy(new InflaterInputStream(new ByteBufferBackedInputStream(
          compressed)), new ByteBufferBackedOutputStream(uncompressed));
      // Prepare the output buffer for reading.
      uncompressed.flip();
      return count;
    }

    @Override
    public int uncompress(byte[] input, int inputOffset, int length,
        byte[] output, int outputOffset) throws IOException {
      return copy(
          new InflaterInputStream(new ByteArrayInputStream(input, inputOffset,
              length)),
          new ByteBufferBackedOutputStream(ByteBuffer.wrap(output,
              outputOffset, output.length - outputOffset)));
    }

    @Override
    public int compress(byte[] input, int inputOffset, int length,
        byte[] output, int outputOffset) throws IOException {
      // TODO: parameters of Deflater to match MCPE expectations.
      return copy(
          new DeflaterInputStream(new ByteArrayInputStream(input, inputOffset,
              length)),
          new ByteBufferBackedOutputStream(ByteBuffer.wrap(output,
              outputOffset, output.length - outputOffset)));
    }

    @Override
    public byte[] compress(String text) throws IOException {
      byte[] input = text.getBytes();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      // TODO: parameters of Deflater to match MCPE expectations.
      copy(new DeflaterInputStream(new ByteArrayInputStream(input, 0,
          input.length)), baos);
      return baos.toByteArray();
    }

    @Override
    public int maxCompressedLength(int length) {
      // unused
      return 0;
    }
  }

  static final private SPI ZLIB;
  static {
    ZLIB = new ZLibSPI();
  }

  public static boolean available() {
    return ZLIB != null;
  }

  public static void uncompress(ByteBuffer compressed, ByteBuffer uncompressed)
      throws IOException {
    ZLIB.uncompress(compressed, uncompressed);
  }

  public static void uncompress(byte[] input, int inputOffset, int length,
      byte[] output, int outputOffset) throws IOException {
    ZLIB.uncompress(input, inputOffset, length, output, outputOffset);
  }

  public static int compress(byte[] input, int inputOffset, int length,
      byte[] output, int outputOffset) throws IOException {
    return ZLIB.compress(input, inputOffset, length, output, outputOffset);
  }

  public static byte[] compress(String text) throws IOException {
    return ZLIB.compress(text);
  }
}
