package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by utsav on 3/7/15.
 */
public class ISISMessage implements Serializable{

    public enum MsgType {
        PROPOSED_PRIORITY, AGREED_PRIORITY, NONE, ERROR
    }

    String from;
    String proposedBy;
    String uid;
    MsgType msgType;
    int priority;

    boolean undeliverable;
    String message;

    //proposed by,proposed priority
    HashMap <String,Integer> proposedPriorities = new HashMap<>();

    public ISISMessage(){
        undeliverable = true;
        msgType = MsgType.NONE;
        uid = UUID.randomUUID().toString();
    }
    public ISISMessage(ISISMessage ob){
        ob.from  = (from);
        ob.uid = new String(uid);
        ob.msgType = msgType;
        ob.priority = priority;

    }

    public ISISMessage(String msg){
        this();

        message = msg;

    }

    @Override
    public boolean equals(Object ob){
        return this.uid.equals(((ISISMessage)ob).uid);
    }
    
    @Override
    public String toString(){
        return "\from;"+from+" proposedBy;"+proposedBy+" uid;"+uid+" priorityType;"+
                msgType +" priority;"+priority+"  undeliverable;"+undeliverable+
                "  message;\n"+ message;
    }

}
