package sa.uavcs.paho.client;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

public class MavMqttClient {

	int state = BEGIN;

	static final int BEGIN = 0;
	static final int CONNECTED = 1;
	static final int PUBLISHED = 2;
	static final int SUBSCRIBED = 3;
	static final int DISCONNECTED = 4;
	static final int FINISH = 5;
	static final int ERROR = 6;
	static final int DISCONNECT = 7;

	static final String BROKER_URL = "ec2-54-186-38-68.us-west-2.compute.amazonaws.com";
	static final int BROKER_PORT = 1884;
	static final String M2MIO_STUFF = "uas";
	static final String M2MIO_THING = "mav01";
	static final String M2MIO_USERNAME = "";
	static final String M2MIO_PASSWORD_MD5 = "";
	static boolean ssl = false;

	private static MavMqttClient instance = null;

	static final String subTopic = "uavcs/#";
	static final String pubTopic = "uavcs/" + M2MIO_THING + "/";

	public static MavMqttClient getInstance() {
		if (instance == null) {
			instance = new MavMqttClient();
		}
		return instance;
	}

	/**
	 * The main entry point of the sample.
	 *
	 * This method handles parsing the arguments specified on the command-line
	 * before performing the specified action.
	 */
	protected MavMqttClient() {

		// Default settings:
		boolean quietMode = false;
		String action = "publish";
		String message = "Message from uas client waiter (Paho MQTTv3)";
		int qos = 2;
		String broker = BROKER_URL;
		int port = BROKER_PORT;
		String clientId = M2MIO_THING + "_" + java.util.UUID.randomUUID();

		boolean cleanSession = true; // Non durable subscriptions

		String userName = null;
		String password = null;

		String protocol = "tcp://";

		if (ssl) {
			protocol = "ssl://";
		}

		String url = protocol + broker + ":" + port;

		// With a valid set of arguments, the real work of
		// driving the client API can begin
		try {
			// Create an instance of this class
			init(url, clientId, cleanSession, quietMode, userName, password);

			// Perform the specified action

		} catch (MqttException me) {
			// Display full details of any exception that occurs
			System.out.println("reason " + me.getReasonCode());
			System.out.println("msg " + me.getMessage());
			System.out.println("loc " + me.getLocalizedMessage());
			System.out.println("cause " + me.getCause());
			System.out.println("excep " + me);
			me.printStackTrace();
		}
	}

	// Private instance variables
	private MqttAsyncClient client;
	private String brokerUrl;
	private boolean quietMode;
	private MqttConnectOptions conOpt;
	private boolean clean;
	private String password;
	private String userName;
	Object waiter = new Object();
	boolean donext = false;
	Throwable ex = null;

	/**
	 * Constructs an instance of the sample client wrapper
	 * 
	 * @param brokerUrl
	 *            the url to connect to
	 * @param clientId
	 *            the client id to connect with
	 * @param cleanSession
	 *            clear state at end of connection or not (durable or
	 *            non-durable subscriptions)
	 * @param quietMode
	 *            whether debug should be printed to standard out
	 * @param userName
	 *            the username to connect with
	 * @param password
	 *            the password for the user
	 * @throws MqttException
	 */
	public void init(String brokerUrl, String clientId, boolean cleanSession, boolean quietMode, String userName,
			String password) throws MqttException {

		this.brokerUrl = brokerUrl;
		this.quietMode = quietMode;
		this.clean = cleanSession;
		this.password = password;
		this.userName = userName;

		String tmpDir = System.getProperty("java.io.tmpdir");
		MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);

		try {
			// Construct the object that contains connection parameters
			// such as cleanSession and LWT
			conOpt = new MqttConnectOptions();
			conOpt.setCleanSession(clean);
			if (password != null) {
				conOpt.setPassword(this.password.toCharArray());
			}
			if (userName != null) {
				conOpt.setUserName(this.userName);
			}

			// Construct the MqttClient instance
			client = new MqttAsyncClient(this.brokerUrl, clientId, dataStore);
			// Connect to the MQTT server
			log("Connecting to " + brokerUrl + " with client ID " + client.getClientId());
			client.connect(conOpt);
			
		} catch (MqttException e) {
			e.printStackTrace();
			log("Unable to set up client: " + e.toString());
			System.exit(1);
		}
	}

	/**
	 * Utility method to handle logging. If 'quietMode' is set, this method does
	 * nothing
	 * 
	 * @param message
	 *            the message to log
	 */
	private void log(String message) {
		if (!quietMode) {
			System.out.println(message);
		}
	}

	/**
	 * Publish / send a message to an MQTT server
	 * 
	 * @param topicName
	 *            the name of the topic to publish to
	 * @param qos
	 *            the quality of service to delivery the message at (0,1,2)
	 * @param payload
	 *            the set of bytes to send to the MQTT server
	 * @throws MqttException
	 */
	public void publish(String topicName, int qos, byte[] payload) throws Throwable {
		// Use a state machine to decide which step to do next. State change
		// occurs
		// when a notification is received that an MQTT action has completed
		while (state != FINISH) {
			switch (state) {
			case BEGIN:
				// Connect using a non-blocking connect
				MqttConnector con = new MqttConnector();
				con.doConnect();
				break;
			case CONNECTED:
				// Publish using a non-blocking publisher
				Publisher pub = new Publisher();
				pub.doPublish(pubTopic + topicName, qos, payload);
				break;
			case PUBLISHED:
				state = DISCONNECT;
				donext = true;
				break;
			case DISCONNECT:
				Disconnector disc = new Disconnector();
				disc.doDisconnect();
				break;
			case ERROR:
				throw ex;
			case DISCONNECTED:
				state = FINISH;
				donext = true;
				break;
			}

			// if (state != FINISH) {
			// Wait until notified about a state change and then perform next
			// action
			waitForStateChange(10000);
			// }
		}
	}
	
	

	public void publishsync(String topicName, int qos, byte[] payload)  {
  
		log("Connected");

		String time = new java.sql.Timestamp(System.currentTimeMillis()).toString();
		log("Publishing at: " + time + " to topic \"" + topicName + "\" qos " + qos);

		// Create and configure a message
		MqttMessage message = new MqttMessage(payload);
		message.setQos(qos);

		// Send the message to the server, control is not returned until
		// it has been delivered to the server meeting the specified
		// quality of service.
		try {
			client.publish(pubTopic + topicName, message);
		} catch (MqttPersistenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
 
	}
	
	public void disconnect () {
		
		// Disconnect the client
		try {
			client.disconnect();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log("Disconnected");
	}

	/**
	 * Wait for a maximum amount of time for a state change event to occur
	 * 
	 * @param maxTTW
	 *            maximum time to wait in milliseconds
	 * @throws MqttException
	 */
	private void waitForStateChange(int maxTTW) throws MqttException {
		synchronized (waiter) {
			if (!donext) {
				try {
					waiter.wait(maxTTW);
				} catch (InterruptedException e) {
					log("timed out");
					e.printStackTrace();
				}

				if (ex != null) {
					throw (MqttException) ex;
				}
			}
			donext = false;
		}
	}

	/**
	 * Connect in a non-blocking way and then sit back and wait to be notified
	 * that the action has completed.
	 */
	public class MqttConnector {

		public MqttConnector() {
		}

		public void doConnect() {
			// Connect to the server
			// Get a token and setup an asynchronous listener on the token which
			// will be notified once the connect completes
			log("Connecting to " + brokerUrl + " with client ID " + client.getClientId());

			IMqttActionListener conListener = new IMqttActionListener() {
				public void onSuccess(IMqttToken asyncActionToken) {
					log("Connected");
					state = CONNECTED;
					carryOn();
				}

				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = ERROR;
					log("connect failed" + exception);
					carryOn();
				}

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				// Connect using a non-blocking connect
				client.connect(conOpt, "Connect sample context", conListener);
			} catch (MqttException e) {
				// If though it is a non-blocking connect an exception can be
				// thrown if validation of parms fails or other checks such
				// as already connected fail.
				state = ERROR;
				donext = true;
				ex = e;
			}
		}
	}

	public class Publisher {
		public void doPublish(String topicName, int qos, byte[] payload) {
			// Send / publish a message to the server
			// Get a token and setup an asynchronous listener on the token which
			// will be notified once the message has been delivered
			MqttMessage message = new MqttMessage(payload);
			message.setQos(qos);

			String time = new java.sql.Timestamp(System.currentTimeMillis()).toString();
			log("Publishing at: " + time + " to topic \"" + topicName + "\" qos " + qos);

			// Setup a listener object to be notified when the publish
			// completes.
			//
			IMqttActionListener pubListener = new IMqttActionListener() {
				public void onSuccess(IMqttToken asyncActionToken) {
					String time2 = new java.sql.Timestamp(System.currentTimeMillis()).toString();
					log("Publish Completed :" + time2);
					// state = PUBLISHED;
					state = CONNECTED;
					carryOn();
				}

				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = ERROR;
					log("Publish failed" + exception);
					carryOn();
				}

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				// Publish the message
				// client.publish(topicName, message);
				client.publish(topicName, message, "Pub sample context", pubListener);
			} catch (MqttException e) {
				state = ERROR;
				donext = true;
				ex = e;
			}
		}
	}

	/**
	 * Disconnect in a non-blocking way and then sit back and wait to be
	 * notified that the action has completed.
	 */
	public class Disconnector {
		public void doDisconnect() {
			// Disconnect the client
			log("Disconnecting");

			IMqttActionListener discListener = new IMqttActionListener() {
				public void onSuccess(IMqttToken asyncActionToken) {
					log("Disconnect Completed");
					state = DISCONNECTED;
					carryOn();
				}

				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = ERROR;
					log("Disconnect failed" + exception);
					carryOn();
				}

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				client.disconnect("Disconnect sample context", discListener);
			} catch (MqttException e) {
				state = ERROR;
				donext = true;
				ex = e;
			}
		}
	}

}
