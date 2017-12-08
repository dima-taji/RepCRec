import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.concurrent.TimeUnit;

/**
 * The driver contains the main method of the system.
 * It runs with one argument: the file containing the different commands
 */
public class Driver {

    public static Driver driver = new Driver();

    //an arrayList contianing all the data managers, each representing a different site
    public ArrayList<DataManager> dataSites = new ArrayList<DataManager>();
    //the transaction manager that will handle the transactions and communicate with the data managers
    public TransactionManager transactionManager = new TransactionManager();

    /**
     * main method
     * @param args - args[0] contains the path to the file
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        //initialize 10 sites with 20 data values
        driver.initialize(10, 20);
        //read the file, interpret the lines, and call the relevant functions
        driver.readFile(args[0]);
    }

    /**
     * The initialization method that creates the data sites, and initializes the data values in the data sites
     *
     * this method is designed with the data specifications of our project. For use with other projects, the data
     * initialization part should be edited to fit the project's specs
     *
     * @param sites - an integer denoting the number of different sites to create
     * @param data - an integer denoting the number of data items to create
     */
    public void initialize(int sites, int data) {
        //create the data sites
        for (int i = 0; i < sites; i++) {
            this.dataSites.add(new DataManager((i + 1)));
        }

        //initialize the data in the data sites
        for (int i = 0; i < data; i++) {
            if (i % 2 == 1) {
                for (DataManager dataSite : dataSites) {
                    dataSite.addItem((i + 1), 10 * (i + 1));
                }
            } else {
                int dataSite = (i + 1) % 10;
                dataSites.get(dataSite).addItem((i + 1), 10 * (i + 1));
            }
        }
    }

    /**
     * readFile method
     * @param filePath - the path to the file containing the commands
     * @throws Exception
     */
    private void readFile(String filePath) throws Exception {

        LineIterator it = FileUtils.lineIterator(new File(filePath));
        int lineCounter = 0;

        while(it.hasNext()) {

            String line = it.nextLine();

            //empty lines are ignored
            if (!line.trim().isEmpty()) {
                //empty spaces in the lines are deleted
                String cleanedLine = line.replaceAll(" ", "").trim();
                lineCounter++;

                //lines starting with // are comment lines and are ignored
                if (cleanedLine.startsWith("//"))
                    continue;

                //begin commands
                else if (cleanedLine.startsWith("begin")) {
                    //begin read only transactions
                    if (cleanedLine.startsWith("beginRO(T")) {
                        int transactionID = Integer.parseInt(cleanedLine.replaceAll("beginRO\\(T", "").replaceAll("\\)", ""));
                        transactionManager.beginReadOnly(transactionID);
                    }
                    //begin regular transactions
                    else if (cleanedLine.startsWith("begin(T")) {
                        int transactionID = Integer.parseInt(cleanedLine.replaceAll("begin\\(T", "").replaceAll("\\)", ""));
                        transactionManager.begin(transactionID);
                    }
                    //throw an Exception if the command begins with 'begin' but doesn't follow the expected structure
                    else {
                        throw new Exception("Syntax error at line " + lineCounter + ". Expected beginRO(Ti) or begin(Ti)\n\t" + line);
                    }
                }
                //read commands
                else if (cleanedLine.startsWith("R(")) {
                    String[] segments = cleanedLine.replaceAll("R\\(", "").replaceAll("\\)", "").split(",");
                    //read commands expect two arguments, the transaction, and the data item to read
                    if (segments.length != 2) {
                        throw new Exception("Syntax error at line " + lineCounter + ". Expected R(Ti, xj).\n\t" + line + "\n");
                    } else {
                        if (segments[0].startsWith("T") && segments[0].length() > 1 && segments[1].startsWith("x") && segments[1].length() > 1) {
                            int transactionID = Integer.parseInt(segments[0].replaceAll("T", ""));
                            int dataIndex = Integer.parseInt(segments[1].replaceAll("x", ""));
                            //the purpose of this loop is to make sure that the transaction calling the command hasn't aborted at an earlier point
                            try {
                                for (Transaction transaction : transactionManager.transactions) {
                                    if (transaction.transactionID == transactionID) {
                                        transactionManager.read(transactionID, dataIndex);
                                    }
                                }
                            } catch (ConcurrentModificationException e) {
                                //We're ignoring this exception because it just means that while we are iterating over the transactions list, one of the transactions aborted
                            }
                        } else {
                            throw new Exception("Syntax error at line " + lineCounter + ". Expected R(Ti, xj).\n\t" + line + "\n");
                        }
                    }
                }
                //write commands
                else if (cleanedLine.startsWith("W(")) {
                    String[] segments = cleanedLine.replaceAll("W\\(", "").replaceAll("\\)", "").split(",");
                    //write commands expect 3 arguments, the transaction, the data item, and the data value to write to the data item
                    if (segments.length != 3) {
                        throw new Exception("Syntax error at line " + lineCounter + ". Expected W(Ti, xj, v).\n\t" + line + "\n");
                    } else {
                        if (segments[0].startsWith("T") && segments[0].length() > 1 && segments[1].startsWith("x") && segments[1].length() > 1) {
                            int transactionID = Integer.parseInt(segments[0].replaceAll("T", ""));
                            int dataIndex = Integer.parseInt(segments[1].replaceAll("x", ""));
                            int dataValue = Integer.parseInt(segments[2]);
                            //the purpose of this loop is to make sure that the transaction calling the command hasn't aborted at an earlier point
                            try {
                                for (Transaction transaction : transactionManager.transactions) {
                                    if (transaction.transactionID == transactionID) {
                                        transactionManager.write(transactionID, dataIndex, dataValue);
                                    }
                                }
                            } catch (ConcurrentModificationException e) {
                                //We're ignoring this exception because it just means that while we are iterating over the transactions list, one of the transactions aborted
                            }
                        } else {
                            throw new Exception("Syntax error at line " + lineCounter + ". Expected W(Ti, xj, v).\n\t" + line + "\n");
                        }
                    }
                }
                //end commands
                else if (cleanedLine.startsWith("end(")) {
                    if (cleanedLine.startsWith("end(T")) {
                        int transactionID = Integer.parseInt(cleanedLine.replaceAll("end\\(T", "").replaceAll("\\)", ""));
                        //the purpose of this loop is to make sure that the transaction calling the command hasn't aborted at an earlier point
                        try {
                            for (Transaction transaction : transactionManager.transactions) {
                                if (transaction.transactionID == transactionID) {
                                    transactionManager.end(transactionID);
                                }
                            }
                        } catch (ConcurrentModificationException e) {
                            //We're ignoring this exception because it just means that while we are iterating over the transactions list, one of the transactions aborted
                        }
                    } else {
                        throw new Exception("Syntax error at line " + lineCounter + ". Expected end(Ti)\n\t" + line);
                    }
                }
                //dump commands
                else if (cleanedLine.startsWith("dump")) {
                    //dump() calls the dumpAll method
                    if (cleanedLine.equals("dump()")) {
                        dumpAll();
                    }
                    //dump(xi) calls the dump method for one data item
                    else if (cleanedLine.startsWith("dump(x")) {
                        int dataIndex = Integer.parseInt(cleanedLine.replaceAll("dump\\(x", "").replaceAll("\\)", ""));
                        dumpDataItem(dataIndex);
                    }
                    //dump(i) calls the dump method for a site
                    else {
                        int siteID = Integer.parseInt(cleanedLine.replaceAll("dump\\(", "").replaceAll("\\)", ""));
                        if (siteID > 0 && siteID <= dataSites.size()) {
                            dumpSite(siteID);
                        } else {
                            throw new Exception("Syntax error at line " + lineCounter + ". Site ID is out of scope\n\t" + line);
                        }
                    }
                }
                //fail commands
                else if (cleanedLine.startsWith("fail(")) {
                    int siteID = Integer.parseInt(cleanedLine.replaceAll("fail\\(", "").replaceAll("\\)", ""));
                    if (siteID > 0 && siteID <= dataSites.size()) {
                        dataSites.get(siteID - 1).fail();
                    } else {
                        throw new Exception("Syntax error at line " + lineCounter + ". Site ID is out of scope\n\t" + line);
                    }
                }
                //recover commands
                else if (cleanedLine.startsWith("recover(")) {
                    int siteID = Integer.parseInt(cleanedLine.replaceAll("recover\\(", "").replaceAll("\\)", ""));
                    if (siteID > 0 && siteID <= dataSites.size()) {
                        dataSites.get(siteID - 1).recover();
                    } else {
                        throw new Exception("Syntax error at line " + lineCounter + ". Site ID is out of scope\n\t" + line);
                    }
                }
                //unexpected input
                else {
                    throw new Exception("Syntax error at line " + lineCounter + ".\n\t" + line);
                }

                //The purpose of this is to add a delay between two line reads, so that two consecutive begins
                // won't result in two transactions with the same start time, making is clear which transaction is older
                TimeUnit.MILLISECONDS.sleep(1);
            }
        }
    }

    //This method iterates over all data sites and calls the dump function from each site that is running
    public void dumpAll() {
        for (DataManager dataSite : dataSites) {
            if(dataSite.status == DataManager.RUNNING) {
                dataSite.dump();
            } else {
                System.out.println("Site " + dataSite.siteId + " is down.\n");
            }
        }
    }

    //This method calls the dump method for a specific site if it is running
    public void dumpSite(int siteID) {
        if (siteID > 0 && siteID <= dataSites.size()) {
            if (dataSites.get(siteID).status == DataManager.RUNNING) {
                dataSites.get(siteID - 1).dump();
            } else {
                System.out.println("Site " + siteID + " is down.\n");
            }
        }
    }

    //This method iterates over the running sites, and calls the dump item method from each site
    public void dumpDataItem(int dataItem) throws Exception {
        boolean success = false;
        for (DataManager dataSite : dataSites) {
            if(dataSite.status == DataManager.RUNNING) {
                if (dataSite.dump(dataItem))
                    success = true;
            }
        }

        if(!success) {
            System.out.println("Data item index " + dataItem + " is unavailable on running sites.\n");
        }
    }

    //This is a debugging method to print the list of running transactions and the status of all data sites
    public void queryState() {
        for (Transaction transaction : transactionManager.transactions) {
            System.out.println(transaction);
        }

        for (DataManager dataSite : dataSites) {
            System.out.println(dataSite);
        }
    }
}
