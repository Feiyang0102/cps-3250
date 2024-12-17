package memory;

import utils.Constants;

public class PhysicalPage {
    public byte[] data;
    public int referenceCount;

    public PhysicalPage() {
        this.data = new byte[Constants.PG_SIZE];
        this.referenceCount = 1;
    }
}
