import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Test {

    static ArrayList<ArrayList<Transaction>> waitList = new ArrayList<ArrayList<Transaction>>();

    public static void main(String[] args) throws InterruptedException {

        Transaction t1 = new Transaction(1, true);
        TimeUnit.MILLISECONDS.sleep(1);
        Transaction t2 = new Transaction(2, true);
        TimeUnit.MILLISECONDS.sleep(1);
        Transaction t3 = new Transaction(3, true);
        TimeUnit.MILLISECONDS.sleep(1);
        Transaction t4 = new Transaction(4, true);
        TimeUnit.MILLISECONDS.sleep(1);
        Transaction t5 = new Transaction(5, true);
        TimeUnit.MILLISECONDS.sleep(1);
        Transaction t6 = new Transaction(6, true);
        TimeUnit.MILLISECONDS.sleep(1);

        ArrayList a1 = new ArrayList();
        a1.add(t1);
        a1.add(t2);
        waitList.add(a1);

        ArrayList a2 = new ArrayList();
        a2.add(t2);
        a2.add(t3);
        waitList.add(a2);

        ArrayList a3 = new ArrayList();
        a3.add(t3);
        a3.add(t4);
        waitList.add(a3);

        ArrayList a4 = new ArrayList();
        a4.add(t3);
        a4.add(t1);
        waitList.add(a4);

        ArrayList a5 = new ArrayList();
        a5.add(t1);
        a5.add(t6);
        waitList.add(a5);

        ArrayList a6 = new ArrayList();
        a6.add(t2);
        a6.add(t5);
        waitList.add(a6);

        System.out.println(checkDeadLock());

        long earliest = 0;
        Transaction t = null;

        for (Map.Entry<Transaction, Boolean> entry : recStack.entrySet()) {
            if (entry.getValue()) {
                if(entry.getKey().startTime > earliest) {
                    t = entry.getKey();
                    earliest = entry.getKey().startTime;
                }
            }
        }

        System.out.println();

    }

    static HashMap<Transaction, Boolean> explored = new HashMap<Transaction, Boolean>();
    static HashMap<Transaction, Boolean> recStack = new HashMap<Transaction, Boolean>();

    public static boolean checkDeadLock() {
        ArrayList<Transaction> transactionsInWaitList = new ArrayList<Transaction>();
        for (ArrayList<Transaction> list : waitList) {
            if(!transactionsInWaitList.contains(list.get(0)))
                transactionsInWaitList.add(list.get(0));
            if(!transactionsInWaitList.contains(list.get(1)))
                transactionsInWaitList.add(list.get(1));
        }


        for (Transaction t : transactionsInWaitList) {
            explored.put(t, false);
        }


        for (Transaction t : transactionsInWaitList) {
            recStack.put(t, false);
        }

        for (Transaction transaction : transactionsInWaitList) {
            if(!explored.get(transaction)) {
                if(checkCycle(transaction, explored, recStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean checkCycle(Transaction transaction, HashMap<Transaction, Boolean> explored, HashMap<Transaction, Boolean> recStack) {
        explored.put(transaction, true);
        recStack.put(transaction, true);

        for (ArrayList<Transaction> transactionArrayList : waitList) {
            if(transactionArrayList.get(0).equals(transaction)) {
                Transaction neighbour = transactionArrayList.get(1);
                if(!explored.get(neighbour)) {
                    if(checkCycle(neighbour, explored, recStack)) {
                        return true;
                    }
                } else if(recStack.get(neighbour)) {
                    return true;
                }
            }
        }

        recStack.put(transaction, false);
        return false;
    }
}
