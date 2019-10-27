package us.hebi.robobuf;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Abstract interface implemented by Protocol Message objects.
 *
 * API partially copied from Google's MessageNano
 *
 * @author Florian Enner
 */
public abstract class ProtoMessage<MessageType extends ProtoMessage> {

    private static final long serialVersionUID = 0L;
    protected volatile int cachedSize = -1;

    // Keep the first two bitfields in the parent class so that they
    // are in the same cache line as the object header
    protected int bitField0_;
    protected int bitField1_;

    /**
     * Get the number of bytes required to encode this message.
     * Returns the cached size or calls getSerializedSize which
     * sets the cached size. This is used internally when serializing
     * so the size is only computed once. If a member is modified
     * then this could be stale call getSerializedSize if in doubt.
     */
    public int getCachedSize() {
        if (cachedSize < 0) {
            // getSerializedSize sets cachedSize
            getSerializedSize();
        }
        return cachedSize;
    }

    /**
     * Computes the number of bytes required to encode this message.
     * The size is cached and the cached result can be retrieved
     * using getCachedSize().
     */
    public int getSerializedSize() {
        int size = computeSerializedSize();
        cachedSize = size;
        return size;
    }

    /**
     * Copies all fields and data from another message of the same
     * type into this message.
     *
     * @param other
     * @return this
     */
    public abstract MessageType copyFrom(MessageType other);

    /**
     * Sets all fields and data to their default values. Does not
     * get rid of memory that was allocated.
     *
     * @return this
     */
    public abstract MessageType clear();

    /**
     * Clears all has state so that the message would serialize empty,
     * but does not set field default values and does not get rid of
     * memory that was allocated for repeated types.
     *
     * Use this if you use this message for serialization purposes or
     * if you do not require default values for unset fields.
     *
     * @return this
     */
    public MessageType clearQuick() {
        return clear();
    }

    /**
     * Returns true if all required fields in the message and all embedded
     * messages are set, false otherwise.
     */
    public boolean isInitialized() {
        return true;
    }

    /**
     * Prints all fields in this message to the printer
     *
     * @param printer output
     */
    public void print(ProtoPrinter printer) {
    }

    /**
     * Computes the number of bytes required to encode this message. This does
     * not update the cached size.
     */
    protected abstract int computeSerializedSize();

    /**
     * Serializes the message and writes it to {@code output}.
     *
     * @param output the output to receive the serialized form.
     * @throws IOException if an error occurred writing to {@code output}.
     */
    public abstract void writeTo(ProtoSink output) throws IOException;

    /**
     * Parse {@code input} as a message of this type and merge it with the
     * message being built.
     */
    public abstract ProtoMessage mergeFrom(ProtoSource input) throws IOException;

    /**
     * Serialize to a byte array.
     *
     * @return byte array with the serialized data.
     */
    public final byte[] toByteArray() {
        return ProtoMessage.toByteArray(this);
    }

    /**
     * Serialize to a byte array.
     *
     * @return byte array with the serialized data.
     */
    public static final byte[] toByteArray(ProtoMessage msg) {
        final byte[] result = new byte[msg.getSerializedSize()];
        toByteArray(msg, result, 0, result.length);
        return result;
    }

    /**
     * Serialize to a byte array starting at offset through length. The
     * method getSerializedSize must have been called prior to calling
     * this method so the proper length is know.  If an attempt to
     * write more than length bytes OutOfSpaceException will be thrown
     * and if length bytes are not written then IllegalStateException
     * is thrown.
     */
    public static final void toByteArray(ProtoMessage msg, byte[] data, int offset, int length) {
        try {
            final ProtoSink output = ProtoSink.newSafeInstance().wrap(data, offset, length);
            msg.writeTo(output);
            output.checkNoSpaceLeft();
        } catch (IOException e) {
            throw new RuntimeException("Serializing to a byte array threw an IOException "
                    + "(should never happen).", e);
        }
    }

    /**
     * Parse {@code data} as a message of this type and merge it with the
     * message being built.
     */
    public static final <T extends ProtoMessage> T mergeFrom(T msg, final byte[] data)
            throws InvalidProtocolBufferException {
        return mergeFrom(msg, data, 0, data.length);
    }

    /**
     * Parse {@code data} as a message of this type and merge it with the
     * message being built.
     */
    public static final <T extends ProtoMessage> T mergeFrom(T msg, final byte[] data,
                                                             final int off, final int len) throws InvalidProtocolBufferException {
        try {
            final ProtoSource input = ProtoSource.newInstance(data, off, len);
            msg.mergeFrom(input);
            input.checkLastTagWas(0);
            return msg;
        } catch (InvalidProtocolBufferException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Reading from a byte array threw an IOException (should "
                    + "never happen).");
        }
    }

    /**
     * Returns a string that contains a human readable representation of the contents. The output
     * may not be compatible with any existing readers.
     */
    @Override
    public String toString() {
        return JsonPrinter.newInstance().print(this).toString();
    }

    /** Provides support for cloning if the method is generated for child classes */
    @Override
    public ProtoMessage clone() throws CloneNotSupportedException {
        return (ProtoMessage) super.clone();
    }

    /**
     * Helper to determine the default value for 'Bytes' fields. The Protobuf
     * generator encodes raw bytes as strings with ISO-8859-1 encoding.
     */
    protected static byte[] bytesDefaultValue(String bytes) {
        return bytes.getBytes(Charsets.ISO_8859_1);
    }

    private static class Charsets {
        private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    }

}
