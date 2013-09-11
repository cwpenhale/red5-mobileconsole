package com.destinationradiodenver.mobileStreaming.singleton;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

/**
 * Creates, updates, locates, and manages media segments.
 * 
 * Original Concept
 * @author Paul Gregoire
 * 
 * Named Pipe Adaptation and EJB Migration
 * @author Connor Penhale
 * 
 */

@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@AccessTimeout(value=10, unit=TimeUnit.SECONDS)
@Lock(LockType.READ)
public class AvailabilityService {

	private static final Logger log = Logger.getLogger(AvailabilityService.class.getName());

	// map of currently available (in-memory) segments, keyed by stream name
	private static ConcurrentHashMap<String, SegmentFacade> segmentMap = new ConcurrentHashMap<String, SegmentFacade>();
	public static ConcurrentHashMap<String, ArrayList<MobileProfile>> profileMap = new ConcurrentHashMap<String, ArrayList<MobileProfile>>();

	//TODO: Put this in a .props
	private static final int numMaxSegments = 10;
	private static final int segmentTimeLimit = 6000;
	private static final int numExpiredSegmentsOnCDN = 2;
	
	public String createUniqueName(String streamName, String mobileProfileName){
		String uniqueName = streamName;
		uniqueName += "_";
		uniqueName += mobileProfileName;
		return uniqueName;
	}

	public int getSegmentIndex(String streamName, String mobileProfileName) {
		return segmentMap.get(createUniqueName(streamName, mobileProfileName)).getIndex();
	}

	
	public boolean isAvailable(String streamName, String mobileProfileName){
		return segmentMap.containsKey(createUniqueName(streamName, mobileProfileName));
	}
	
	@Lock(LockType.WRITE)
	public void update(String uniqueName, Object passed) {
		log.errorf("Availability Service recieved an update from an SegmentConsumer");
		log.tracef("The SegmentConsumer passing the update is: %s", uniqueName);
		SegmentFacade segmentFacade = segmentMap.get(uniqueName);
		if(segmentFacade!=null){
			log.tracef("Adding to an existing segment");
			segmentFacade.addSegment((byte[]) passed);
			segmentMap.replace(uniqueName, segmentFacade);
		}else{
			log.errorf("Creating a new Segment for SegmentConsumer: %s", uniqueName);
			SegmentFacade newFacade = new SegmentFacade(uniqueName, numMaxSegments);
			newFacade.addSegment((byte[]) passed);
			segmentMap.put(uniqueName, newFacade);
		}
	}
	
	//TEST2
	public byte[] getSegment(String streamName, String mobileProfileName, int sequenceNumber) {
		return segmentMap.get(createUniqueName(streamName, mobileProfileName)).getSegment(sequenceNumber);
	}


	public int getSegmentCount(String streamName, String mobileProfileName) {
		//break it down
		String uniqueName = createUniqueName(streamName, mobileProfileName);
		log.errorf("UniqueName: %s", uniqueName);
		SegmentFacade sF = segmentMap.get(uniqueName);
		if(sF!=null)
			return sF.getSegmentCount();
		log.errorf("Segment Facade retreived was null");
		return 0;
	}
	
	public int getNumExpiredSegmentsOnCDN() {
		return numExpiredSegmentsOnCDN;
	}
	
	public int getSegmentTimeLimit() {
		return segmentTimeLimit;
	}

	public int getNumMaxSegments() {
		return numMaxSegments;
	}

	public boolean isADPAvailable(String streamName) {
		Set<String> set = segmentMap.keySet();
		log.tracef("There are %s segments in the segment map", set.size());
		for(String s : set){
			log.tracef("Examining key %s", s);
			if(s.startsWith(streamName))
				return true;
		}
		return false;
	}
	
	public ArrayList<MobileProfile> getMobileProfiles(String streamName) {
		return profileMap.get(streamName);
	}

private class SegmentFacade {
		
		// map of currently available segments
		HashMap<Integer, byte[]> segments = new HashMap<Integer, byte[]>();
		
		private int index;
		
		private String uniqueName;
		
		private int numMaxSegments;

		public int getIndex() {
			//for saftey, return one behind current
			int temp = index - 1;
			return temp;
		}

		public void setNumMaxSegments(int numMaxSegments) {
			this.numMaxSegments = numMaxSegments;
		}

		public SegmentFacade(String name, int numMaxSegments){
			log.tracef("Creating SegmentFacade for %s with a maximum of %s segments", name, numMaxSegments);
			setUniqueName(name);
			setNumMaxSegments(numMaxSegments);
			index = 0;
		}
		

		public void setUniqueName(String uniqueName) {
			this.uniqueName = uniqueName;
		}

		public int getSegmentCount() {
			return segments.size();
		}
		
		public void addSegment(byte[] segment){
			log.tracef("Adding segment %s in facade for: %s", index, uniqueName);
			if(segments.size() >= numMaxSegments){
				segments.remove(index-numMaxSegments);
			}
			segments.put(index, segment);
			log.tracef("Added segment %s in facade for: %s", index, uniqueName);
			index++;
			//TODO: Determine if this level of logging is useful
			//			It may be the lack of sleep but I think this is more logging than
			//			most people put in their applications
			/*if(log.isTraceEnabled()){
				int streamSizeInMemory = 0;
				for(int i = index; i < getSegmentCount(); i--){
					streamSizeInMemory += segments.get(i).length;
				}
				streamSizeInMemory = streamSizeInMemory/1024;
				log.tracef("SegmentFacade for %s is %skb:", uniqueName, streamSizeInMemory);
				long totalMemory = Runtime.getRuntime().totalMemory()/1024;
				long percent = (streamSizeInMemory/totalMemory)*100;
				log.tracef("SegmentFacade for %s is using %s% of JVM", uniqueName, percent);
			} */
		}

		/**
		 * Returns a segment matching the requested index.
		 * 
		 * @return segment matching the index or null
		 */
		
		public byte[] getSegment(int index) {
			return segments.get(index);
		}
		
	}

	public void addProfile(String publishedName, MobileProfile mP) {
		ArrayList<MobileProfile> list = getMobileProfiles(publishedName);
		if(list!=null){
			if(list.size()>0)
				list.add(mP);
		}else{
			list = new ArrayList<MobileProfile>();
			list.add(mP);
		}
		log.infof("Adding %s to profileMap", publishedName);
		profileMap.put(publishedName, list);
	}
	
	public void removeProfile(String publishedName, MobileProfile mP) {
		ArrayList<MobileProfile> list = getMobileProfiles(publishedName);
		if(list!=null){
			if(list.size()>0){
				list.remove(mP);
				profileMap.put(publishedName, list);
			}
		}
	}
}
