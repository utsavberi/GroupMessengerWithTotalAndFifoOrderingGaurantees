package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

public class GroupMessengerActivity extends Activity {

    private int last_agreed_priority = 0;
    private int last_proposed_priority = 0;
    private int counter = 0;
    private String myPort;


    private final PriorityQueue<ISISMessage> msgPriorityQueue = new PriorityQueue<ISISMessage>(10, new Comparator<ISISMessage>() {
        @Override
        public int compare(ISISMessage lhs, ISISMessage rhs) {
            if (lhs.priority != rhs.priority) {
                return lhs.priority - rhs.priority;
            } else
            {
                Log.d(SERVER_TAG, "compare : found same priority while inserting to mpq : lhs" + lhs.toString() + " rhs : " + rhs.toString() + " and chose " + (lhs.from + lhs.priority).compareTo(rhs.from + rhs.priority));
                return (lhs.from + lhs.priority).compareTo(rhs.from + rhs.priority);
            }
        }
    });

    private static final String SERVER_TAG = GroupMessengerActivity.class.getSimpleName() + ".Server";
    private static final String CLIENT_TAG = GroupMessengerActivity.class.getSimpleName() + ".Client";
    private static final String REMOTE_PORT0 = "11108";
    private static final String REMOTE_PORT1 = "11112";
    private static final String REMOTE_PORT2 = "11116";
    private static final String REMOTE_PORT3 = "11120";
    private static final String REMOTE_PORT4 = "11124";
    private static final int SERVER_PORT = 10000;
    private final ArrayList<String> remotePorts =new ArrayList<String>(){{add(REMOTE_PORT0); add(REMOTE_PORT1); add(REMOTE_PORT2); add(REMOTE_PORT3); add(REMOTE_PORT4);}};



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        Log.d(SERVER_TAG, "just Testing");
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));



        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(SERVER_TAG, "Can't create a ServerSocket " + e.getLocalizedMessage());
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText("");
                TextView textView1 = (TextView) findViewById(R.id.textView1);
                textView1.append("\t>>" + msg);
                textView1.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });

        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String msg = editText.getText().toString() + "\n";
                    editText.setText("");
                    TextView localTextView = (TextView) findViewById(R.id.textView1);
                    localTextView.append("\t>>" + msg);
                    TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                    remoteTextView.append("\n");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket clientSocket;
            try {
                while (true) {

                    clientSocket = serverSocket.accept();
                    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                    Object object = null;
                    try {
                        object = ois.readObject();
                        if (object instanceof ISISMessage) {
                            ISISMessage isisMessage = (ISISMessage) object;
                            Log.d(SERVER_TAG, "In server task do in background rcvd msg " + isisMessage.toString());
                            if (isisMessage.msgType == ISISMessage.MsgType.NONE) {
                                proposeMessagePriority(isisMessage);
                            } else if (isisMessage.msgType == ISISMessage.MsgType.PROPOSED_PRIORITY) {
                                decideAgreedPriority(isisMessage);
//                                checkPendingMsgs();
                            } else if (isisMessage.msgType == ISISMessage.MsgType.AGREED_PRIORITY) {//agreed priority type
                                publishWithAgreedPriority(isisMessage);
                            } else if(isisMessage.msgType == ISISMessage.MsgType.ERROR){
                                remotePorts.remove(isisMessage.message);
                                checkPendingMsgs();
                            }

                        } else {
                            Log.e(SERVER_TAG, "object not of type isis msg");
                        }

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private void checkPendingMsgs() {
            Iterator<ISISMessage> pq_iter = msgPriorityQueue.iterator();

            while (pq_iter.hasNext()) {
                ISISMessage iterMessage = pq_iter.next();
                if(iterMessage.proposedPriorities.size()>=remotePorts.size()){

                    ISISMessage isisMessage = iterMessage;
                    isisMessage.msgType = ISISMessage.MsgType.AGREED_PRIORITY;
                    isisMessage.undeliverable = false;
                    int maxPriority = Collections.max(iterMessage.proposedPriorities.values());
                    isisMessage.priority = maxPriority;
                    last_agreed_priority = maxPriority;
                    Log.d(SERVER_TAG, "decideAgreedPriority: multicasting final agreed msgs");
                    multicastIsisMsg(isisMessage);
                    Log.d(SERVER_TAG, "decideAgreedPriority: DONE multicasting final agreed msgs");

                }
            }
        }

        private void proposeMessagePriority(ISISMessage isisMessage) {
            int proposedPriority = Math.max(last_agreed_priority, last_proposed_priority) + 1;
            last_proposed_priority = proposedPriority;
            isisMessage.msgType = ISISMessage.MsgType.PROPOSED_PRIORITY;
            isisMessage.priority = proposedPriority;
            isisMessage.undeliverable = true;
            isisMessage.proposedBy = myPort;
            synchronized (msgPriorityQueue) {
                if (!msgPriorityQueue.contains(isisMessage))
                    msgPriorityQueue.add(isisMessage);
            }

            String sendProposedPriorityTo = isisMessage.from;
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(sendProposedPriorityTo));
                Log.d(SERVER_TAG, "proposeMessagePriority:replying to client with proposed priority :" + proposedPriority + " to port "
                        + sendProposedPriorityTo);
                ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
                Log.d(SERVER_TAG, "proposeMessagePriority:sending msg " + isisMessage);
                os.writeObject(isisMessage);
                Log.d(SERVER_TAG, "proposeMessagePriority:proposed priority se nt to client");
            } catch (IOException e) {
                e.printStackTrace();
                remotePorts.remove(sendProposedPriorityTo);
                ISISMessage errorMsg = new ISISMessage();
                errorMsg.from = myPort;
                errorMsg.msgType = ISISMessage.MsgType.ERROR;
                errorMsg.message = sendProposedPriorityTo;
                multicastIsisMsg(errorMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void decideAgreedPriority(ISISMessage isisMessage) {
            Log.d(SERVER_TAG, "decideAgreedPriority: isis msg priority type proposed" + isisMessage.priority + " by " + isisMessage.proposedBy);
            synchronized (msgPriorityQueue) {
                if (msgPriorityQueue.size() == 0) {
                    Log.d(SERVER_TAG, "decideAgreedPriority: msgPriority queue size was 0");
                    msgPriorityQueue.add(isisMessage);
                }
            }
            synchronized (msgPriorityQueue) {
                Iterator<ISISMessage> itr = msgPriorityQueue.iterator();

                while (itr.hasNext()) {
                    ISISMessage iterMessage = itr.next();
                    Log.d(SERVER_TAG, "decideAgreedPriority: pq statuus");
                    Log.d(SERVER_TAG, iterMessage.toString());
                    if (iterMessage.equals(isisMessage)) {
                        Log.d(SERVER_TAG, "decideAgreedPriority: found mesg in pq");
                        iterMessage.proposedPriorities.put(isisMessage.proposedBy, new Integer(isisMessage.priority));
                        if (iterMessage.proposedPriorities.size() >= remotePorts.size()) {
                            for (Map.Entry e : iterMessage.proposedPriorities.entrySet()) {
                                Log.d(SERVER_TAG, e.getKey() + " proposed " + e.getValue());
                            }
                            int maxPriority = Collections.max(iterMessage.proposedPriorities.values());
                            Log.d(SERVER_TAG, "decideAgreedPriority: max priority" + maxPriority);
                            isisMessage.msgType = ISISMessage.MsgType.AGREED_PRIORITY;
                            isisMessage.undeliverable = false;
                            isisMessage.priority = maxPriority;
                            last_agreed_priority = maxPriority;
                            Log.d(SERVER_TAG, "decideAgreedPriority: multicasting final agreed msgs");
                                multicastIsisMsg(isisMessage);
                                Log.d(SERVER_TAG, "decideAgreedPriority: DONE multicasting final agreed msgs");

                        }
                    }
                }
            }
        }

        private void publishWithAgreedPriority(ISISMessage isisMessage) {
            Log.d(SERVER_TAG, "publishWithAgreedPriority : in");
            synchronized (msgPriorityQueue) {
                Iterator<ISISMessage> itr = msgPriorityQueue.iterator();
                ISISMessage toRemove = null;
                while (itr.hasNext()) {
                    ISISMessage iterMessage = itr.next();
                    if (iterMessage.equals(isisMessage)) {
                        Log.d(SERVER_TAG, "publishWithAgreedPriority : found message to delete " + iterMessage + " matching " + isisMessage);
                        toRemove = iterMessage;
                    }

                }

                if (toRemove != null) {
                    msgPriorityQueue.remove(toRemove);

                    isisMessage.undeliverable = false;
                    if (!msgPriorityQueue.contains(isisMessage)) {
                        msgPriorityQueue.add(isisMessage);
                    } else {
                        Log.e(SERVER_TAG, "publishWithAgreedPriority :msq already had this key ");
                    }
                } else {
                    Log.d(SERVER_TAG, "publishWithAgreedPriority : Did not find msg in priority q ");
                }
            }
            publishTopPriorityDeliverableMsg();
        }

        private void publishTopPriorityDeliverableMsg() {
            Log.d(SERVER_TAG, "publishTopPriorityDeliverableMsg : will now publish top deliverables");
            Iterator<ISISMessage> iter = msgPriorityQueue.iterator();
            Log.d(SERVER_TAG, "publishTopPriorityDeliverableMsg : found the following msgs on p q");
            while (iter.hasNext()) {
                Log.d("publishTopPriorityDeliverableMsg : " + SERVER_TAG, iter.next().toString());
            }
            synchronized (msgPriorityQueue) {
                while (msgPriorityQueue.peek() != null && msgPriorityQueue.peek().undeliverable == false) {
//                Log.d(SERVER_TAG,"printing");
                    ISISMessage tmp = msgPriorityQueue.poll();
                    Log.d(SERVER_TAG, tmp.toString());
                    String msg = tmp.message;
                    Log.d(SERVER_TAG, "publishTopPriorityDeliverableMsg : printing " + tmp);
                    publishProgress(msg);
                    Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
                    ContentResolver mContentResolver = getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put("key", counter++);//(tmp.priority+tmp.uid));
                    cv.put("value", msg.trim());
                    mContentResolver.insert(mUri, cv);

                }
            }
        }


        protected void onProgressUpdate(String... strings) {
            String strReceived = strings[0].trim();
            TextView textView1 = (TextView) findViewById(R.id.textView1);
            textView1.append(strReceived + "\t\n");
            textView1.append("\n");
        }
    }

    private void multicastIsisMsg(ISISMessage isisMessage) {
        ArrayList<String> errRemotePorts = new ArrayList<>();
        synchronized (remotePorts) {
            String remotePort = "";
            for(int i = 0 ; i < remotePorts.size() ; i++)
//            for (String remotePort : remotePorts) {
//                String remotePort = remotePorts.get(i).toString();
            {
                try {

                    remotePort = remotePorts.get(i);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
                    Log.d(SERVER_TAG, "multicastIsisMsg : sending msg to " + remotePort);
                    os.writeObject(isisMessage);
                    Log.d(SERVER_TAG, "multicastIsisMsg : msg sent to " + remotePort);
                    socket.close();
                } catch (IOException ex) {
                    Log.e(CLIENT_TAG, ex.toString());
                    errRemotePorts.add(remotePort);
                    ex.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        }
        if (errRemotePorts.isEmpty() == false) {
            remotePorts.removeAll(errRemotePorts);
        }
    }

    private class ClientTask extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... msgs) {

            ISISMessage isisMessage = new ISISMessage(msgs[0]);
            isisMessage.from = myPort;

            ISISMessage local = new ISISMessage(msgs[0]);
            local.from = myPort;
            local.proposedBy = myPort;
            local.priority = Math.max(last_agreed_priority, last_proposed_priority) + 1;
            last_proposed_priority = local.priority;
            local.proposedPriorities.put(myPort, local.priority);
            local.uid = isisMessage.uid;
            String myPort = msgs[1];

            Log.d(CLIENT_TAG, "from " + myPort);

            synchronized (msgPriorityQueue) {
                msgPriorityQueue.add(local);
            }

            multicastIsisMsg(isisMessage);

            return null;
        }

    }

}
