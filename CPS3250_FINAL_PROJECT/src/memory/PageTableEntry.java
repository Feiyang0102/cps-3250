package memory;

public class PageTableEntry {
    public int physicalAddress;
    public boolean readOnly; // 用于实现写时复制

    public PageTableEntry(int physicalAddress, boolean readOnly) {
        this.physicalAddress = physicalAddress;
        this.readOnly = readOnly;
    }
}
