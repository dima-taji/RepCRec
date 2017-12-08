public class LockTuple {
    int transaction;
    boolean lockType;

    public static final boolean READ = true;
    public static final boolean WRITE = false;

    public LockTuple(int transaction, boolean lockType) {
        this.transaction = transaction;
        this.lockType = lockType;
    }

    public int getTransaction() {
        return transaction;
    }

    public void setTransaction(int transaction) {
        this.transaction = transaction;
    }

    public boolean isLockType() {
        return lockType;
    }

    public void setLockType(boolean lockType) {
        this.lockType = lockType;
    }

    @Override
    public String toString() {
        return "" + (lockType == READ ? "R(" : "RW(") + transaction + ")";
    }
}
