package org.djna.asynch.intro;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ExampleRequester implements MessageListener {
    private static int ackMode;
    private static String clientQueueName;

    private boolean transacted = false;
    private MessageProducer producer;
    private Map<String, Message> correlationMap;

    static {
        clientQueueName = "client.messages";
        ackMode = Session.AUTO_ACKNOWLEDGE;
    }

    public ExampleRequester() {
        correlationMap = new HashMap<>();
        ActiveMQConnectionFactory connectionFactory
                = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(transacted, ackMode);
            Destination adminQueue = session.createQueue(clientQueueName);

            //Setup a message producer to send message to the queue the server is consuming from
            this.producer = session.createProducer(adminQueue);
            this.producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            //Create a temporary queue that this client will listen for responses on then create a consumer
            //that consumes message from this temporary queue...for a real application a client should reuse
            //the same temp queue for each message to the server...one temp queue per client
            Destination tempDest = session.createTemporaryQueue();
            System.out.printf("listening to %s%n", tempDest);
            MessageConsumer responseConsumer = session.createConsumer(tempDest);

            //This class will handle the messages to the temp queue as well
            responseConsumer.setMessageListener(this);

            //Now create the actual message you want to send
            TextMessage txtMessage = session.createTextMessage();
            txtMessage.setText("MyProtocolMessage");

            //Set the reply to field to the temp queue you created above, this is the queue the server
            //will respond to
            txtMessage.setJMSReplyTo(tempDest);

            //Set a correlation ID so when you get a response you know which sent message the response is for
            //If there is never more than one outstanding message to the server then the
            //same correlation ID can be used for all the messages...if there is more than one outstanding
            //message to the server you would presumably want to associate the correlation ID with this
            //message somehow...a Map works good
            String correlationId = this.createRandomString();
            correlationMap.put(correlationId, txtMessage);
            txtMessage.setJMSCorrelationID(correlationId);

            System.out.printf("sending %s to %s%n", correlationId, clientQueueName);

            this.producer.send(txtMessage, DeliveryMode.NON_PERSISTENT, 8, 60 * 1000);
        } catch (JMSException e) {
            System.out.printf("Exception %s%n", e);
        }
    }

    private String createRandomString() {
        Random random = new Random(System.currentTimeMillis());
        long randomLong = random.nextLong();
        return Long.toHexString(randomLong);
    }

    public void onMessage(Message message) {
        String messageText = null;
        try {
            System.out.println("reply message received" + message);
            Message requestMessage = correlationMap.get(message.getJMSCorrelationID());
            if ( requestMessage == null){
                System.out.printf("Unexpected message, %s%n", message.getJMSCorrelationID());
            } else{
                System.out.printf("Response to %s%n", requestMessage);
            }

            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                messageText = textMessage.getText();
                System.out.println("messageText = " + messageText);
            }
        } catch (JMSException e) {
            System.out.printf("Exception %s%n", e);
        }
    }

    public static void main(String[] args) {
        System.out.printf("Starting Requester %n");
        new ExampleRequester();
    }
}
