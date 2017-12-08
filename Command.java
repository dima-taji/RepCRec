/**
 * This class is used to store the commands that will be added to the waiting queue
 */
public class Command implements Comparable<Command>{
    String command;
    int transactionID;
    int dataItem;
    int dataValue;

    public Command(String command, int transactionID, int dataItem, int dataValue) {
        this.command = command;
        this.transactionID = transactionID;
        this.dataItem = dataItem;
        this.dataValue = dataValue;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public int getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(int transactionID) {
        this.transactionID = transactionID;
    }

    public int getDataItem() {
        return dataItem;
    }

    public void setDataItem(int dataItem) {
        this.dataItem = dataItem;
    }

    public int getDataValue() {
        return dataValue;
    }

    public void setDataValue(int dataValue) {
        this.dataValue = dataValue;
    }


    @Override
    public int compareTo(Command o) {
        if(command.equals(o.command) && transactionID == o.transactionID && dataItem == o.dataItem && dataValue == o.dataValue)
            return 0;
        else
            return -1;
    }
}
