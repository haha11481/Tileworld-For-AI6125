package tileworld.agent;

public class Message {
  private final String from; // the sender
  private final String to; // the recepient
  private final String message; // the message

  public Message(String from, String to, String message) {
    this.from = from;
    this.to = to;
    this.message = message;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getMessage() {
    return message;
  }

}
