package top.likoslupus.ferrum.codec;

import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that buffers writes and emits the whole lz4-java block stream through the native
 * kernel on close.
 *
 * <p>Region payloads are always written as one buffered chunk, so a single native call is the
 * right
 * granularity. If the native kernel is unavailable or fails, the buffered bytes are piped through
 * the vanilla {@link LZ4BlockOutputStream}, which guarantees byte-identical vanilla behavior.
 */
final class NativeLz4OutputStream extends OutputStream {

    private final OutputStream out;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean closed;

    NativeLz4OutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int value) {
        buffer.write(value);
    }

    @Override
    public void write(
            byte[] bytes,
            int offset,
            int length
    ) {
        buffer.write(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        var raw = buffer.toByteArray();
        var encoded = NativeLz4.encode(raw);
        if (encoded != null) {
            out.write(encoded);
            out.close();
            return;
        }

        try (var vanilla = new LZ4BlockOutputStream(out)) {
            vanilla.write(raw);
        }
    }

}
