package com.destinationradiodenver.mobileStreaming.cdi;

import javax.enterprise.inject.Produces;

import org.jboss.seam.spring.context.SpringContext;
import org.jboss.seam.spring.context.Web;
import org.jboss.seam.spring.inject.SpringBean;
import org.springframework.context.ApplicationContext;

import com.destinationradiodenver.mobileStreaming.Application;

public class Producers {
    
    @Produces @Web @SpringContext
    ApplicationContext context;
    
    @SpringContext
    @Produces @SpringBean
    Application application;
}