// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ork.sevenstates.apng.AbstractAPNGWriter;
import ork.sevenstates.apng.Consts;

/** Writes full-frame APNG animations with an alpha channel. */
public final class AlphaApngWriter extends AbstractAPNGWriter {
  private final FileChannel output;

  private Dimension imageSize;
  private long animationControlOffset;
  private int imageCount;
  private boolean writerClosed;

  @SuppressWarnings("resource") // Closing the channel also closes the RandomAccessFile.
  public AlphaApngWriter(File file) throws FileNotFoundException {
    super(0);
    output = new RandomAccessFile(file, "rw").getChannel();
  }

  /** Writes a complete animation frame with the specified APNG delay fraction. */
  public void writeImage(BufferedImage image, int delayNumerator, int delayDenominator) throws IOException {
    ensureOpen();
    if (image == null) {
      throw new IOException("Image is null");
    }

    final BufferedImage frame = ColorConvert.toBufferedImage(image, true, false);
    final Dimension frameSize = new Dimension(frame.getWidth(), frame.getHeight());
    if (imageSize == null) {
      imageSize = frameSize;
      output.write(ByteBuffer.wrap(Consts.getPNGSIGArr()));
      output.write(makeIHDRChunk(imageSize, (byte)4, (byte)8));
      animationControlOffset = output.position();
      output.write(ByteBuffer.wrap(Consts.getacTLArr()));
    } else if (!imageSize.equals(frameSize)) {
      throw new IOException("APNG frame dimensions do not match");
    }

    final Rectangle bounds = new Rectangle(imageSize);
    final ByteBuffer pixels = getPixelBytes(frame, imageSize);
    // Full frames use source blending so transparent pixels clear data from the preceding frame.
    output.write(makeFCTL(bounds, delayNumerator, delayDenominator, false));
    output.write(makeDAT((imageCount == 0) ? Consts.IDAT_SIG : Consts.fdAT_SIG, pixels));
    imageCount++;
  }

  @Override
  public void writeImage(Image image, Dimension size, int delayNumerator, int delayDenominator) throws IOException {
    writeImage(ColorConvert.toBufferedImage(image, true, false), delayNumerator, delayDenominator);
  }

  @Override
  public void close() throws IOException {
    if (writerClosed) {
      return;
    }
    writerClosed = true;
    try {
      if (imageCount == 0) {
        output.truncate(0);
        throw new IOException("APNG contains no frames");
      }
      output.write(ByteBuffer.wrap(Consts.getIENDArr()));
      final long endOffset = output.position();
      output.position(animationControlOffset);
      output.write(make_acTLChunk(imageCount, 0));
      output.truncate(endOffset);
    } finally {
      super.close();
      output.close();
    }
  }
}
