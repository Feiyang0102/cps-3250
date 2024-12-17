package process;

public class Bitmap {
    public byte[] bits;
    public int btmpBytesLen;

    public Bitmap(int size) {
        bits = new byte[size];
        btmpBytesLen = size;
    }

    // 拷贝构造函数
    public Bitmap(Bitmap other) {
        this.btmpBytesLen = other.btmpBytesLen;
        this.bits = new byte[other.bits.length];
        System.arraycopy(other.bits, 0, this.bits, 0, other.bits.length);
    }
}
