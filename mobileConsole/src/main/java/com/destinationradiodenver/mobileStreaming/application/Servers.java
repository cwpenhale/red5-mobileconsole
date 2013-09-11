package com.destinationradiodenver.mobileStreaming.application;

import java.io.Serializable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Named;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.web.entity.Red5Server;

@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@AccessTimeout(value=10, unit=TimeUnit.SECONDS)
@Named
public class Servers extends CopyOnWriteArrayList<Red5Server> implements Serializable {
	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = -5411315964008656521L;
	private Logger log = Logger.getLogger(Servers.class);
	
	public Servers(){
	}
	
	@PostConstruct
	private void init(){
		log.info("Encoders Servers initialized");
	}
	
}