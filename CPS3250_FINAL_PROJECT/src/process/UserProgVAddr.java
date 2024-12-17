package process;

public class UserProgVAddr {
    public Bitmap vaddrBitmap;
    public int vaddrStart;

    public UserProgVAddr(int vaddrStart, int bitmapSize) {
        this.vaddrStart = vaddrStart;
        this.vaddrBitmap = new Bitmap(bitmapSize);
    }

    // 拷贝构造函数
    public UserProgVAddr(UserProgVAddr other) {
        this.vaddrStart = other.vaddrStart;
        this.vaddrBitmap = new Bitmap(other.vaddrBitmap);
    }
}
