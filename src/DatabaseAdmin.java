import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseAdmin {
    private static final Logger logger = Logger.getLogger(DatabaseAdmin.class.getName());
    private final FirebaseDatabase databaseInstance;
    private DatabaseReference allMessagesRef;
    private DatabaseReference treatmentAll_TasksRef;
    private DatabaseReference allTasksPath;
    boolean initiated;

    /**
     * Authenticate Server - Using Database Admin API
     */
    public DatabaseAdmin() {
        FileInputStream serviceAccount = null;
        try {
            serviceAccount = new FileInputStream(VALUES.FILE_PATH_TO_DB_CREDENTIALS);
            // Initialize the app with a custom auth variable
            Map<String, Object> auth = new HashMap<String, Object>();
            auth.put("uid", VALUES.DB_ADMIN_AUTH_VALUE);
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
                    .setDatabaseUrl(VALUES.FIREBASE_DB_URL)
                    .setDatabaseAuthVariableOverride(auth)
                    .build();

            FirebaseApp.initializeApp(options);
        } catch (IOException e) {e.printStackTrace();}
        databaseInstance = FirebaseDatabase.getInstance();
        allMessagesRef = databaseInstance.getReference("userTaskInfo");
        allTasksPath = databaseInstance.getReference("tasks");
        treatmentAll_TasksRef = allTasksPath.child("treatmentAll");
        initiated = false;
    }

    /**
     * Database listener for all tasks added to database.
     * All 'new' tasks are sent to users as notifications. *
     * The ChildEventListener received a callback for every
     * child in the database when created (this is ignored).
     * Then the ValueEventListener receives a callback (this
     * sets initialise to true). The ChildEventListener now
     * receives a callback for every new child added to the database.
     */
    public void addNewTaskListener() {
        logger.log(Level.INFO, "Adding new task listener");

        treatmentAll_TasksRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
                if (!initiated) {
                    //This ignored the tasks that are in the database already when the server is run
                    return;
                }
                String taskId = dataSnapshot.getKey();
                logger.log(Level.INFO, "New Task Added To DB, taskId: " + taskId);
                Object value = dataSnapshot.getValue();
                String jsonTaskString = new Gson().toJson(value);
                String state = String.valueOf(dataSnapshot.child("state").getValue());
                String treatment = dataSnapshot.getRef().getParent().getKey();
                if (state.equalsIgnoreCase("notify") || state.equalsIgnoreCase("notifyavailable")) {
                    Main.sendTaskData(jsonTaskString, taskId, VALUES.TOPICS_TEST);
                    Main.getMessagesForTask(taskId);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });

        // Callback received only once all initial children have been read.
        treatmentAll_TasksRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                initiated = true;
                logger.log(Level.INFO, "Database has " + dataSnapshot.getChildrenCount() + " tasks on server start.");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });
    }

    /**
     * Reads all (user task info) messages from users for a specific task.
     * (These messages are stored in the database)
     * Sends task notification to ALL(for now) users that send a message (in time).
     * If a client device sends a (user task info) message after OnDataChange(...) is called,
     * it will not be read in this method.
     * @param taskId of task to check messages for
     */
    public void getMessagesForTask(String taskId) {
        //Read all user task info messages for task from database
        logger.log(Level.INFO, "Reading user task info messages for task: " + taskId);
        DatabaseReference taskMessagesRef = allMessagesRef.child(taskId);
        taskMessagesRef.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                ArrayList<String> userIds = new ArrayList<>();
                //Iterate through all messages
                for (DataSnapshot messageData : dataSnapshot.getChildren()) {
                    String userId = String.valueOf(messageData.child("userId").getValue());
                    String locationScore = String.valueOf(messageData.child("locationScore").getValue());
                    logger.log(Level.INFO, "user task info message in db score:" + locationScore);
                    userIds.add(userId);
                    //Can create UserTaskInfo Object using Gson and use these for processing
                }

                //TODO processing / selecting users
                sendTaskNotificationToUsers(taskId, userIds);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });
    }

    /**
     * Reads all (user task info) messages from users for all tasks.
     * (These messages are stored in the database)
     * Sends task notification to ALL(for now) users that send a message (in time).
     * If a client device sends a (user task info) message after OnDataChange(...) is called,
     * it will not be read in this method.
     * TODO: delete this (mostly duplicate) method if not needed.
     */
    public void getMessagesForAllTasks() {
        //Read all user task info messages from database
        logger.log(Level.INFO, "Adding listener to all tasks");
        allMessagesRef.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> userIds = new ArrayList<>();
                //Iterate through all tasks
                for (DataSnapshot taskData : dataSnapshot.getChildren()) {
                    String taskId = taskData.getKey();
                    //Iterate through all messages
                    for (DataSnapshot messageData : taskData.getChildren()) {
                        String userId = String.valueOf(messageData.child("userId").getValue());
                        logger.log(Level.INFO, "taskId:userId - " + taskId + ":" + userId);
                        userIds.add(userId);
                        //Can create UserTaskInfo Object using Gson and use these for processing
                    }//todo no double loop?

                    //TODO processing / selecting users
                    sendTaskNotificationToUsers(taskId, userIds);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });

        //removing messages for all tasks
        //messagesRef.removeValue();
    }

    /**
     * Sends notification to all users in list. (Calls Main which calls FCM Server)
     * Retrieves task data from database to be sent to user with notification.
     * @param taskId of task
     * @param userIds List of users to send task notification to
     */
    private void sendTaskNotificationToUsers(String taskId, ArrayList<String> userIds) {
        logger.log(Level.INFO, "Getting task details from DB for notification");
        //Read task details from database
        DatabaseReference taskRef = treatmentAll_TasksRef.child(taskId);
        taskRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String jsonTaskString = dataSnapshot.getValue(String.class);
                logger.log(Level.INFO, "Json task details for notification: " + jsonTaskString);
                Main.sendTaskNotification(jsonTaskString, userIds);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });
    }

    /**
     * If a task has no user set (user = null) or no user field,
     * then change state of task to 'available'.
     * @param taskId of task
     */
    public void makeTaskAvailableIfNotTaken(String taskId) {
        logger.log(Level.INFO, "Checking if task taken: " + taskId);
        //Read task info from database
        DatabaseReference taskRef = treatmentAll_TasksRef.child(taskId);
        taskRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //Check user field of task
                String userId = String.valueOf(dataSnapshot.child("user").getValue());
                String state = String.valueOf(dataSnapshot.child("state").getValue());
                logger.log(Level.INFO, "task taken? user: " + userId);
                logger.log(Level.INFO, "current task state: " + state);
                if (!dataSnapshot.hasChild("user") || dataSnapshot.child("user") == null) {
                    makeTaskAvailable(taskId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                logger.log(Level.INFO, "DB Error: " + databaseError);
            }
        });
    }

    /**
     * Sets the state, of the task, to 'available'. Doesn't check current state of task.
     * Also removes all user task info messages for this task
     * @param taskId of task
     */
    private void makeTaskAvailable(String taskId) {
        logger.log(Level.INFO, "Making task available: " + taskId);
        DatabaseReference taskRef = treatmentAll_TasksRef.child(taskId);
        taskRef.child("state").setValue("available");
        removeUserTaskInfoMessages(taskId);
    }

    private void removeUserTaskInfoMessages(String taskId) {
        logger.log(Level.INFO, "Removing User Task Info messages for task: " + taskId);
        DatabaseReference taskMessagesRef = allMessagesRef.child(taskId);
        taskMessagesRef.removeValue();
    }

    private void removeAllUserTaskInfoMessages() {
        logger.log(Level.INFO, "Removing All User Task Info messages");
        allMessagesRef.removeValue();
    }
}