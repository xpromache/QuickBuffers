package us.hebi.robobuf;

/**
 * @author Florian Enner
 * @since 12 Aug 2019
 */
public class ByteStore {

    public ByteStore(String world) {
    }

    public ByteStore() {
    }

    public int length() {
        return array.length;
    }

    public byte[] array() {
        return array;
    }

    public int remainingCapacity() {
        return 0;
    }

    public void clear() {
        length = 0;
    }

    public void copyFrom(ByteStore other) {

    }

    public void setBytes(byte[] array) {
        this.array = array;
    }

    public void setBytes(byte[] buffer, int offset, int length) {

    }

    public void setLength(int length) {

    }

    byte[] array;
    int length;

}
