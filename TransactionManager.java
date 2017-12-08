import java.util.*;

/**
 * This system should contain one instance of this class.
 * This class represents the transaction manager. It keeps track of transactions, and calls the commands for each
 * transcation.
 */
public class TransactionManager {

    //transaction can be read only, or regular transactions
    public final boolean REGULAR = true;
    public final boolean READ_ONLY = false;

    //An arrayList that tracks transactions
    ArrayList<Transaction> transactions = new ArrayList<Transaction>();

    //These two array lists keep track of transactions waiting on each other, to detect and resolve deadlocks
    ArrayList<ArrayList<Transaction>> waitList = new ArrayList<ArrayList<Transaction>>();
    ArrayList<Command> commands = new ArrayList<Command>();

    //LinkedList works like a Queue with FIFO by using get(0), and remove(Object at index 0)
    //The purpose of this Linked list is to keep track of the commands in the order in which they arrive and execute
    //them when transactions are waiting
    LinkedList<Command> waitingCommands = new LinkedList<Command>();

    /**
     * A regular transaction begins by creating an object for the transaction and adding it to the list of transactions
     *
     * @param transactionID - the ID of the transaction that is starting
     */
    public void begin(int transactionID) {
        transactions.add(new Transaction(transactionID, REGULAR));
        System.out.println("T" + transactionID + " starts as a regular transaction");
    }

    /**
     * When a read only transaction starts it reads all the available data items available at start time and stores
     * them in an array that is specific for the transaction
     *
     * @param transactionID - the ID of the transaction that is starting
     */
    public void beginReadOnly(int transactionID) {
        Transaction t = new Transaction(transactionID, READ_ONLY);
        transactions.add(t);
        System.out.println("T" + transactionID + " starts as a read-only transaction");

        for (DataManager dataSite : Driver.driver.dataSites) {
            //Check that the site is up
            if(dataSite.status == DataManager.RUNNING) {
                for (Map.Entry<Integer, Data> entry : dataSite.data.entrySet()) {
                    //Check that that the site contains the data item, and that it is available for read
                    if(!t.readValues.containsKey(entry.getKey()) && dataSite.failedData.get(entry.getKey()) != null) {
                        HashMap<Integer, DataManager> temp = new HashMap<Integer, DataManager>();
                        temp.put(entry.getValue().getDataValue(), dataSite);
                        t.readValues.put(entry.getValue(), temp);
                    }
                }
            }
        }
    }

    /**
     * This method looks for a site to read the data item from for regular transactions, or read the item from the
     * transactions's read list
     *
     * @param transactionID - the ID of the transaction doing the reading
     * @param dataIndex - the index of the data item being read
     * @return - return 0 if the read failed, and 1 if it succeeded (whether it happened or is still waiting are both a success)
     * @throws Exception
     */
    public int read(int transactionID, int dataIndex) throws Exception {

        //Determine the transaction object that is doing the reading
        //The point of this is to know to which transaction object to store the read value, and to make sure that the
        //transaction is still running, and that it had not failed at a previous point.
        Transaction t = null;
        for (Transaction transaction : transactions) {
            if(transaction.getTransactionID() == transactionID) {
                t = transaction;
                break;
            }
        }

        boolean allSitesDown = true;

        if(t != null) {
            //Only regular transactions need to go through sites and obtain a read lock
            if (t.transactionType == Transaction.REGULAR) {

                //The command object is created
                Command command = new Command("R", transactionID, dataIndex, 0);
                //determine if there is any transaction that this command would conflict with -
                //i.e. a different transaction that has an earlier write lock on the same data item
                for (Command strings : commands) {
                    if (strings.getDataItem() == command.getDataItem() && strings.getTransactionID() != command.getTransactionID() && strings.getCommand().equals("W")) {
                        //Then our transaction needs to wait for this previous transaction iff it is still running
                        //We add the transactions to the wait list
                        ArrayList<Transaction> newWait = new ArrayList<Transaction>();
                        Transaction t1 = null;
                        Transaction t2 = null;
                        for (Transaction transaction : transactions) {
                            if (transaction.getTransactionID() == transactionID) {
                                t1 = transaction;
                            } else if (transaction.getTransactionID() == strings.getTransactionID()) {
                                t2 = transaction;
                            }
                        }

                        if(t1 != null && t2 != null) {
                            newWait.add(t1);
                            newWait.add(t2);
                            //check that we don't already have the edge between T1 and T2 in the wait list
                            if (!waitList.contains(newWait))
                                waitList.add(newWait);

                            if (checkDeadLock()) {
                                //To break out of the method in case of a deadlock
                                return 0;
                            }
                        }
                    }
                }
                commands.add(command);

                boolean read = false;

                //if the transaction has already written the data item, then it reads the same value that it wrote
                if(t.writeValues.containsKey(dataIndex)) {
                    int siteId = 0;
                    int dataValue = 0;
                    for (Map.Entry<Integer, ArrayList<DataManager>> entry : t.writeValues.get(dataIndex).entrySet()) {
                        try {
                            siteId = entry.getValue().get(0).siteId;
                        } catch (IndexOutOfBoundsException e) {
                            System.out.println();
                        }
                        dataValue = entry.getKey();
                    }

                    System.out.println("T" + transactionID + " reads data item x" + dataIndex + "." + siteId + " = " + dataValue);
                    read = true;
                    allSitesDown = false;
                }
                //otherwise, it looks for the data item elsewhere
                else {
                    //iterate over the data sites
                    for (DataManager dataSite : Driver.driver.dataSites) {
                        //check if the sites are up
                        if (dataSite.status == DataManager.RUNNING) {
                            //and if they contain the data item we want
                            if (dataSite.data.containsKey(dataIndex)) {
                                allSitesDown = false;
                                //and that item is available to be read - i.e. it's not a duplicated item on a site that recovered before the item is written and committed again
                                if (dataSite.failedData.get(dataIndex) != null) {
                                    if (dataSite.lockTable.containsKey(dataIndex)) {
                                        //and that it doesn't have any locks on it but the data item exists in the lock table
                                        if (dataSite.lockTable.get(dataIndex).size() == 0) {
                                            //OK to read
                                            read = true;
                                            HashMap<Integer, DataManager> temp = new HashMap<Integer, DataManager>();
                                            temp.put(dataSite.data.get(dataIndex).getDataValue(), dataSite);
                                            t.readValues.put(dataSite.data.get(dataIndex), temp);
                                            dataSite.lockTable.get(dataIndex).add(new LockTuple(transactionID, LockTuple.READ));
                                            System.out.println("T" + transactionID + " reads data item x" + dataIndex + "." + dataSite.siteId + " = " + dataSite.data.get(dataIndex).getDataValue());
                                            break;
                                        }
                                        //or has only read locks, or a write lock that is by the same transaction
                                        else {
                                            boolean allReads = true;
                                            for (LockTuple lockTuple : dataSite.lockTable.get(dataIndex)) {
                                                if (lockTuple.lockType == LockTuple.WRITE && lockTuple.transaction != transactionID) {
                                                    allReads = false;
                                                }
                                            }
                                            if (allReads) {
                                                //OK to read
                                                read = true;
                                                HashMap<Integer, DataManager> temp = new HashMap<Integer, DataManager>();
                                                temp.put(dataSite.data.get(dataIndex).getDataValue(), dataSite);
                                                t.readValues.put(dataSite.data.get(dataIndex), temp);
                                                dataSite.lockTable.get(dataIndex).add(new LockTuple(transactionID, LockTuple.READ));
                                                System.out.println("T" + transactionID + " reads data item x" + dataIndex + "." + dataSite.siteId + " = " + dataSite.data.get(dataIndex).getDataValue());
                                                break;
                                            }
                                        }
                                    }
                                    //and that it doesn't have any locks on it but the data item doesn't exist in the lock table
                                    else {
                                        //OK to read
                                        read = true;
                                        HashMap<Integer, DataManager> temp = new HashMap<Integer, DataManager>();
                                        temp.put(dataSite.data.get(dataIndex).getDataValue(), dataSite);
                                        t.readValues.put(dataSite.data.get(dataIndex), temp);
                                        ArrayList<LockTuple> tempList = new ArrayList<LockTuple>();
                                        tempList.add(new LockTuple(transactionID, LockTuple.READ));
                                        dataSite.lockTable.put(dataIndex, tempList);
                                        System.out.println("T" + transactionID + " reads data item x" + dataIndex + "." + dataSite.siteId + " = " + dataSite.data.get(dataIndex).getDataValue());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                //if the transaction cannot read the data item, the read call is added ot the waiting commands to be attempted once other transactions are committed
                if (!read && !allSitesDown) {
                    //wait
                    waitingCommands.add(command);
                } else if(allSitesDown) {
                    abort(t);
                    transactions.remove(t);
                    System.out.println("\nT" + transactionID + " aborted because all sites are down.");
                }
            } else {
                //Read the value in t.readValues().get(dataIndex)
                //values are already there at start of transaction
                t.printRead(dataIndex);
            }
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * this method attempts to take a write lock on a data item for the transaction to update the data item value
     *
     * @param transactionID - the ID of the transaction that is doing the writing
     * @param dataIndex - the data item that is being updated
     * @param dataValue - the new value of the data item
     * @return - return 0 if the write failed, and 1 if it succeeded (whether it happened or is still waiting are both a success)
     * @throws Exception
     */
    public int write(int transactionID, int dataIndex, int dataValue) throws Exception {
        //The command is created and added only if it hadn't been seen before
        //i.e. if the command was waiting and is running again, then we don't check for dead locks

        Command command = new Command("W", transactionID, dataIndex, dataValue);

        boolean commandSeen = false;

        for (Command command1 : commands) {
            if(command1.dataValue == command.dataValue && command1.dataItem == command.dataItem && command1.transactionID == command.transactionID && command1.command.equals(command.command)) {
                commandSeen = true;
                break;
            }
        }

        if(!commandSeen) {
            for (Command strings : commands) {
                if (strings.getDataItem() == command.getDataItem() && strings.getTransactionID() != command.getTransactionID()) {
                    //Then our transaction needs to wait for this old transaction
                    ArrayList<Transaction> newWait = new ArrayList<Transaction>();
                    Transaction t1 = null;
                    Transaction t2 = null;
                    for (Transaction transaction : transactions) {
                        if (transaction.getTransactionID() == transactionID) {
                            t1 = transaction;
                        } else if (transaction.getTransactionID() == strings.getTransactionID()) {
                            t2 = transaction;
                        }
                    }

                    if(t1 != null && t2 != null) {
                        newWait.add(t1);
                        newWait.add(t2);

                        if (!waitList.contains(newWait))
                            waitList.add(newWait);

                        if (checkDeadLock()) {
                            //To break out of the method in case of a deadlock
                            return 0;
                        }
                    }
                }
            }
            commands.add(command);
        }

        System.out.println("T" + transactionID + " attempts to write the value " + dataValue + " to data item x" + dataIndex);

        ArrayList<DataManager> sitesToWriteTo = new ArrayList<DataManager>();

        //To write, the transaction must take write locks on all copies of the data item that are available on running sites
        boolean canWrite = true;
        for (DataManager dataSite : Driver.driver.dataSites) {
            if(dataSite.status == DataManager.RUNNING) {
                if(dataSite.data.containsKey(dataIndex)) {
                    if(dataSite.lockTable.containsKey(dataIndex)) {
                        //if the lock table has this item, but doesn't have any locks on it, or it has one lock that is for the same transaction it is okay to write to this site
                        if (dataSite.lockTable.get(dataIndex).size() == 0 || (dataSite.lockTable.get(dataIndex).size() == 1 && dataSite.lockTable.get(dataIndex).get(0).transaction == transactionID)) {
                            sitesToWriteTo.add(dataSite);
                        }
                        //if the data item is in the lock table, and there are locks that are for other transaction then the entire write cannot be completed, and the command is added to the waiting queue
                        else {
                            sitesToWriteTo = null;
                            canWrite = false;
                            waitingCommands.add(command);
                            break;
                        }
                    }
                    //if the data lock table doesn't contain the data item at all it's okay to write to it
                    else {
                        sitesToWriteTo.add(dataSite);
                    }
                }
            }
        }



        //if the transaction is cleared to write, the new value of the data item and the sites it needs to be written to
        //are added in a hash in the transaction object to be committed at the time it ends, and locks are added on all
        //the data items that are going to be written on all the sites
        if(canWrite) {
            Transaction t = null;
            for (Transaction transaction : transactions) {
                if (transaction.getTransactionID() == transactionID) {
                    t = transaction;
                    break;
                }
            }

            if (sitesToWriteTo.size() != 0) {
                t.write(dataIndex, dataValue, sitesToWriteTo);
                for (DataManager dataManager : sitesToWriteTo) {
                    if (dataManager.lockTable.containsKey(dataIndex)) {
                        dataManager.lockTable.get(dataIndex).add(new LockTuple(transactionID, LockTuple.WRITE));
                    } else {
                        LockTuple lockTuple = new LockTuple(transactionID, LockTuple.WRITE);
                        ArrayList<LockTuple> tempList = new ArrayList<LockTuple>();
                        tempList.add(lockTuple);
                        dataManager.lockTable.put(dataIndex, tempList);
                    }
                }
            }
            //it didn't find any sites to write to, which we can assume is because it is trying to write to a failed site
            else {
                abort(t);
                transactions.remove(t);
                System.out.println("\nT" + t.transactionID + " aborted because of site failure.");
            }
        }

        return 1;
    }

    /**
     * Check deadlock method
     * @return - true if there is a deadlock, false if there is no deadlock
     * @throws Exception
     *
     * Algorithm for this method is adapted from http://www.geeksforgeeks.org/detect-cycle-in-a-graph/
     */
    public boolean checkDeadLock() throws Exception {
        ArrayList<Transaction> transactionsInWaitList = new ArrayList<Transaction>();
        for (ArrayList<Transaction> list : waitList) {
            if(!transactionsInWaitList.contains(list.get(0)))
                transactionsInWaitList.add(list.get(0));
            if(!transactionsInWaitList.contains(list.get(1)))
                transactionsInWaitList.add(list.get(1));
        }

        HashMap<Transaction, Boolean> explored = new HashMap<Transaction, Boolean>();
        for (Transaction t : transactionsInWaitList) {
            explored.put(t, false);
        }

        HashMap<Transaction, Boolean> recStack = new HashMap<Transaction, Boolean>();
        for (Transaction t : transactionsInWaitList) {
            recStack.put(t, false);
        }

        for (Transaction transaction : transactionsInWaitList) {
            if(!explored.get(transaction)) {
                if(checkCycle(transaction, explored, recStack)) {
                    long latest = 0;
                    Transaction t = null;

                    //go over the transactions and find the transaction with the latest start time
                    for (Map.Entry<Transaction, Boolean> entry : recStack.entrySet()) {
                        if (entry.getValue()) {
                            if(entry.getKey().startTime > latest) {
                                t = entry.getKey();
                                latest = entry.getKey().startTime;
                            }
                        }
                    }

                    //Print the transactions that are causing the deadlock
                    System.out.print("\nTransaction T" + t.getTransactionID() + " aborted for a deadlock with transactions ");
                    ArrayList<Transaction> transactionsInDeadlock = new ArrayList<Transaction>();
                    transactionsInDeadlock.add(t);
                    for (Map.Entry<Transaction, Boolean> entry : recStack.entrySet()) {
                        if (entry.getValue() && entry.getKey().getTransactionID() != t.getTransactionID()) {
                            System.out.print("T" + entry.getKey().getTransactionID() + " ");
                            transactionsInDeadlock.add(entry.getKey());
                        }
                    }
                    System.out.println();

                    boolean started = false;
                    for (ArrayList<Transaction> transactionArrayList : waitList) {
                        if(transactionsInDeadlock.contains(transactionArrayList.get(0)) && transactionsInDeadlock.contains(transactionArrayList.get(1))) {
                            if(started) {
                                System.out.print("\t\tT" + transactionArrayList.get(0).transactionID + "\t-->\tT" + transactionArrayList.get(1).transactionID);
                            } else {
                                System.out.print("T" + transactionArrayList.get(0).transactionID + "\t-->\tT" + transactionArrayList.get(1).transactionID);
                                started = true;
                            }
                        }
                    }
                    System.out.println("\n");

                    //abort the transaction
                    abort(t);
                    //remove it from the list of transactions
                    transactions.remove(t);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Remove the transaction and all its related commands from all lists and queues
     *
     * @param t - the transaction object that is begin removed
     * @throws Exception
     */
    private void abort(Transaction t) throws Exception {
        ArrayList<ArrayList<Transaction>> waitListTransactionsToRemove = new ArrayList<ArrayList<Transaction>>();

        for (ArrayList<Transaction> transactionArrayList : waitList) {

            Transaction firstTransaction = null;
            for (Transaction transaction1 : transactionArrayList) {
                if(transaction1 != null) {
                    firstTransaction = transaction1;
                    break;
                }
            }


            if(firstTransaction.equals(t) || transactionArrayList.get(1).equals(t)) {
                waitListTransactionsToRemove.add(transactionArrayList);
            }
        }

        for (ArrayList<Transaction> transactionArrayList : waitListTransactionsToRemove) {
            waitList.remove(transactionArrayList);
        }

        ArrayList<Command> commandsToRemove = new ArrayList<Command>();

        for (Command command : commands) {
            if(command.getTransactionID() == t.getTransactionID()) {
                commandsToRemove.add(command);
            }
        }

        for (Command command : commandsToRemove) {
            commands.remove(command);
        }

        commandsToRemove = new ArrayList<Command>();

        for (Command command : waitingCommands) {
            if(command.getTransactionID() == t.getTransactionID()) {
                commandsToRemove.add(command);
            }
        }

        for (Command command : commandsToRemove) {
            waitingCommands.remove(command);
        }

        for (DataManager dataSite : Driver.driver.dataSites) {
            for (Integer integer : dataSite.lockTable.keySet()) {
                ArrayList<LockTuple> tuplesToRemove = new ArrayList<LockTuple>();
                for (LockTuple lockTuple : dataSite.lockTable.get(integer)) {
                    if(lockTuple.transaction == t.transactionID) {
                        tuplesToRemove.add(lockTuple);
                    }
                }
                for (LockTuple lockTuple : tuplesToRemove) {
                    dataSite.lockTable.get(integer).remove(lockTuple);
                }
            }
        }

        LinkedList<Command> tempWaitingCommands = new LinkedList<Command>(waitingCommands);
        int index = 0;
        while (waitingCommands.size() != 0 && index < tempWaitingCommands.size()) {
            Command command = tempWaitingCommands.get(index);
            waitingCommands.remove(command);
            index++;
            if (command.getCommand().equals("R")) {
                int tID = command.getTransactionID();
                int dataItem = command.getDataItem();
                this.read(tID, dataItem);
            } else {    //the only other option is W
                int tID = command.getTransactionID();
                int dataItem = command.getDataItem();
                int dataValue = command.getDataValue();
                this.write(tID, dataItem, dataValue);
            }
        }
    }

    /**
     * Part of the check deadlock method
     *
     * @param transaction
     * @param explored
     * @param recStack
     * @return
     *
     * Code adapted from the website http://www.geeksforgeeks.org/detect-cycle-in-a-graph/
     */
    private boolean checkCycle(Transaction transaction, HashMap<Transaction, Boolean> explored, HashMap<Transaction, Boolean> recStack) {
        explored.put(transaction, true);
        recStack.put(transaction, true);


        for (ArrayList<Transaction> transactionArrayList : waitList) {
            Transaction firstTransaction = null;
            for (Transaction transaction1 : transactionArrayList) {
                if(transaction1 != null) {
                    firstTransaction = transaction1;
                    break;
                }
            }

            if(firstTransaction.equals(transaction)) {
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

    /**
     * end method, that commits the transaction if it has executed all its commands, and iterates over the other
     * waiting commands to try to run them
     *
     * @param transactionID
     * @return - 0 if the transaction is not done executing all its commands, 1 if the commit succeeds
     * @throws Exception
     */
    public int end(int transactionID) throws Exception {

        for (Command waitingCommand : waitingCommands) {
            if(waitingCommand.transactionID == transactionID) {
                waitingCommands.add(new Command("end", transactionID, 0, 0));
                return 0;
            }
        }


        Transaction t = null;
        for (Transaction transaction : transactions) {
            if(transaction.getTransactionID() == transactionID) {
                t = transaction;
                break;
            }
        }

        if(t != null) {
            if (t.transactionType == Transaction.REGULAR) {
                if(t.writeValues.size() != 0) {
                    System.out.println("");
                }
                for (Map.Entry<Integer, HashMap<Integer, ArrayList<DataManager>>> integerHashMapEntry : t.writeValues.entrySet()) {
                    // integerHashMapEntry.getKey() is the data index
                    for (Map.Entry<Integer, ArrayList<DataManager>> arrayListEntry : integerHashMapEntry.getValue().entrySet()) {
                        // arrayListEntry.getKey() is the new data value
                        for (DataManager dataManager : arrayListEntry.getValue()) {
                            dataManager.updateItem(integerHashMapEntry.getKey(), arrayListEntry.getKey(), t);
                            System.out.println("T" + transactionID + " writes value " + arrayListEntry.getKey() + " to data item x" + integerHashMapEntry.getKey() + "." + dataManager.siteId);
                        }
                    }
                }

                //release the locks that the transaction had
                for (Map.Entry<Data, HashMap<Integer, DataManager>> hashMapEntry : t.readValues.entrySet()) {
                    for (Map.Entry<Integer, DataManager> entry : hashMapEntry.getValue().entrySet()) {
                        ArrayList<LockTuple> lockTuplesToRemove = new ArrayList<LockTuple>();
                        for (LockTuple lockTuple : entry.getValue().lockTable.get(hashMapEntry.getKey().dataIndex)) {
                            if(lockTuple.transaction == t.transactionID) {
                                lockTuplesToRemove.add(lockTuple);
                            }
                        }
                        for (LockTuple lockTuple : lockTuplesToRemove) {
                            entry.getValue().lockTable.get(hashMapEntry.getKey().dataIndex).remove(lockTuple);
                        }
                    }
                }

                System.out.println("\nT" + transactionID + " commits\n");

                LinkedList<Command> tempWaitingCommands = new LinkedList<Command>(waitingCommands);
                int index = 0;
                while (waitingCommands.size() != 0 && index < tempWaitingCommands.size()) {
                    Command command = tempWaitingCommands.get(index);
                    waitingCommands.remove(command);
                    index++;
                    if (command.getCommand().equals("R")) {
                        int tID = command.getTransactionID();
                        int dataItem = command.getDataItem();
                        this.read(tID, dataItem);
                    } else if(command.getCommand().equals("W")) {
                        int tID = command.getTransactionID();
                        int dataItem = command.getDataItem();
                        int dataValue = command.getDataValue();
                        this.write(tID, dataItem, dataValue);
                    } else {
                        int tID = command.getTransactionID();
                        this.end(tID);
                    }
                }
            } else {
                System.out.println("T" + transactionID + " ended");
            }
        }

        transactions.remove(t);
        return 1;
    }

    /**
     * Site failure will cause all the sites that have read from this site, or wrote to it to abort
     *
     * @param dataManager
     * @throws Exception
     */
    public void siteFail(DataManager dataManager) throws Exception {
        ArrayList<Transaction> transactionsToRemove = new ArrayList<Transaction>();
        for (Transaction transaction : transactions) {
            for (Map.Entry<Data, HashMap<Integer, DataManager>> entry : transaction.readValues.entrySet()) {
                for (Map.Entry<Integer, DataManager> managerEntry : entry.getValue().entrySet()) {
                    if(managerEntry.getValue().equals(dataManager)) {
                        System.out.println("Transaction T" + transaction.getTransactionID() + " aborted because of failure of site " + dataManager.siteId);
                        abort(transaction);
                        transactionsToRemove.add(transaction);
                    }
                }
            }

            for (Map.Entry<Integer, HashMap<Integer, ArrayList<DataManager>>> hashMapEntry : transaction.writeValues.entrySet()) {
                for (Map.Entry<Integer, ArrayList<DataManager>> listEntry : hashMapEntry.getValue().entrySet()) {
                    for (DataManager manager : listEntry.getValue()) {
                        if(dataManager.equals(manager) && !transactionsToRemove.contains(transaction)) {
                            System.out.println("Transaction T" + transaction.getTransactionID() + " aborted because of failure of site " + dataManager.siteId);
                            abort(transaction);
                            transactionsToRemove.add(transaction);
                        }
                    }
                }
            }
        }
        for (Transaction transaction : transactionsToRemove) {
            transactions.remove(transaction);
        }
    }
}
