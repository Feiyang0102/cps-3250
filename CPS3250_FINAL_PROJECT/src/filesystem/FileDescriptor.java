package filesystem;

public class FileDescriptor {
    public Inode inode;
    public int position; // 文件指针位置
    public int flags;    // 打开文件的标志，例如只读、读写等

    public FileDescriptor(Inode inode, int position, int flags) {
        this.inode = inode;
        this.position = position;
        this.flags = flags;
    }
}
