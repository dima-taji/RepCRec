import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Every data manager is responsible for one data site
 * It handles locks on data items that it houses, and it handles site failure and recovery.
 */
public class DataManager {

    public static final boolean FAILED = false;
    public static final boolean RUNNING = true;

    //The structure containing the data items and their values. The key is the data item index, and the value is the data item value.
    HashMap<Integer, Data> data = new HashMap<Integer, Data>();
    HashMap<Integer, Data> failedData = new HashMap<Integer, Data>();

    //The lock table structure
    //The key is the data item
    //The value is an ArrayList of all the transactions and lock types they hold. ArrayList is used to preserve the order
    HashMap<Integer, ArrayList<LockTuple>> lockTable = new HashMap<Integer, ArrayList<LockTuple>>();

    int siteId;

    boolean status;

    long lastRecovery;

    public DataManager(int i) {
        this.siteId = i;
        status = RUNNING;
        lastRecovery = System.currentTimeMillis();
        lockTable = new HashMap<Integer, ArrayList<LockTuple>>();
    }

    public void addItem(int item, int value) {
        data.put(item, new Data(item, value, this));
        failedData.put(item, new Data(item, value, this));
    }

    public void deleteItem(int item) {
        if(data.containsKey(item)) {
            data.remove(item);
        }
    }

    /**
     * Update an item at commit time
     * If the item is a duplicated item after the site recovery, it "removes" it from the failed data list
     *
     * @param item
     * @param value
     * @param t
     * @return
     */
    public boolean updateItem(int item, int value, Transaction t) {
        if(data.containsKey(item) && lockTable.containsKey(item)) {
            if(lockTable.get(item).get(0).transaction == t.transactionID && lockTable.get(item).get(0).lockType == LockTuple.WRITE) {
                data.get(item).setDataValue(value);
                if(failedData.get(item) == null)
                    failedData.put(item, new Data(item, value, this));
                else
                    failedData.get(item).setDataValue(value);
                lockTable.get(item).remove(0);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * a site failure will erase its lock table, and will cause all the transactions that have interacted with it
     * previously to abort
     *
     * @throws Exception
     */
    public void fail() throws Exception {
        if(status == RUNNING) {
            lockTable = new HashMap<Integer, ArrayList<LockTuple>>();
            status = FAILED;
            System.out.println("Site " + siteId + " failed.");
            Driver.driver.transactionManager.siteFail(this);
        } else {
            System.out.println("Site " + siteId + " is already failed.");
        }
    }

    /**
     * When a site recovers, it makes the data entry that can only be found on it available for reads and writes immediately.
     * Other data items are set to be failed data items until their values are committed by a write from a transaction
     */
    public void recover() {
        if(status == FAILED) {
            status = RUNNING;
            lastRecovery = System.currentTimeMillis();
            System.out.println("Site " + siteId + " recovered.");

            HashMap<Integer, Boolean> replicatedData = new HashMap<Integer, Boolean>();

            for (Map.Entry<Integer, Data> integerIntegerEntry : data.entrySet()) {
                replicatedData.put(integerIntegerEntry.getKey(), false);
            }

            for (DataManager dataSite : Driver.driver.dataSites) {
                if (dataSite != this) {
                    for (Map.Entry<Integer, Data> integerIntegerEntry : dataSite.data.entrySet()) {
                        if (replicatedData.containsKey(integerIntegerEntry.getKey())) {
                            replicatedData.put(integerIntegerEntry.getKey(), true);
                        }
                    }
                }
            }

            for (Map.Entry<Integer, Data> entry : data.entrySet()) {
                if (replicatedData.get(entry.getKey())) {
                    failedData.put(entry.getKey(), null);
                } else {
                    failedData.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            System.out.println("Site " + siteId + " is already running.");
        }
    }

    //print all the values that are on this site
    public void dump() {
        for (Integer dataItem : data.keySet()) {
            System.out.print("x" + dataItem + "." + siteId + " = " + data.get(dataItem).getDataValue());
            if(failedData.get(dataItem) != null) {
                System.out.println("");
            } else {
                System.out.println(" not available for read until a write command is committed");
            }
        }
        System.out.println();
    }

    //print the value of this particular data item on this site
    public boolean dump(int dataItem) {
        if (data.containsKey(dataItem)) {
            System.out.print("x" + dataItem + "." + siteId + " = " + data.get(dataItem).getDataValue());
            if(failedData.get(dataItem) != null) {
                System.out.println("");
            } else {
                System.out.println(" not available for read until a write command is committed");
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        String statusOutput = "Site " + siteId + " is " + (status == FAILED ? "failed" : "running");

        for (Integer integer : lockTable.keySet()) {
            statusOutput += "\n" + "x" + integer;
            for (LockTuple lockTuple : lockTable.get(integer)) {
                statusOutput += "\t" + lockTuple.toString();
            }
        }

        return statusOutput;
    }

}
