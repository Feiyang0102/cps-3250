package filesystem;

public class Inode {
    public int openCount;

    public Inode() {
        this.openCount = 1;
    }
}
