/*
* This class define Counters informations such as CounterID, QueueLength, and AlertActive.
* server use this class to store the information of each Counter.
 */
public class CounterData{
    private final String counterID; // the identifier consist of a letter and a number.
    private Integer queueLength; // store the latest queue number. (null = PENDING)
    private boolean alertActive; // true when a counter has entered a state of congestion, and alert has already been sent. 

    public CounterData(String counterId) {
        this.counterID = counterId;
        this.queueLength = null;
        this.alertActive = false;
    }
    // getters
    public String getCounterId() {
        return counterID;
    }

    public Integer getQueueLength() {
        return queueLength;
    }
    // check whether the counter is still in a PENDING state.
    public boolean isPending() {
        return queueLength == null;
    }
    // return the current state of the alert.
    public boolean isAlertActive() {
        return alertActive;
    }  
    // setters
    // changes the number of people waiting.
    public void setQueueLength(int queueLength){
        // Reject queue lengths outside the allowed range (0 to 30).
        if (queueLength < 0 || queueLength > 30){
            throw new IllegalArgumentException("Queue length must be between 0 and 30");
        }
        this.queueLength = queueLength;
    }
    // change the alert status.
    public void setAlertActive(boolean alertActive) {
        this.alertActive = alertActive;
    }
}