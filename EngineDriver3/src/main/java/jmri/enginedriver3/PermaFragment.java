package jmri.enginedriver3;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by activity on first run, this fragment has no ui, and will be retained without 
 *   restart until the app is ended.  It is responsible for all of the background threads,
 *   starting, communicating with, and stopping them as needed.  It is also responsible for 
 *   maintaining many of the shared entities found in mainApp.
 */
public class PermaFragment extends Fragment {

    private static MainApplication mainApp; // hold pointer to mainApp
    private int started = 0;

    private Thread  jmdnsRunnableThread = null;
    public Handler jmdnsRunnableHandler;  //this is set by the thread after startup
    private Thread  debugRunnableThread = null;
    public Handler debugRunnableHandler;  //this is set by the thread after startup
    private Thread  webSocketRunnableThread = null;
    public Handler webSocketRunnableHandler;  //this is set by the thread after startup

    protected MainActivity mainActivity = null;

    PermaFragment permaFragment; //set in constructor
    Handler permaFragHandler = new PermaFrag_Handler();

    public PermaFragment() {
        permaFragment = this;
    }
    @Override
    public void onAttach(Activity activity) {
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onAttach()");
        this.mainActivity = (MainActivity) activity;  //save ref to the new activity
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onCreate()");
        mainApp =(MainApplication)getActivity().getApplication();  //set pointer to app

        //TODO: load from storage, once save is implemented
//	    setServer("10.10.3.131");
//	    setServer("192.168.1.247");
        mainApp.setServer(null);  //this will be set once connected, by the CommThread
//        mainApp.setWiThrottlePort(0);
        mainApp.setWebPort(-1);
        mainApp.discoveredServersList = new ArrayList<HashMap<String, String> >();

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDetach() {
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onDetach()");
        this.mainActivity = null;  //remove ref to the old activity
        super.onDetach();
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onActivityCreated()");
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);  //this is what makes the fragment not go away on rotation
    }
    @Override
    public void onStart() {
        started++;
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onStart() " + started);
        super.onStart();
        startThreads();
    }
    @Override
    public void onDestroy() {
        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onDestroy()");
        cancelThreads();
        super.onDestroy();
    }
//    @Override
//    public void onStop() {
//        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onStop()");
//        super.onStop();
//    }
//    @Override
//    public void onResume() {
//        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onResume()");
//        super.onResume();
//    }
//    @Override
//    public void onPause() {
//        Log.d(Consts.DEBUG_TAG, "in PermaFrag.onPause()");
//        super.onPause();
//    }
    /** Start the "permanent" background tasks, which run with app. */
    public void startThreads() {
        if (jmdnsRunnableThread == null) {
            Log.d(Consts.DEBUG_TAG, "starting the jmdnsRunnableThread");
            jmdnsRunnableThread = new Thread(new JmdnsRunnable(this, mainApp)); //create thread, pass ref back to this fragment
            jmdnsRunnableThread.start();
        }
        if (debugRunnableThread == null) {
            Log.d(Consts.DEBUG_TAG, "starting the debugRunnableThread");
            debugRunnableThread = new Thread(new DebugRunnable(this, mainApp)); //create thread, pass ref back to this fragment
            debugRunnableThread.start();
        }
    }

    /** Cancel the background tasks via message handlers*/
    public void cancelThreads() {
        if (jmdnsRunnableThread != null) {
            Log.d(Consts.DEBUG_TAG, "ending the jmdnsRunnableThread");
            mainApp.sendMsg(jmdnsRunnableHandler, MessageType.SHUTDOWN);
            jmdnsRunnableThread = null;
        }
        if (debugRunnableThread != null) {
            Log.d(Consts.DEBUG_TAG, "ending the debugRunnableThread");
            mainApp.sendMsg(debugRunnableHandler, MessageType.SHUTDOWN);
            debugRunnableThread = null;
        }
        cancelWebSocketThread();
    }

    private void cancelWebSocketThread() {
        if (webSocketRunnableThread != null) {
            Log.d(Consts.DEBUG_TAG, "ending the webSocketRunnableThread");
            if (webSocketRunnableThread.isAlive()) {
                mainApp.sendMsg(webSocketRunnableHandler, MessageType.SHUTDOWN);
            }
            webSocketRunnableThread = null;
        }
    }


    private class PermaFrag_Handler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessageType.CONNECT_REQUESTED:
                    Log.d(Consts.DEBUG_TAG, "in PermaFrag_Handler.handleMessage() CONNECT_REQUESTED");
                    //start websocket thread and let its success send CONNECTED
                    if (webSocketRunnableThread != null) {  //one is started, shut it down, give it a chance to end, try again
                        Log.d(Consts.DEBUG_TAG, "webSocketRunnableThread already running, shutting down and retrying start");
                        mainApp.sendMsg(webSocketRunnableHandler, MessageType.SHUTDOWN);  //shut down running thread
                        mainApp.sendMsgDelayed(this, 1000, msg);  //delay to give shutdown time to run, then try again
                    } else {  //start up a new webSocketRunnable
                        Log.d(Consts.DEBUG_TAG, "starting the webSocketRunnableThread");
                        String rs = msg.obj.toString();
                        int wsp = msg.arg1;
                        webSocketRunnableThread = new Thread(new WebSocketRunnable(permaFragment, mainApp,
                                rs, wsp)); //create thread, pass ref back to this fragment, also rqsted connection
                        webSocketRunnableThread.start();
                    }
                    break;
                case MessageType.DISCONNECT_REQUESTED:
                    Log.d(Consts.DEBUG_TAG, "in PermaFrag_Handler.handleMessage() DISCONNECT_REQUESTED");
                    cancelWebSocketThread();
                    break;
                //server is lost, clear out variables
                case MessageType.DISCONNECTED:
                    Log.d(Consts.DEBUG_TAG, "in PermaFrag_Handler.handleMessage() DISCONNECTED");
                    webSocketRunnableThread = null;
                    webSocketRunnableHandler = null;
                    mainApp.setServer(null);
                    mainApp.setWebPort(-1);
                    mainApp.setJmriVersion(null);
                    mainApp.setPowerState(null);
                    if (mainActivity!=null) {
                        mainApp.sendMsg(mainActivity.mainActivityHandler, msg);  //forward to activity
                    }
                    break;
                //simply forward these along to activity
                case MessageType.MESSAGE_LONG:
                case MessageType.MESSAGE_SHORT:
                case MessageType.DISCOVERED_SERVER_LIST_CHANGED:
                case MessageType.CONNECTED:
                case MessageType.HEARTBEAT:
                case MessageType.POWER_STATE_CHANGED:
                case MessageType.JMRI_TIME_CHANGED:
                    if (mainActivity!=null) {
                        mainApp.sendMsg(mainActivity.mainActivityHandler, msg);
                    }
                    break;

                default:  //log unknown messages, probably indicates missing coding
                    Log.w(Consts.DEBUG_TAG, "in PermaFrag_Handler.handleMessage() received unknown message type " + msg.what);
                    break;
            }  //end of switch msg.what
            super.handleMessage(msg);
        }
    }

}