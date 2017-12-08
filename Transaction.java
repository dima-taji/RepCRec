import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Transaction {
    //A transaction can be a read only transaction, or a regular transaction
    public static final boolean REGULAR = true;
    public static final boolean READ_ONLY = false;

    int transactionID;
    boolean transactionType;
    long startTime;
    boolean status;

    public final boolean WAITING = false;
    public final boolean RUNNING = true;

    HashMap<Data, HashMap<Integer, DataManager>> readValues = new HashMap<Data, HashMap<Integer, DataManager>>();
    HashMap<Integer, HashMap<Integer, ArrayList<DataManager>>> writeValues = new HashMap<Integer, HashMap<Integer, ArrayList<DataManager>>>();


    public Transaction(int transactionID, boolean transactionType) {
        this.transactionID = transactionID;
        this.transactionType = transactionType;
        startTime = System.currentTimeMillis();
        status = RUNNING;
    }

    public int getTransactionID() {
        return transactionID;
    }

    public boolean isTransactionType() {
        return transactionType;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public void waiting() {
        this.status = WAITING;
    }

    @Override
    public String toString() {
        return "T" + transactionID + "\t" + (transactionType == READ_ONLY ? "Read Only" : "Regular") + "\t" + "Started at " + startTime + "\tStatus: " + (status == WAITING ? "Waiting" : "Running");
    }

    /**
     * add a read value to the readValues hash
     * @param d
     * @param value
     * @param site
     */
    public void read(Data d, int value, DataManager site) {
        HashMap<Integer, DataManager> temp = new HashMap<Integer, DataManager>();
        temp.put(value, site);
        readValues.put(d, temp);
    }

    /**
     * Add a write value to the writeValues hash
     * @param dataIndex
     * @param value
     * @param sites
     */
    public void write(int dataIndex, int value, ArrayList<DataManager> sites) {
        HashMap<Integer, ArrayList<DataManager>> temp = new HashMap<Integer, ArrayList<DataManager>>();
        temp.put(value, sites);
        writeValues.put(dataIndex, temp);
    }

    /**
     * When a read is done by a read only transaction it attempts to read the value that is stored in its readValues
     * hash, if it has it
     *
     * @param dataIndex
     * @return
     */
    public int printRead(int dataIndex) {
        DataManager m = null;
        int dataValue = 0;

        for (Map.Entry<Data, HashMap<Integer, DataManager>> dataHashMapEntry : readValues.entrySet()) {
            if(dataHashMapEntry.getKey().dataIndex == dataIndex) {
                for (Map.Entry<Integer, DataManager> integerDataManagerEntry : dataHashMapEntry.getValue().entrySet()) {
                    m = integerDataManagerEntry.getValue();
                    dataValue = integerDataManagerEntry.getKey();
                }
                System.out.println("T" + transactionID + " reads data item x" + dataIndex + "." + m.siteId + " = " + dataValue);
                return 1;
            }
        }

        System.out.println("T" + transactionID + " cannot read item x" + dataIndex);
        return 0;
    }
}
