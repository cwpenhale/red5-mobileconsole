package com.destinationradiodenver.mobileStreaming.messages;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.logging.Logger;

public class StatusMessageDispatcher {

	private static final Logger log = Logger.getLogger(StatusMessageDispatcher.class);

	private String jmsTopicLookup;
	

	public void sendObjectMessage(StatusMessage statusMessage, String destination) throws JMSException {
		// create JMS message
		try {
			InitialContext context = new InitialContext();
			ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("java:/ConnectionFactory");
			Topic topic = (Topic) context.lookup(getJmsTopicLookup());
			Connection connection = connectionFactory.createConnection();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer producer = session.createProducer(topic);
			connection.start();
			ObjectMessage jmsMessage = session.createObjectMessage(statusMessage);
			try{
				producer.send(jmsMessage);
			}catch (JMSException ex){
				log.error("JMS Exception in StatusMessageDispatcher");
				ex.printStackTrace();
			}finally{
				log.info("Closing JMS connection");
				connection.close();
			}
		} catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public String getJmsTopicLookup() {
		return jmsTopicLookup;
	}


	public void setJmsTopicLookup(String jmsTopicLookup) {
		this.jmsTopicLookup = jmsTopicLookup;
	}

}
