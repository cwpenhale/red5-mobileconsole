package org.red5.stream.http.servlet;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.singleton.AvailabilityService;

/**
 * Servlet implementation class TransportSegment
 */
public class TransportSegment extends HttpServlet {

	private static final long serialVersionUID = 2077065865046683060L;
	
	private static final Logger log = Logger.getLogger(TransportSegment.class.getName());
	
	@EJB
	private AvailabilityService availabilityService;
	
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		log.errorf("Segment requested");
		
		
		// http://localhost:5080/httplivestreamingstreaming/test1.ts
		
		String servletPath = request.getServletPath();
		
		int segmentDuration = availabilityService.getSegmentTimeLimit() / 1000;
		int numMaxSegments  = availabilityService.getNumMaxSegments();
		int numExpiredSegmentsOnCDN = availabilityService.getNumExpiredSegmentsOnCDN();
		/*
		 * CDN Segment Age:
		 * 		For a Origin-Pull CDN, a MPEG-TS segment should expire
		 * 		once it is not longer available on the server for a cycle.
		 * 		Calcuate by taking the duration of the segment, and multiplying
		 * 		it by the number of max segments, and adding twice the segment
		 * 		duration to that value. For example, a if a segment is 6
		 * 		seconds long, and there can only be 10 segments available
		 * 		on the server at a given time, the first segment is no longer
		 * 		available 66 seconds after it was first created.
		 */
		int cdnSegmentAge = (segmentDuration*numMaxSegments)+(numExpiredSegmentsOnCDN*segmentDuration);
		
		String[] path = servletPath.split("/");
		
		/*
		 * @param we are expecting the following servlet path: /Application/Stream/MobileProfileName/stream00000000000.ts
		 */
		
		String streamName = null;
		String mobileProfileName = null;
		String requestedSegment = null;
		String[] parts = servletPath.split("/");
		log.tracef("Parts: %s", parts.length);			
		if(!(parts.length==4)){
			log.errorf("Servlet Request was not formatted as expected. Expected /streamName/stream.m3u8 or /streamName/mobileProfileName/stream.m3u8 , but got: %s", servletPath);
			response.sendError(404, "Segment not found");
			return;
		}else{
			streamName = parts[1];
			log.errorf("Stream Name: %s", streamName);
			mobileProfileName = parts[2];
			log.errorf("Mobile Profile Name: %s", mobileProfileName);
			log.tracef("Requested Profile Name: %s", mobileProfileName);
			requestedSegment = path[3];
			log.tracef("Requested Segment: %s", requestedSegment);
		}

		

		
		/*	TODO: Define if this is appropriate for Origin-Pull CDNs.
		 * 		If the same CDN requests the file again, its probably necessary.
		 * 		Until proven otherwise, I am removing this code.
		 * 		Maybe when I reFOSS the app I will make this tunable;
		 * 	
		 *	//fail if they request the same segment
		 *  // if(tunableSessionBehavior){
		 * 	HttpSession session = ((HttpServletRequest) request).getSession(false);
		 *	if (session != null) {
		 *		String sN = (String) session.getAttribute("streamName");
		 *		String mPN = (String) session.getAttribute("mobileProfileName");
		 *		if (streamName.equals(sN)||mobileProfileName.equals(mPN)) {
		 *			log.info("Segment %s was already played by this requester", sN+mPN);
		 *			return;
		 *		}
		 *		session.setAttribute("stream", streamName);
		 *		session.setAttribute("mobileProfileName", mobileProfileName);
		 *	}
		 *		
		 */
		
		String fileNameSansExtension = requestedSegment.replace(".ts", "");
		String sequenceNumberStr = fileNameSansExtension.replace("stream", "");
		int sequenceNumber = Integer.valueOf(sequenceNumberStr);	
		log.tracef("Segment sequence: %s", sequenceNumber);
		
		if (availabilityService.isAvailable(streamName, mobileProfileName)) {
			response.setContentType("video/MP2T");
			byte[] segment = availabilityService.getSegment(streamName, mobileProfileName, sequenceNumber);
			if (segment != null) {
		        InputStream iS = new ByteArrayInputStream(segment);
				ServletOutputStream oS = response.getOutputStream();
		        response.setHeader("Cache-Control", "max-age="+cdnSegmentAge);
		        response.setContentType("video/MP2T");
		        response.setContentLength(segment.length);
		        if(IOUtils.copy(iS, oS) < 0){
		        	log.errorf("Error serving TS: %s", servletPath);
		        	response.sendError(500, "Unable to serve segment");
		        }
		        oS.flush();
		        oS.close();
			} else {
				log.infof("Segment for %s was not found", streamName);
			}
		} else {
			//T0D0 <== done? let requester know that stream segment is not available
			response.sendError(404, "Segment not found");
		}
		
	}

}
