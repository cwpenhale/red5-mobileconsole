package org.red5.stream.http.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.ejb.EJB;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.singleton.AvailabilityService;



/**
 * Provides an http stream playlist in m3u8 format.
 * 
 * Original Concept
 * @author Paul Gregoire
 * 
 * Named Pipe and EJB Adaptation
 * @author Connor Penhale
 * 
 * HTML status codes used by this servlet:
 * <pre>
 *  400 Bad Request
 *  406 Not Acceptable
 *  412 Precondition Failed
 *  417 Expectation Failed
 * </pre>
 * 
 * @see
 * {@link http://tools.ietf.org/html/draft-pantos-http-live-streaming-03}
 * {@link http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/StreamingMediaGuide/HTTPStreamingArchitecture/HTTPStreamingArchitecture.html#//apple_ref/doc/uid/TP40008332-CH101-SW2}
 */
public class PlayList extends HttpServlet {
	
	/**
	 * @author Connor Penhale
	 */
	private static final long serialVersionUID = 7051372891611466350L;

	private static final Logger log = Logger.getLogger(PlayList.class.getName());
	
	@EJB
	private AvailabilityService availabilityService;
	
	// number of segments that must exist before displaying any in the playlist
	private int minimumSegmentCount = 1;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		/*
		 * To supply a parameter to a servlet, use a config or init param.
		 * The config-param looks like this:
		 * <pre>
		 *     <context-param>
		 *     <param-name>startStreamOnRequest</param-name>
		 *     <param-value>true</param-value>
		 *     </context-param>
		 * </pre>
		 * 
		 * And is accessed like so:
		 * config.getServletContext().getInitParameter("startStreamOnRequest")
		 * 
		 * An init-param looks like this:
		 * <pre>
		 *     <servlet>
		 *     <servlet-name>PlayList</servlet-name>
		 *     <servlet-class>org.red5.stream.http.servlet.PlayList</servlet-class>
		 *     <init-param>
		 *     <param-name>startStreamOnRequest</param-name>
		 *     <param-value>true</param-value>
		 *     </init-param>
		 *     </servlet>
		 * </pre>
		 * 
		 * And is accessed like so:
		 * getInitParameter("startStreamOnRequest");
		 * 
		 */
		String minimumSegmentCountParam = getInitParameter("minimumSegmentCount");
		if (!StringUtils.isEmpty(minimumSegmentCountParam)) {
			minimumSegmentCount = Integer.valueOf(minimumSegmentCountParam);
		}
		log.errorf("Minimum segment count - param: %s value: %s", minimumSegmentCountParam, minimumSegmentCount);
	}

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
		log.errorf("Playlist requested");

		String servletPath = request.getServletPath();

		/*
		 * Expected Paths:
		 * 
		 * 		/streamName/stream.m3u8
		 * 		/streamName/mobileProfileName/stream.m3u8
		 * 
		 */
		log.tracef("Posting: %s", servletPath);

		String streamName = null;
		String mobileProfileName = null;
		String[] parts = servletPath.split("/");
		log.tracef("Parts: %s", parts.length);			
		if(!(parts.length==3||parts.length==4)){
			log.errorf("Servlet Request was not formatted as expected. Expected /streamName/stream.m3u8 or /streamName/mobileProfileName/stream.m3u8 , but got: %s", servletPath);
			response.sendError(404, "Playlist not found");
			return;
		}else{
			if(parts.length>=3){
				streamName = parts[1];
				log.errorf("Stream Name: %s", streamName);
				if(parts.length==4){
					mobileProfileName = parts[2];
					log.errorf("Mobile Profile Name: %s", mobileProfileName);
				}
			}

		}
		
		//write the playlist
		
        PrintWriter writer = response.getWriter();
		int segmentDuration = availabilityService.getSegmentTimeLimit() / 1000;
		
		// The amount of time a Origin-Pull CDN should cache an m3u8 playlist is 1 second shorter than the duration of a segment
		int cdnMaxAgeSeconds = segmentDuration - 1;
        response.setHeader("Cache-Control", "max-age="+cdnMaxAgeSeconds);
        // TODO: define if needs to be tunable
        
        response.setContentType("application/x-mpegURL");
        
		
        //Determine if mobile profile or adaptive bitrate playlist
        if(mobileProfileName!=null){
        	
        	/*
        	 * Stream Playlist (Not root, adaptive playlist)
        	 * 
        	 * Example:
        	 * 
        	 * 	 	#EXTM3U
        	 * 		#EXT-X-ALLOW-CACHE:NO
        	 * 		#EXT-X-MEDIA-SEQUENCE:0
			 *		#EXT-X-TARGETDURATION:10
			 *
			 *		#EXTINF:10,
			 *		http://media.example.com/segment1.ts
			 *		#EXTINF:10,
			 *		http://media.example.com/segment2.ts
			 *		#EXTINF:10,
			 *		http://media.example.com/segment3.ts
			 *		#EXT-X-ENDLIST
        	 */
        	
            writer.println("#EXTM3U\n#EXT-X-ALLOW-CACHE:NO\n");
        	if(availabilityService.isAvailable(streamName, mobileProfileName)){
    			log.errorf("PLS for Stream: %s is available", streamName);
    			int count = availabilityService.getSegmentCount(streamName, mobileProfileName);
        		if (count < minimumSegmentCount) {
        			log.errorf("Starting wait loop for segment availability");
            		long maxWaitTime = minimumSegmentCount * availabilityService.getSegmentTimeLimit();
            		long start = System.currentTimeMillis();
            		do {
            			try {
    						Thread.sleep(500);
    					} catch (InterruptedException e) {
    						//
    					}
    					if ((System.currentTimeMillis() - start) >= maxWaitTime) {
    						log.infof("Maximum segment wait time exceeded for %s/%s", streamName, mobileProfileName);
    						break;
    					}
            		} while ((count = availabilityService.getSegmentCount(streamName , mobileProfileName)) < minimumSegmentCount);
        		}
        		// get the count one last time
        		count = availabilityService.getSegmentCount(streamName , mobileProfileName);
        		log.errorf("Segment count: %s", count);    		
        		if (count >= minimumSegmentCount) {
            		//get segment duration in seconds
            		//get current sequence number
            		int sequenceNumber = availabilityService.getSegmentIndex(streamName, mobileProfileName);	
            		log.tracef("Current sequence number: %s", sequenceNumber);
            		/*
            		HTTP streaming spec section 3.2.2
            		Each media file URI in a Playlist has a unique sequence number.  The sequence number of a URI is equal to the sequence number
            		of the URI that preceded it plus one. The EXT-X-MEDIA-SEQUENCE tag indicates the sequence number of the first URI that appears 
            		in a Playlist file.
            		*/     		
            		// determine the lowest sequence number
                    int lowestSequenceNumber = Math.max(-1, sequenceNumber - availabilityService.getNumMaxSegments()) + 1;
                    log.tracef("Lowest sequence number: %s", lowestSequenceNumber);
                    // for logging
                    StringBuilder sb = new StringBuilder();
            		// create the heading
                    String playListHeading = String.format("#EXT-X-TARGETDURATION:%s\n#EXT-X-MEDIA-SEQUENCE:%s\n", segmentDuration, lowestSequenceNumber);   		
                    writer.println(playListHeading);
                    sb.append(playListHeading);
                    //loop through the x closest segments
					for (int s = lowestSequenceNumber; s <= sequenceNumber; s++) {
						String playListEntry = String.format(
								"#EXTINF:%s, no desc\nstream%s.ts\n",
								segmentDuration, s);
						writer.println(playListEntry);
						sb.append(playListEntry);
					}
                	// are we on the last segment?
                	// Live streams are never on their last segment
                    /*if (segment.isLast()) {
                    	log.errorf("Last segment");
                    	writer.println("#EXT-X-ENDLIST\n");
                    	sb.append("#EXT-X-ENDLIST\n");
                    }	 */
					log.errorf(sb.toString());
				} else {
					log
							.tracef(
									"Minimum segment count not yet reached, currently at: %s",
									count);
				}
			}
		} else {
    	
    	/*
    	 * Root / Adaptive Playlist
    	 * 
    	 * Example:
    	 * 
    	 *  #EXTM3U
    	 *  #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=96000
    	 *  StreamName/2G/stream.m3u8
    	 *  #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=256000
    	 *  StreamName/4G/stream.m3u8
    	 *  #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000
    	 *  StreamName/WiFi/stream.m3u8
    	 *  
    	 */
			writer.println("#EXTM3U\n");
			if (availabilityService.isADPAvailable(streamName)) {
				log.errorf("ADP PLS for Stream: %s is available", streamName);
				// remove it from requested list if its there
				ArrayList<MobileProfile> mobileProfiles = availabilityService.getMobileProfiles(streamName);
				if(mobileProfiles!=null)
					log.tracef("There are %s mobileProfiles in the profileMap for %s", mobileProfiles.size(), streamName);
				for (MobileProfile mp : mobileProfiles) {
					String pLHeade = String.format("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=%s", mp.getBandwidth());
					writer.println(pLHeade);
					String pLEntry = String.format("%s/stream.m3u8", mp.getName());
					writer.println(pLEntry);
				}
			}
		}
	writer.flush();
	}
}
