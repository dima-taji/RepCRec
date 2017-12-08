import java.util.HashMap;

public class Data {
    int dataIndex;
    int dataValue;

    DataManager site;

    HashMap<Transaction, Integer> uncommittedValues = new HashMap<Transaction, Integer>();

    long lastCommitTime;
    HashMap<Transaction, Long> transactionAccessTime = new HashMap<Transaction, Long>();



    public Data(int dataIndex, int dataValue, DataManager site) {
        this.dataIndex = dataIndex;
        this.dataValue = dataValue;
        this.site = site;
        lastCommitTime = System.currentTimeMillis();
    }

    public int getDataValue() {
        return dataValue;
    }

    public void setDataValue(int dataValue) {
        this.dataValue = dataValue;
    }

    public long getLastCommitTime() {
        return lastCommitTime;
    }

    public void setLastCommitTime(long lastCommitTime) {
        this.lastCommitTime = lastCommitTime;
    }

    public HashMap<Transaction, Long> getTransactionAccessTime() {
        return transactionAccessTime;
    }

    public void setTransactionAccessTime(HashMap<Transaction, Long> transactionAccessTime) {
        this.transactionAccessTime = transactionAccessTime;
    }

    public void commit(Transaction t) {
        lastCommitTime = System.currentTimeMillis();
        if(uncommittedValues.containsKey(t)) {
            dataValue = uncommittedValues.get(t);
        }
    }

    public void addAccess(Transaction t) {
        transactionAccessTime.put(t, System.currentTimeMillis());
    }

    public void addValue(Transaction t, int value) {
        uncommittedValues.put(t, value);
    }
}
