package memory;

import java.util.HashMap;
import java.util.Map;

public class PageDirectory {
    // 虚拟地址到页表项的映射
    public Map<Integer, PageTableEntry> pageTableEntries;

    public PageDirectory() {
        this.pageTableEntries = new HashMap<>();
    }

    // 添加页表项
    public void addPageTableEntry(int virtualAddress, PageTableEntry entry) {
        pageTableEntries.put(virtualAddress, entry);
    }

    // 获取页表项
    public PageTableEntry getPageTableEntry(int virtualAddress) {
        return pageTableEntries.get(virtualAddress);
    }
}
