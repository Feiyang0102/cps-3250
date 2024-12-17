package memory;

import utils.Constants;

import java.util.HashMap;
import java.util.Map;

public class PhysicalMemoryManager {
    static int nextFreePhysicalAddress = 0;
    static Map<Integer, PhysicalPage> physicalMemory = new HashMap<>();

    public static int allocatePhysicalPage() {
        int physicalAddress = nextFreePhysicalAddress;
        nextFreePhysicalAddress += Constants.PG_SIZE;
        physicalMemory.put(physicalAddress, new PhysicalPage());
        return physicalAddress;
    }

    public static byte[] readPhysicalMemory(int physicalAddress) {
        PhysicalPage page = physicalMemory.get(physicalAddress);
        return page.data;
    }

    public static void writePhysicalMemory(int physicalAddress, byte[] data) {
        PhysicalPage page = physicalMemory.get(physicalAddress);
        System.arraycopy(data, 0, page.data, 0, data.length);
    }

    public static void increaseReferenceCount(int physicalAddress) {
        PhysicalPage page = physicalMemory.get(physicalAddress);
        page.referenceCount++;
    }

    public static void decreaseReferenceCount(int physicalAddress) {
        PhysicalPage page = physicalMemory.get(physicalAddress);
        page.referenceCount--;
        if (page.referenceCount == 0) {
            physicalMemory.remove(physicalAddress);
        }
    }
}
